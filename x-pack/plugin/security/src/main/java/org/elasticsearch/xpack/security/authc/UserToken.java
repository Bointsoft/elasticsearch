/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.security.authc;

import org.elasticsearch.Version;
import org.elasticsearch.common.UUIDs;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.xpack.core.security.authc.Authentication;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * This token is a combination of a {@link Authentication} object with an expiry. This token can be
 * serialized for use later. Note, if serializing this token to a entity outside of the cluster,
 * care must be taken to encrypt and validate the serialized bytes or they cannot be trusted.
 *
 * Additionally, care must also be used when transporting these tokens as a stolen token can be
 * used by an adversary to gain access. For this reason, TLS must be enabled for these tokens to
 * be used.
 */
public final class UserToken implements Writeable, ToXContentObject {

    private final Version version;
    private final String id;
    private final Authentication authentication;
    private final Instant expirationTime;
    private final Map<String, Object> metadata;

    /**
     * Create a new token with an autogenerated id
     */
    UserToken(Authentication authentication, Instant expirationTime) {
        this(Version.CURRENT, authentication, expirationTime, Collections.emptyMap());
    }

    /**
     * Create a new token with an autogenerated id
     */
    private UserToken(Version version, Authentication authentication, Instant expirationTime, Map<String, Object> metadata) {
        this(UUIDs.randomBase64UUID(), version, authentication, expirationTime, metadata);
    }

    /**
     * Create a new token from an existing id
     */
    UserToken(String id, Version version, Authentication authentication, Instant expirationTime, Map<String, Object> metadata) {
        this.version = Objects.requireNonNull(version);
        this.id = Objects.requireNonNull(id);
        this.authentication = Objects.requireNonNull(authentication);
        this.expirationTime = Objects.requireNonNull(expirationTime);
        this.metadata = metadata;
    }

    /**
     * Creates a new token based on the values from the stream
     */
    UserToken(StreamInput input) throws IOException {
        this.version = input.getVersion();
        this.id = input.readString();
        this.authentication = new Authentication(input);
        this.expirationTime = Instant.ofEpochSecond(input.readLong(), input.readInt());
        if (version.before(Version.V_6_2_0)) {
            this.metadata = Collections.emptyMap();
        } else {
            this.metadata = input.readMap();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(id);
        authentication.writeTo(out);
        out.writeLong(expirationTime.getEpochSecond());
        out.writeInt(expirationTime.getNano());
        if (out.getVersion().onOrAfter(Version.V_6_2_0)) {
            out.writeMap(metadata);
        }
    }

    /**
     * Get the authentication (will not be null)
     */
    Authentication getAuthentication() {
        return authentication;
    }

    /**
     * Get the expiration time
     */
    Instant getExpirationTime() {
        return expirationTime;
    }

    /**
     * The ID of this token
     */
    public String getId() {
        return id;
    }

    /**
     * The version of the node this token was created on
     */
    Version getVersion() {
        return version;
    }

    /**
     * The metadata associated with this token
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("id", id);
        builder.field("expiration_time", expirationTime.toEpochMilli());
        builder.field("version", version.id);
        builder.field("metadata", metadata);
        try (BytesStreamOutput output = new BytesStreamOutput()) {
            output.setVersion(version);
            authentication.writeTo(output);
            builder.field("authentication", output.bytes().toBytesRef().bytes);
        }
        return builder.endObject();
    }

    static UserToken fromSourceMap(Map<String, Object> source) throws IllegalStateException, DateTimeException {
        final String id = (String) source.get("id");
        if (id == null) {
            throw new IllegalStateException("user token source document does not have the \"id\" field");
        }
        final Long expirationEpochMilli = (Long) source.get("expiration_time");
        if (expirationEpochMilli == null) {
            throw new IllegalStateException("user token source document does not have the \"expiration_time\" field");
        }
        final Integer versionId = (Integer) source.get("version");
        if (versionId == null) {
            throw new IllegalStateException("user token source document does not have the \"version\" field");
        }
        @SuppressWarnings("unchecked")
        final Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
        final String authString = (String) source.get("authentication");
        if (authString == null) {
            throw new IllegalStateException("user token source document does not have the \"authentication\" field");
        }
        final Version version = Version.fromId(versionId);
        try (StreamInput in = StreamInput.wrap(Base64.getDecoder().decode(authString))) {
            in.setVersion(version);
            Authentication authentication = new Authentication(in);
            return new UserToken(id, version, authentication, Instant.ofEpochMilli(expirationEpochMilli), metadata);
        } catch (IOException e) {
            throw new IllegalStateException("user token source document contains malformed \"authentication\" field", e);
        }
    }
}
