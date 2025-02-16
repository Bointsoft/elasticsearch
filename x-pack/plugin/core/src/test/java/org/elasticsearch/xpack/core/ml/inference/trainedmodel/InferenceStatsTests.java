/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.core.ml.inference.trainedmodel;

import org.elasticsearch.Version;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.xpack.core.ml.AbstractBWCSerializationTestCase;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;

import static org.elasticsearch.xpack.core.ml.utils.ToXContentParams.FOR_INTERNAL_STORAGE;

public class InferenceStatsTests extends AbstractBWCSerializationTestCase<InferenceStats> {

    public static InferenceStats createTestInstance(String modelId, @Nullable String nodeId) {
        return new InferenceStats(randomNonNegativeLong(),
            randomNonNegativeLong(),
            randomNonNegativeLong(),
            randomNonNegativeLong(),
            modelId,
            nodeId,
            Instant.now()
            );
    }

    public static InferenceStats mutateForVersion(InferenceStats instance, Version version) {
        if (instance == null) {
            return null;
        }
        if (version.before(Version.V_7_9_0)) {
            return new InferenceStats(
                instance.getMissingAllFieldsCount(),
                instance.getInferenceCount(),
                instance.getFailureCount(),
                0L,
                instance.getModelId(),
                instance.getNodeId(),
                instance.getTimeStamp()
            );
        }
        return instance;
    }

    @Override
    protected InferenceStats doParseInstance(XContentParser parser) throws IOException {
        return InferenceStats.PARSER.apply(parser, null);
    }

    @Override
    protected boolean supportsUnknownFields() {
        return true;
    }

    @Override
    protected InferenceStats createTestInstance() {
        return createTestInstance(randomAlphaOfLength(10), randomAlphaOfLength(10));
    }

    @Override
    protected Writeable.Reader<InferenceStats> instanceReader() {
        return InferenceStats::new;
    }

    @Override
    protected ToXContent.Params getToXContentParams() {
        return new ToXContent.MapParams(Collections.singletonMap(FOR_INTERNAL_STORAGE, "true"));
    }

    @Override
    protected InferenceStats mutateInstanceForVersion(InferenceStats instance, Version version) {
        return mutateForVersion(instance, version);
    }
}
