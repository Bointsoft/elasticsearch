/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.security.rest.action.user;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.core.RestApiVersion;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.license.XPackLicenseState;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.RestBuilderListener;
import org.elasticsearch.xpack.core.security.action.user.DeleteUserResponse;
import org.elasticsearch.xpack.core.security.client.SecurityClient;
import org.elasticsearch.xpack.security.rest.action.SecurityBaseRestHandler;

import java.io.IOException;
import java.util.List;

import static org.elasticsearch.rest.RestRequest.Method.DELETE;

/**
 * Rest action to delete a user from the security index
 */
public class RestDeleteUserAction extends SecurityBaseRestHandler {

    public RestDeleteUserAction(Settings settings, XPackLicenseState licenseState) {
        super(settings, licenseState);
    }

    @Override
    public List<Route> routes() {
        return org.elasticsearch.core.List.of(
            Route.builder(DELETE, "/_security/user/{username}")
                .replaces(DELETE, "/_xpack/security/user/{username}", RestApiVersion.V_7).build()
        );
    }

    @Override
    public String getName() {
        return "security_delete_user_action";
    }

    @Override
    public RestChannelConsumer innerPrepareRequest(RestRequest request, NodeClient client) throws IOException {
        final String username = request.param("username");
        final String refresh = request.param("refresh");
        return channel -> new SecurityClient(client).prepareDeleteUser(username)
                .setRefreshPolicy(refresh)
                .execute(new RestBuilderListener<DeleteUserResponse>(channel) {
                    @Override
                    public RestResponse buildResponse(DeleteUserResponse response, XContentBuilder builder) throws Exception {
                        return new BytesRestResponse(response.found() ? RestStatus.OK : RestStatus.NOT_FOUND,
                                builder.startObject()
                                        .field("found", response.found())
                                        .endObject());
                    }
                });
    }
}
