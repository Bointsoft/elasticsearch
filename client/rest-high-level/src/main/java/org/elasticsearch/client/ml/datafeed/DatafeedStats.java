/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.client.ml.datafeed;

import org.elasticsearch.client.ml.NodeAttributes;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.common.xcontent.ParseField;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * Datafeed Statistics POJO
 */
public class DatafeedStats implements ToXContentObject {

    private final String datafeedId;
    private final DatafeedState datafeedState;
    @Nullable
    private final NodeAttributes node;
    @Nullable
    private final String assignmentExplanation;
    @Nullable
    private final DatafeedTimingStats timingStats;
    @Nullable
    private final RunningState runningState;

    public static final ParseField ASSIGNMENT_EXPLANATION = new ParseField("assignment_explanation");
    public static final ParseField NODE = new ParseField("node");
    public static final ParseField TIMING_STATS = new ParseField("timing_stats");
    public static final ParseField RUNNING_STATE = new ParseField("running_state");

    public static final ConstructingObjectParser<DatafeedStats, Void> PARSER = new ConstructingObjectParser<>("datafeed_stats",
    true,
    a -> {
        String datafeedId = (String)a[0];
        DatafeedState datafeedState = DatafeedState.fromString((String)a[1]);
        NodeAttributes nodeAttributes = (NodeAttributes)a[2];
        String assignmentExplanation = (String)a[3];
        DatafeedTimingStats timingStats = (DatafeedTimingStats)a[4];
        RunningState runningState = (RunningState) a[5];
        return new DatafeedStats(datafeedId, datafeedState, nodeAttributes, assignmentExplanation, timingStats, runningState);
    } );

    static {
        PARSER.declareString(ConstructingObjectParser.constructorArg(), DatafeedConfig.ID);
        PARSER.declareString(ConstructingObjectParser.constructorArg(), DatafeedState.STATE);
        PARSER.declareObject(ConstructingObjectParser.optionalConstructorArg(), NodeAttributes.PARSER, NODE);
        PARSER.declareString(ConstructingObjectParser.optionalConstructorArg(), ASSIGNMENT_EXPLANATION);
        PARSER.declareObject(ConstructingObjectParser.optionalConstructorArg(), DatafeedTimingStats.PARSER, TIMING_STATS);
        PARSER.declareObject(ConstructingObjectParser.optionalConstructorArg(), RunningState.PARSER, RUNNING_STATE);
    }

    public DatafeedStats(String datafeedId,
                         DatafeedState datafeedState,
                         @Nullable NodeAttributes node,
                         @Nullable String assignmentExplanation,
                         @Nullable DatafeedTimingStats timingStats,
                         @Nullable RunningState runningState) {
        this.datafeedId = Objects.requireNonNull(datafeedId);
        this.datafeedState = Objects.requireNonNull(datafeedState);
        this.node = node;
        this.assignmentExplanation = assignmentExplanation;
        this.timingStats = timingStats;
        this.runningState = runningState;
    }

    public String getDatafeedId() {
        return datafeedId;
    }

    public DatafeedState getDatafeedState() {
        return datafeedState;
    }

    public NodeAttributes getNode() {
        return node;
    }

    public String getAssignmentExplanation() {
        return assignmentExplanation;
    }

    public DatafeedTimingStats getDatafeedTimingStats() {
        return timingStats;
    }

    public RunningState getRunningState() {
        return runningState;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(DatafeedConfig.ID.getPreferredName(), datafeedId);
        builder.field(DatafeedState.STATE.getPreferredName(), datafeedState.toString());
        if (node != null) {
            builder.startObject("node");
            builder.field("id", node.getId());
            builder.field("name", node.getName());
            builder.field("ephemeral_id", node.getEphemeralId());
            builder.field("transport_address", node.getTransportAddress());

            builder.startObject("attributes");
            for (Map.Entry<String, String> entry : node.getAttributes().entrySet()) {
                if (entry.getKey().startsWith("ml.")) {
                    builder.field(entry.getKey(), entry.getValue());
                }
            }
            builder.endObject();
            builder.endObject();
        }
        if (assignmentExplanation != null) {
            builder.field(ASSIGNMENT_EXPLANATION.getPreferredName(), assignmentExplanation);
        }
        if (timingStats != null) {
            builder.field(TIMING_STATS.getPreferredName(), timingStats);
        }
        if (runningState != null) {
            builder.field(RUNNING_STATE.getPreferredName(), runningState);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public int hashCode() {
        return Objects.hash(datafeedId, datafeedState.toString(), node, assignmentExplanation, timingStats, runningState);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DatafeedStats other = (DatafeedStats) obj;
        return Objects.equals(datafeedId, other.datafeedId) &&
            Objects.equals(this.datafeedState, other.datafeedState) &&
            Objects.equals(this.node, other.node) &&
            Objects.equals(this.assignmentExplanation, other.assignmentExplanation) &&
            Objects.equals(this.runningState, other.runningState) &&
            Objects.equals(this.timingStats, other.timingStats);
    }
}
