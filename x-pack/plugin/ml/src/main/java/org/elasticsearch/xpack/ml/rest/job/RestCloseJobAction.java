/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.ml.rest.job;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.core.RestApiVersion;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestToXContentListener;
import org.elasticsearch.xpack.core.ml.action.CloseJobAction;
import org.elasticsearch.xpack.core.ml.action.CloseJobAction.Request;
import org.elasticsearch.xpack.core.ml.job.config.Job;

import java.io.IOException;
import java.util.List;

import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.xpack.ml.MachineLearning.BASE_PATH;
import static org.elasticsearch.xpack.ml.MachineLearning.PRE_V7_BASE_PATH;

public class RestCloseJobAction extends BaseRestHandler {

    @Override
    public List<Route> routes() {
        return org.elasticsearch.core.List.of(
            Route.builder(POST, BASE_PATH + "anomaly_detectors/{" + Job.ID + "}/_close")
                .replaces(POST, PRE_V7_BASE_PATH + "anomaly_detectors/{" + Job.ID + "}/_close", RestApiVersion.V_7).build()
        );
    }

    @Override
    public String getName() {
        return "ml_close_job_action";
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) throws IOException {
        Request request;
        if (restRequest.hasContentOrSourceParam()) {
            request = Request.parseRequest(restRequest.param(Job.ID.getPreferredName()), restRequest.contentParser());
        } else {
            request = new Request(restRequest.param(Job.ID.getPreferredName()));
            if (restRequest.hasParam(Request.TIMEOUT.getPreferredName())) {
                request.setCloseTimeout(TimeValue.parseTimeValue(
                    restRequest.param(Request.TIMEOUT.getPreferredName()), Request.TIMEOUT.getPreferredName()));
            }
            if (restRequest.hasParam(Request.FORCE.getPreferredName())) {
                request.setForce(restRequest.paramAsBoolean(Request.FORCE.getPreferredName(), request.isForce()));
            }
            if (restRequest.hasParam(Request.ALLOW_NO_JOBS)) {
                LoggingDeprecationHandler.INSTANCE.usedDeprecatedName(
                    null, () -> null, Request.ALLOW_NO_JOBS, Request.ALLOW_NO_MATCH.getPreferredName());
            }
            request.setAllowNoMatch(
                restRequest.paramAsBoolean(
                    Request.ALLOW_NO_MATCH.getPreferredName(),
                    restRequest.paramAsBoolean(Request.ALLOW_NO_JOBS, request.allowNoMatch())));
        }
        return channel -> client.execute(CloseJobAction.INSTANCE, request, new RestToXContentListener<>(channel));
    }
}
