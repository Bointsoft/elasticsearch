/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.client.ml;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.client.ml.job.config.Job;
import org.elasticsearch.common.xcontent.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Objects;

/**
 * Request to open a Machine Learning Job
 */
public class OpenJobRequest extends ActionRequest implements ToXContentObject {

    public static final ParseField TIMEOUT = new ParseField("timeout");
    public static final ConstructingObjectParser<OpenJobRequest, Void> PARSER = new ConstructingObjectParser<>(
        "open_job_request", true, a -> new OpenJobRequest((String) a[0]));

    static {
        PARSER.declareString(ConstructingObjectParser.constructorArg(), Job.ID);
        PARSER.declareString((request, val) -> request.setTimeout(TimeValue.parseTimeValue(val, TIMEOUT.getPreferredName())), TIMEOUT);
    }

    public static OpenJobRequest fromXContent(XContentParser parser) throws IOException {
        return PARSER.parse(parser, null);
    }

    private String jobId;
    private TimeValue timeout;

    /**
     * Create a new request with the desired jobId
     *
     * @param jobId unique jobId, must not be null
     */
    public OpenJobRequest(String jobId) {
        this.jobId = Objects.requireNonNull(jobId, "[job_id] must not be null");
    }

    public String getJobId() {
        return jobId;
    }

    /**
     * The jobId to open
     *
     * @param jobId unique jobId, must not be null
     */
    public void setJobId(String jobId) {
        this.jobId = Objects.requireNonNull(jobId, "[job_id] must not be null");
    }

    public TimeValue getTimeout() {
        return timeout;
    }

    /**
     * How long to wait for job to open before timing out the request
     *
     * @param timeout default value of 30 minutes
     */
    public void setTimeout(TimeValue timeout) {
        this.timeout = timeout;
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(Job.ID.getPreferredName(), jobId);
        if (timeout != null) {
            builder.field(TIMEOUT.getPreferredName(), timeout.getStringRep());
        }
        builder.endObject();
        return builder;
    }

    @Override
    public String toString() {
        return Strings.toString(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, timeout);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        OpenJobRequest that = (OpenJobRequest) other;
        return Objects.equals(jobId, that.jobId) && Objects.equals(timeout, that.timeout);
    }
}
