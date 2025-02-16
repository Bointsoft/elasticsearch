/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.core.security.action.token;

import org.elasticsearch.Version;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.VersionUtils;
import org.elasticsearch.xpack.core.security.authc.Authentication;
import org.elasticsearch.xpack.core.security.user.User;

public class CreateTokenResponseTests extends ESTestCase {

    public void testSerialization() throws Exception {
        CreateTokenResponse response = new CreateTokenResponse(randomAlphaOfLengthBetween(1, 10), TimeValue.timeValueMinutes(20L),
            randomBoolean() ? null : "FULL", randomAlphaOfLengthBetween(1, 10), randomBoolean() ? null :randomAlphaOfLengthBetween(1, 10),
            new Authentication(new User("joe", new String[]{"custom_superuser"}, new User("bar", "not_superuser")),
                new Authentication.RealmRef("test", "test", "node"), new Authentication.RealmRef("test", "test", "node")));
        try (BytesStreamOutput output = new BytesStreamOutput()) {
            response.writeTo(output);
            try (StreamInput input = output.bytes().streamInput()) {
                CreateTokenResponse serialized = new CreateTokenResponse(input);
                assertEquals(response, serialized);
            }
        }

        response = new CreateTokenResponse(randomAlphaOfLengthBetween(1, 10), TimeValue.timeValueMinutes(20L),
            randomBoolean() ? null : "FULL", null, null,
            new Authentication(new User("joe", new String[]{"custom_superuser"}, new User("bar", "not_superuser")),
                new Authentication.RealmRef("test", "test", "node"), new Authentication.RealmRef("test", "test", "node")));
        try (BytesStreamOutput output = new BytesStreamOutput()) {
            response.writeTo(output);
            try (StreamInput input = output.bytes().streamInput()) {
                CreateTokenResponse serialized = new CreateTokenResponse(input);
                assertEquals(response, serialized);
            }
        }
    }

    public void testSerializationToPre62Version() throws Exception {
        CreateTokenResponse response = new CreateTokenResponse(randomAlphaOfLengthBetween(1, 10), TimeValue.timeValueMinutes(20L),
            randomBoolean() ? null : "FULL", randomBoolean() ? null : randomAlphaOfLengthBetween(1, 10), null,
            new Authentication(new User("joe", new String[]{"custom_superuser"}, new User("bar", "not_superuser")),
                new Authentication.RealmRef("test", "test", "node"),
                new Authentication.RealmRef("test", "test", "node")));
        final Version version = VersionUtils.randomVersionBetween(random(), Version.V_6_0_0, Version.V_6_1_4);
        try (BytesStreamOutput output = new BytesStreamOutput()) {
            output.setVersion(version);
            response.writeTo(output);
            try (StreamInput input = output.bytes().streamInput()) {
                input.setVersion(version);
                CreateTokenResponse serialized = new CreateTokenResponse(input);
                assertNull(serialized.getRefreshToken());
                assertEquals(response.getTokenString(), serialized.getTokenString());
                assertEquals(response.getExpiresIn(), serialized.getExpiresIn());
                assertEquals(response.getScope(), serialized.getScope());
            }
        }
    }

    public void testSerializationToPost62Pre65Version() throws Exception {
        CreateTokenResponse response = new CreateTokenResponse(randomAlphaOfLengthBetween(1, 10), TimeValue.timeValueMinutes(20L),
            randomBoolean() ? null : "FULL", randomAlphaOfLengthBetween(1, 10), null, null);
        final Version version = VersionUtils.randomVersionBetween(random(), Version.V_6_2_0, Version.V_6_4_0);
        try (BytesStreamOutput output = new BytesStreamOutput()) {
            output.setVersion(version);
            response.writeTo(output);
            try (StreamInput input = output.bytes().streamInput()) {
                input.setVersion(version);
                CreateTokenResponse serialized = new CreateTokenResponse(input);
                assertEquals(response, serialized);
            }
        }

        // no refresh token
        response = new CreateTokenResponse(randomAlphaOfLengthBetween(1, 10), TimeValue.timeValueMinutes(20L),
            randomBoolean() ? null : "FULL", null, null, new Authentication(
                new User("joe", new String[]{"custom_superuser"}, new User("bar", "not_superuser")),
                new Authentication.RealmRef("test", "test", "node"),
                new Authentication.RealmRef("test", "test", "node")));
        try (BytesStreamOutput output = new BytesStreamOutput()) {
            output.setVersion(version);
            response.writeTo(output);
            try (StreamInput input = output.bytes().streamInput()) {
                input.setVersion(version);
                CreateTokenResponse serialized = new CreateTokenResponse(input);
                assertEquals("", serialized.getRefreshToken());
                assertEquals(response.getTokenString(), serialized.getTokenString());
                assertEquals(response.getExpiresIn(), serialized.getExpiresIn());
                assertEquals(response.getScope(), serialized.getScope());
            }
        }
    }
}
