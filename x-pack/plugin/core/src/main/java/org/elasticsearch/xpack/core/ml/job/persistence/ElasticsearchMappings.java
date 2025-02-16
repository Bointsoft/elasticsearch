/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.core.ml.job.persistence;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingAction;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexAbstraction;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.elasticsearch.core.CheckedFunction;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.Index;
import org.elasticsearch.plugins.MapperPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.xpack.core.ClientHelper.ML_ORIGIN;
import static org.elasticsearch.xpack.core.ClientHelper.executeAsyncWithOrigin;

/**
 * Static methods to create Elasticsearch index mappings for the autodetect
 * persisted objects/documents and configurations
 * <p>
 * ElasticSearch automatically recognises array types so they are
 * not explicitly mapped as such. For arrays of objects the type
 * must be set to <i>nested</i> so the arrays are searched properly
 * see https://www.elastic.co/guide/en/elasticsearch/guide/current/nested-objects.html
 * <p>
 * It is expected that indexes to which these mappings are applied have their
 * default analyzer set to "keyword", which does not tokenise fields.  The
 * index-wide default analyzer cannot be set via these mappings, so needs to be
 * set in the index settings during index creation. For the results mapping the
 * _all field is disabled and a custom all field is used in its place. The index
 * settings must have {@code "index.query.default_field": "all_field_values" } set
 * for the queries to use the custom all field. The custom all field has its
 * analyzer set to "whitespace" by these mappings, so that it gets tokenised
 * using whitespace.
 */
public class ElasticsearchMappings {

    /**
     * String constants used in mappings
     */
    public static final String ENABLED = "enabled";
    public static final String ANALYZER = "analyzer";
    public static final String WHITESPACE = "whitespace";
    public static final String NESTED = "nested";
    public static final String COPY_TO = "copy_to";
    public static final String PROPERTIES = "properties";
    public static final String TYPE = "type";
    public static final String DYNAMIC = "dynamic";

    /**
     * Name of the custom 'all' field for results
     */
    public static final String ALL_FIELD_VALUES = "all_field_values";

    /**
     * Name of the Elasticsearch field by which documents are sorted by default
     */
    public static final String ES_DOC = "_doc";

    /**
     * The configuration document type
     */
    public static final String CONFIG_TYPE = "config_type";

    /**
     * Elasticsearch data types
     */
    public static final String BOOLEAN = "boolean";
    public static final String DATE = "date";
    public static final String DOUBLE = "double";
    public static final String INTEGER = "integer";
    public static final String KEYWORD = "keyword";
    public static final String LONG = "long";
    public static final String TEXT = "text";

    private static final Logger logger = LogManager.getLogger(ElasticsearchMappings.class);

    private ElasticsearchMappings() {
    }

    static String[] mappingRequiresUpdate(ClusterState state, String[] concreteIndices, Version minVersion) throws IOException {
        List<String> indicesToUpdate = new ArrayList<>();

        ImmutableOpenMap<String, ImmutableOpenMap<String, MappingMetadata>> currentMapping = state.metadata().findMappings(concreteIndices,
                new String[0], MapperPlugin.NOOP_FIELD_FILTER,
                Metadata.ON_NEXT_INDEX_FIND_MAPPINGS_NOOP
        );

        for (String index : concreteIndices) {
            ImmutableOpenMap<String, MappingMetadata> innerMap = currentMapping.get(index);
            if (innerMap != null) {
                MappingMetadata metadata = innerMap.valuesIt().next();
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> meta = (Map<String, Object>) metadata.sourceAsMap().get("_meta");
                    if (meta != null) {
                        String versionString = (String) meta.get("version");
                        if (versionString == null) {
                            logger.info("Version of mappings for [{}] not found, recreating", index);
                            indicesToUpdate.add(index);
                            continue;
                        }

                        Version mappingVersion = Version.fromString(versionString);

                        if (mappingVersion.onOrAfter(minVersion)) {
                            continue;
                        } else {
                            logger.info("Mappings for [{}] are outdated [{}], updating it[{}].", index, mappingVersion, Version.CURRENT);
                            indicesToUpdate.add(index);
                            continue;
                        }
                    } else {
                        logger.info("Version of mappings for [{}] not found, recreating", index);
                        indicesToUpdate.add(index);
                        continue;
                    }
                } catch (Exception e) {
                    logger.error(new ParameterizedMessage("Failed to retrieve mapping version for [{}], recreating", index), e);
                    indicesToUpdate.add(index);
                    continue;
                }
            } else {
                logger.info("No mappings found for [{}], recreating", index);
                indicesToUpdate.add(index);
            }
        }
        return indicesToUpdate.toArray(new String[indicesToUpdate.size()]);
    }

    public static void addDocMappingIfMissing(String alias,
                                              CheckedFunction<String, String, IOException> mappingSupplier,
                                              Client client, ClusterState state, TimeValue masterNodeTimeout,
                                              ActionListener<Boolean> listener) {
        IndexAbstraction indexAbstraction = state.metadata().getIndicesLookup().get(alias);
        if (indexAbstraction == null) {
            // The index has never been created yet
            listener.onResponse(true);
            return;
        }
        String[] concreteIndices = indexAbstraction.getIndices().stream().map(IndexMetadata::getIndex).map(Index::getName)
            .toArray(String[]::new);

        String[] indicesThatRequireAnUpdate;
        try {
            indicesThatRequireAnUpdate = mappingRequiresUpdate(state, concreteIndices, Version.CURRENT);
        } catch (IOException e) {
            listener.onFailure(e);
            return;
        }

        if (indicesThatRequireAnUpdate.length > 0) {
            // Use the mapping type of the first index in the update
            IndexMetadata indexMetadata = state.metadata().index(indicesThatRequireAnUpdate[0]);
            String mappingType = indexMetadata.mapping().type();

            try {
                String mapping = mappingSupplier.apply(mappingType);
                PutMappingRequest putMappingRequest = new PutMappingRequest(indicesThatRequireAnUpdate);
                putMappingRequest.type(mappingType);
                putMappingRequest.source(mapping, XContentType.JSON);
                putMappingRequest.origin(ML_ORIGIN);
                putMappingRequest.masterNodeTimeout(masterNodeTimeout);
                executeAsyncWithOrigin(client, ML_ORIGIN, PutMappingAction.INSTANCE, putMappingRequest,
                    ActionListener.wrap(response -> {
                        if (response.isAcknowledged()) {
                            listener.onResponse(true);
                        } else {
                            listener.onFailure(new ElasticsearchException("Attempt to put missing mapping in indices "
                                + Arrays.toString(indicesThatRequireAnUpdate) + " was not acknowledged"));
                        }
                    }, listener::onFailure));
            } catch (IOException e) {
                listener.onFailure(e);
            }
        } else {
            logger.trace("Mappings are up to date.");
            listener.onResponse(true);
        }
    }
}
