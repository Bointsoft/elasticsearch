/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.admin.indices.upgrade.post;

import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.PrimaryMissingActionException;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.DefaultShardOperationFailedException;
import org.elasticsearch.action.support.broadcast.node.TransportBroadcastByNodeAction;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.routing.IndexRoutingTable;
import org.elasticsearch.cluster.routing.RoutingTable;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.ShardsIterator;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Upgrade index/indices action.
 */
public class TransportUpgradeAction extends TransportBroadcastByNodeAction<UpgradeRequest, UpgradeResponse, ShardUpgradeResult> {

    private final IndicesService indicesService;
    private final NodeClient client;

    @Inject
    public TransportUpgradeAction(ClusterService clusterService, TransportService transportService, IndicesService indicesService,
                                  ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver,
                                  NodeClient client) {
        super(UpgradeAction.NAME, clusterService, transportService, actionFilters, indexNameExpressionResolver,
            UpgradeRequest::new, ThreadPool.Names.FORCE_MERGE);
        this.indicesService = indicesService;
        this.client = client;
    }

    @Override
    protected UpgradeResponse newResponse(UpgradeRequest request, int totalShards, int successfulShards, int failedShards,
                                          List<ShardUpgradeResult> shardUpgradeResults,
                                          List<DefaultShardOperationFailedException> shardFailures, ClusterState clusterState) {
        Map<String, Integer> successfulPrimaryShards = new HashMap<>();
        Map<String, Tuple<Version, org.apache.lucene.util.Version>> versions = new HashMap<>();
        for (ShardUpgradeResult result : shardUpgradeResults) {
            successfulShards++;
            String index = result.getShardId().getIndex().getName();
            if (result.primary()) {
                Integer count = successfulPrimaryShards.get(index);
                successfulPrimaryShards.put(index, count == null ? 1 : count + 1);
            }
            Tuple<Version, org.apache.lucene.util.Version> versionTuple = versions.get(index);
            if (versionTuple == null) {
                versions.put(index, new Tuple<>(result.upgradeVersion(), result.oldestLuceneSegment()));
            } else {
                // We already have versions for this index - let's see if we need to update them based on the current shard
                Version version = versionTuple.v1();
                org.apache.lucene.util.Version luceneVersion = versionTuple.v2();
                // For the metadata we are interested in the _latest_ Elasticsearch version that was processing the metadata
                // Since we rewrite the mapping during upgrade the metadata is always rewritten by the latest version
                if (result.upgradeVersion().after(versionTuple.v1())) {
                    version = result.upgradeVersion();
                }
                // For the lucene version we are interested in the _oldest_ lucene version since it determines the
                // oldest version that we need to support
                if (result.oldestLuceneSegment().onOrAfter(versionTuple.v2()) == false) {
                    luceneVersion = result.oldestLuceneSegment();
                }
                versions.put(index, new Tuple<>(version, luceneVersion));
            }
        }
        Map<String, Tuple<Version, String>> updatedVersions = new HashMap<>();
        Metadata metadata = clusterState.metadata();
        for (Map.Entry<String, Tuple<Version, org.apache.lucene.util.Version>> versionEntry : versions.entrySet()) {
            String index = versionEntry.getKey();
            Integer primaryCount = successfulPrimaryShards.get(index);
            int expectedPrimaryCount = metadata.index(index).getNumberOfShards();
            if (primaryCount == metadata.index(index).getNumberOfShards()) {
                updatedVersions.put(index, new Tuple<>(versionEntry.getValue().v1(), versionEntry.getValue().v2().toString()));
            } else {
                logger.warn("Not updating settings for the index [{}] because upgraded of some primary shards failed - " +
                        "expected[{}], received[{}]", index, expectedPrimaryCount, primaryCount == null ? 0 : primaryCount);
            }
        }

        return new UpgradeResponse(updatedVersions, totalShards, successfulShards, failedShards, shardFailures);
    }

    @Override
    protected void shardOperation(UpgradeRequest request, ShardRouting shardRouting, Task task,
                                  ActionListener<ShardUpgradeResult> listener) {
        ActionListener.completeWith(listener, () -> {
            IndexShard indexShard = indicesService.indexServiceSafe(shardRouting.shardId().getIndex())
                .getShard(shardRouting.shardId().id());
            org.apache.lucene.util.Version oldestLuceneSegment = indexShard.upgrade(request);
            // We are using the current version of Elasticsearch as upgrade version since we update mapping to match the current version
            return new ShardUpgradeResult(shardRouting.shardId(), indexShard.routingEntry().primary(), Version.CURRENT,
                oldestLuceneSegment);
        });
    }

    @Override
    protected ShardUpgradeResult readShardResult(StreamInput in) throws IOException {
        return new ShardUpgradeResult(in);
    }

    @Override
    protected UpgradeRequest readRequestFrom(StreamInput in) throws IOException {
        return new UpgradeRequest(in);
    }

    /**
     * The upgrade request works against *all* shards.
     */
    @Override
    protected ShardsIterator shards(ClusterState clusterState, UpgradeRequest request, String[] concreteIndices) {
        ShardsIterator iterator = clusterState.routingTable().allShards(concreteIndices);
        Set<String> indicesWithMissingPrimaries = indicesWithMissingPrimaries(clusterState, concreteIndices);
        if (indicesWithMissingPrimaries.isEmpty()) {
            return iterator;
        }
        // If some primary shards are not available the request should fail.
        throw new PrimaryMissingActionException("Cannot upgrade indices because the following indices are missing primary shards " +
            indicesWithMissingPrimaries);
    }

    /**
     * Finds all indices that have not all primaries available
     */
    private Set<String> indicesWithMissingPrimaries(ClusterState clusterState, String[] concreteIndices) {
        Set<String> indices = new HashSet<>();
        RoutingTable routingTable = clusterState.routingTable();
        for (String index : concreteIndices) {
            IndexRoutingTable indexRoutingTable = routingTable.index(index);
            if (indexRoutingTable.allPrimaryShardsActive() == false) {
                indices.add(index);
            }
        }
        return indices;
    }

    @Override
    protected ClusterBlockException checkGlobalBlock(ClusterState state, UpgradeRequest request) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    @Override
    protected ClusterBlockException checkRequestBlock(ClusterState state, UpgradeRequest request, String[] concreteIndices) {
        return state.blocks().indicesBlockedException(ClusterBlockLevel.METADATA_WRITE, concreteIndices);
    }

    @Override
    protected void doExecute(Task task, UpgradeRequest request, final ActionListener<UpgradeResponse> listener) {
        super.doExecute(task, request, ActionListener.wrap(upgradeResponse -> {
            if (upgradeResponse.versions().isEmpty()) {
                listener.onResponse(upgradeResponse);
            } else {
                updateSettings(upgradeResponse, listener);
            }
        }, listener::onFailure));
    }

    private void updateSettings(final UpgradeResponse upgradeResponse, final ActionListener<UpgradeResponse> listener) {
        UpgradeSettingsRequest upgradeSettingsRequest = new UpgradeSettingsRequest(upgradeResponse.versions());
        client.executeLocally(UpgradeSettingsAction.INSTANCE, upgradeSettingsRequest,
            listener.delegateFailure((delegatedListener, updateSettingsResponse) -> delegatedListener.onResponse(upgradeResponse)));
    }
}
