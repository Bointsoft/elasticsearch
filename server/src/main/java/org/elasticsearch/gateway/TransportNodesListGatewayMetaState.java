/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gateway;

import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.PlainActionFuture;
import org.elasticsearch.action.support.nodes.BaseNodeRequest;
import org.elasticsearch.action.support.nodes.BaseNodeResponse;
import org.elasticsearch.action.support.nodes.BaseNodesRequest;
import org.elasticsearch.action.support.nodes.BaseNodesResponse;
import org.elasticsearch.action.support.nodes.TransportNodesAction;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.util.List;

public class TransportNodesListGatewayMetaState extends TransportNodesAction<TransportNodesListGatewayMetaState.Request,
                                                                             TransportNodesListGatewayMetaState.NodesGatewayMetaState,
                                                                             TransportNodesListGatewayMetaState.NodeRequest,
                                                                             TransportNodesListGatewayMetaState.NodeGatewayMetaState> {

    public static final String ACTION_NAME = "internal:gateway/local/meta_state";
    public static final ActionType<NodesGatewayMetaState> TYPE = new ActionType<>(ACTION_NAME, NodesGatewayMetaState::new);

    private final GatewayMetaState metaState;

    @Inject
    public TransportNodesListGatewayMetaState(ThreadPool threadPool, ClusterService clusterService, TransportService transportService,
                                              ActionFilters actionFilters, GatewayMetaState metaState) {
        super(ACTION_NAME, threadPool, clusterService, transportService, actionFilters,
            Request::new, NodeRequest::new, ThreadPool.Names.GENERIC, NodeGatewayMetaState.class);
        this.metaState = metaState;
    }

    public ActionFuture<NodesGatewayMetaState> list(String[] nodesIds, @Nullable TimeValue timeout) {
        PlainActionFuture<NodesGatewayMetaState> future = PlainActionFuture.newFuture();
        execute(new Request(nodesIds).timeout(timeout), future);
        return future;
    }

    @Override
    protected NodeRequest newNodeRequest(Request request) {
        return new NodeRequest();
    }

    @Override
    protected NodeGatewayMetaState newNodeResponse(StreamInput in) throws IOException {
        return new NodeGatewayMetaState(in);
    }

    @Override
    protected NodesGatewayMetaState newResponse(Request request, List<NodeGatewayMetaState> responses, List<FailedNodeException> failures) {
        return new NodesGatewayMetaState(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected NodeGatewayMetaState nodeOperation(NodeRequest request) {
        return new NodeGatewayMetaState(clusterService.localNode(), metaState.getMetadata());
    }

    public static class Request extends BaseNodesRequest<Request> {

        public Request(StreamInput in) throws IOException {
            super(in);
        }

        public Request(String... nodesIds) {
            super(nodesIds);
        }
    }

    public static class NodesGatewayMetaState extends BaseNodesResponse<NodeGatewayMetaState> {

        public NodesGatewayMetaState(StreamInput in) throws IOException {
            super(in);
        }

        public NodesGatewayMetaState(ClusterName clusterName, List<NodeGatewayMetaState> nodes, List<FailedNodeException> failures) {
            super(clusterName, nodes, failures);
        }

        @Override
        protected List<NodeGatewayMetaState> readNodesFrom(StreamInput in) throws IOException {
            return in.readList(NodeGatewayMetaState::new);
        }

        @Override
        protected void writeNodesTo(StreamOutput out, List<NodeGatewayMetaState> nodes) throws IOException {
            out.writeList(nodes);
        }
    }

    public static class NodeRequest extends BaseNodeRequest {
        NodeRequest() {}
        NodeRequest(StreamInput in) throws IOException {
            super(in);
        }
    }

    public static class NodeGatewayMetaState extends BaseNodeResponse {

        private Metadata metadata;

        public NodeGatewayMetaState(StreamInput in) throws IOException {
            super(in);
            if (in.readBoolean()) {
                metadata = Metadata.readFrom(in);
            }
        }

        public NodeGatewayMetaState(DiscoveryNode node, Metadata metadata) {
            super(node);
            this.metadata = metadata;
        }

        public Metadata metadata() {
            return metadata;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            if (metadata == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                metadata.writeTo(out);
            }
        }
    }
}
