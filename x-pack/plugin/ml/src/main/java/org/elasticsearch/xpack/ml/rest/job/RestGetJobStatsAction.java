/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.ml.rest.job;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.core.RestApiVersion;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestToXContentListener;
import org.elasticsearch.xpack.core.ml.action.GetJobsStatsAction;
import org.elasticsearch.xpack.core.ml.action.GetJobsStatsAction.Request;
import org.elasticsearch.xpack.core.ml.job.config.Job;

import java.io.IOException;
import java.util.List;

import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.xpack.ml.MachineLearning.BASE_PATH;
import static org.elasticsearch.xpack.ml.MachineLearning.PRE_V7_BASE_PATH;

public class RestGetJobStatsAction extends BaseRestHandler {

    @Override
    public List<Route> routes() {
        return org.elasticsearch.core.List.of(
            Route.builder(GET, BASE_PATH + "anomaly_detectors/{" + Job.ID + "}/_stats")
                .replaces(GET, PRE_V7_BASE_PATH + "anomaly_detectors/{" + Job.ID + "}/_stats", RestApiVersion.V_7).build(),
            Route.builder(GET, BASE_PATH + "anomaly_detectors/_stats")
                .replaces(GET, PRE_V7_BASE_PATH + "anomaly_detectors/_stats", RestApiVersion.V_7).build()
        );
    }

    @Override
    public String getName() {
        return "ml_get_job_stats_action";
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) throws IOException {
        String jobId = restRequest.param(Job.ID.getPreferredName());
        if (Strings.isNullOrEmpty(jobId)) {
            jobId = Metadata.ALL;
        }
        Request request = new Request(jobId);
        if (restRequest.hasParam(Request.ALLOW_NO_JOBS)) {
            LoggingDeprecationHandler.INSTANCE.usedDeprecatedName(null, () -> null, Request.ALLOW_NO_JOBS, Request.ALLOW_NO_MATCH);
        }
        request.setAllowNoMatch(
            restRequest.paramAsBoolean(
                Request.ALLOW_NO_MATCH,
                restRequest.paramAsBoolean(Request.ALLOW_NO_JOBS, request.allowNoMatch())));
        return channel -> client.execute(GetJobsStatsAction.INSTANCE, request, new RestToXContentListener<>(channel));
    }
}
