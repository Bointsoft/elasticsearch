/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.security.authc.ldap.support;

import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.LDAPInterface;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.xpack.core.security.authc.RealmConfig;
import org.elasticsearch.xpack.core.security.authc.ldap.support.LdapMetadataResolverSettings;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.elasticsearch.xpack.security.authc.ldap.ActiveDirectorySIDUtil.convertToString;
import static org.elasticsearch.xpack.security.authc.ldap.ActiveDirectorySIDUtil.TOKEN_GROUPS;
import static org.elasticsearch.xpack.security.authc.ldap.support.LdapUtils.OBJECT_CLASS_PRESENCE_FILTER;
import static org.elasticsearch.xpack.security.authc.ldap.support.LdapUtils.searchForEntry;

public class LdapMetadataResolver {

    private final String[] attributeNames;
    private final boolean ignoreReferralErrors;

    public LdapMetadataResolver(RealmConfig realmConfig, boolean ignoreReferralErrors) {
        this(realmConfig.getSetting(LdapMetadataResolverSettings.ADDITIONAL_METADATA_SETTING), ignoreReferralErrors);
    }

    LdapMetadataResolver(Collection<String> attributeNames, boolean ignoreReferralErrors) {
        this.attributeNames = attributeNames.toArray(new String[attributeNames.size()]);
        this.ignoreReferralErrors = ignoreReferralErrors;
    }

    public String[] attributeNames() {
        return attributeNames;
    }

    public void resolve(LDAPInterface connection, String userDn, TimeValue timeout, Logger logger,
                        Collection<Attribute> attributes,
                        ActionListener<Map<String, Object>> listener) {
        if (this.attributeNames.length == 0) {
            listener.onResponse(Collections.emptyMap());
        } else if (attributes != null) {
            listener.onResponse(toMap(name -> findAttribute(attributes, name)));
        } else {
            searchForEntry(connection, userDn, SearchScope.BASE, OBJECT_CLASS_PRESENCE_FILTER,
                    Math.toIntExact(timeout.seconds()), ignoreReferralErrors,
                    ActionListener.wrap((SearchResultEntry entry) -> {
                        if (entry == null) {
                            listener.onResponse(Collections.emptyMap());
                        } else {
                            listener.onResponse(toMap(entry::getAttribute));
                        }
                    }, listener::onFailure), this.attributeNames);
        }
    }

    private Attribute findAttribute(Collection<Attribute> attributes, String name) {
        return attributes.stream()
                .filter(attr -> attr.getName().equals(name))
                .findFirst().orElse(null);
    }

    private Map<String, Object> toMap(Function<String, Attribute> attributes) {
        return Collections.unmodifiableMap(
                Arrays.stream(this.attributeNames).map(attributes).filter(Objects::nonNull)
                        .collect(Collectors.toMap(
                                attr -> attr.getName(),
                                attr -> {
                                    final String[] values = attr.getValues();
                                    if(attr.getName().equals(TOKEN_GROUPS)) {
                                        return values.length == 1 ? convertToString(attr.getValueByteArrays()[0]) :
                                            Arrays.stream(attr.getValueByteArrays())
                                            .map((sidBytes) -> convertToString(sidBytes))
                                            .collect(Collectors.toList());
                                    }
                                    return values.length == 1 ? values[0] : Arrays.asList(values);
                                 })
                        )
        );
    }
}
