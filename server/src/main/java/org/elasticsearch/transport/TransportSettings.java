/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.transport;

import org.elasticsearch.action.admin.cluster.node.liveness.TransportLivenessAction;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.core.TimeValue;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static org.elasticsearch.common.settings.Setting.affixKeySetting;
import static org.elasticsearch.common.settings.Setting.boolSetting;
import static org.elasticsearch.common.settings.Setting.enumSetting;
import static org.elasticsearch.common.settings.Setting.intSetting;
import static org.elasticsearch.common.settings.Setting.listSetting;
import static org.elasticsearch.common.settings.Setting.timeSetting;

public final class TransportSettings {

    public static final String DEFAULT_PROFILE = "default";
    public static final String FEATURE_PREFIX = "transport.features";

    public static final Setting<List<String>> HOST =
        listSetting("transport.host", emptyList(), Function.identity(), Setting.Property.NodeScope);
    public static final Setting<List<String>> PUBLISH_HOST =
        listSetting("transport.publish_host", HOST, Function.identity(), Setting.Property.NodeScope);
    public static final Setting.AffixSetting<List<String>> PUBLISH_HOST_PROFILE =
        affixKeySetting("transport.profiles.", "publish_host", key -> listSetting(key, PUBLISH_HOST, Function.identity(),
            Setting.Property.NodeScope));
    public static final Setting<List<String>> BIND_HOST =
        listSetting("transport.bind_host", HOST, Function.identity(), Setting.Property.NodeScope);
    public static final Setting.AffixSetting<List<String>> BIND_HOST_PROFILE = affixKeySetting("transport.profiles.", "bind_host",
        key -> listSetting(key, BIND_HOST, Function.identity(), Setting.Property.NodeScope));
    public static final Setting<String> OLD_PORT =
        new Setting<>("transport.tcp.port", "9300-9400", Function.identity(), Setting.Property.NodeScope, Setting.Property.Deprecated);
    public static final Setting<String> PORT =
        new Setting<>("transport.port", OLD_PORT, Function.identity(), Setting.Property.NodeScope);
    public static final Setting.AffixSetting<String> PORT_PROFILE = affixKeySetting("transport.profiles.", "port",
        key -> new Setting<>(key, PORT, Function.identity(), Setting.Property.NodeScope));
    public static final Setting<Integer> PUBLISH_PORT =
        intSetting("transport.publish_port", -1, -1, Setting.Property.NodeScope);
    public static final Setting.AffixSetting<Integer> PUBLISH_PORT_PROFILE = affixKeySetting("transport.profiles.", "publish_port",
        key -> intSetting(key, -1, -1, Setting.Property.NodeScope));
    public static final Setting<Compression.Enabled> OLD_TRANSPORT_COMPRESS =
        enumSetting(Compression.Enabled.class, "transport.tcp.compress", Compression.Enabled.FALSE, Setting.Property.NodeScope,
            Setting.Property.Deprecated);
    public static final Setting<Compression.Enabled> TRANSPORT_COMPRESS =
        enumSetting(Compression.Enabled.class, "transport.compress", OLD_TRANSPORT_COMPRESS, (e) -> {}, Setting.Property.NodeScope);
    public static final Setting<Compression.Scheme> TRANSPORT_COMPRESSION_SCHEME =
        enumSetting(Compression.Scheme.class, "transport.compression_scheme", Compression.Scheme.DEFLATE,
            Setting.Property.NodeScope);
    // the scheduled internal ping interval setting, defaults to disabled (-1)
    public static final Setting<TimeValue> PING_SCHEDULE =
        timeSetting("transport.ping_schedule", TimeValue.timeValueSeconds(-1), Setting.Property.NodeScope);
    public static final Setting<TimeValue> TCP_CONNECT_TIMEOUT =
        timeSetting("transport.tcp.connect_timeout", NetworkService.TCP_CONNECT_TIMEOUT, Setting.Property.NodeScope,
            Setting.Property.Deprecated);
    public static final Setting<TimeValue> CONNECT_TIMEOUT =
        timeSetting("transport.connect_timeout", TCP_CONNECT_TIMEOUT, Setting.Property.NodeScope);
    public static final Setting<Settings> DEFAULT_FEATURES_SETTING = Setting.groupSetting(FEATURE_PREFIX + ".", Setting.Property.NodeScope);

    // Tcp socket settings

    public static final Setting<Boolean> OLD_TCP_NO_DELAY =
        boolSetting("transport.tcp_no_delay", NetworkService.TCP_NO_DELAY, Setting.Property.NodeScope, Setting.Property.Deprecated);
    public static final Setting<Boolean> TCP_NO_DELAY =
        boolSetting("transport.tcp.no_delay", OLD_TCP_NO_DELAY, Setting.Property.NodeScope);
    public static final Setting.AffixSetting<Boolean> OLD_TCP_NO_DELAY_PROFILE =
        affixKeySetting("transport.profiles.", "tcp_no_delay", key -> boolSetting(key, TCP_NO_DELAY, Setting.Property.NodeScope,
            Setting.Property.Deprecated));
    public static final Setting.AffixSetting<Boolean> TCP_NO_DELAY_PROFILE =
        affixKeySetting("transport.profiles.", "tcp.no_delay",
            key -> boolSetting(key,
                fallback(key, OLD_TCP_NO_DELAY_PROFILE, "tcp\\.no_delay$", "tcp_no_delay"),
                Setting.Property.NodeScope));
    public static final Setting<Boolean> TCP_KEEP_ALIVE =
        boolSetting("transport.tcp.keep_alive", NetworkService.TCP_KEEP_ALIVE, Setting.Property.NodeScope);
    public static final Setting.AffixSetting<Boolean> OLD_TCP_KEEP_ALIVE_PROFILE =
        affixKeySetting("transport.profiles.", "tcp_keep_alive",
            key -> boolSetting(key, TCP_KEEP_ALIVE, Setting.Property.NodeScope, Setting.Property.Deprecated));
    public static final Setting.AffixSetting<Boolean> TCP_KEEP_ALIVE_PROFILE =
        affixKeySetting("transport.profiles.", "tcp.keep_alive",
            key -> boolSetting(key,
                fallback(key, OLD_TCP_KEEP_ALIVE_PROFILE, "tcp\\.keep_alive$", "tcp_keep_alive"),
                Setting.Property.NodeScope));
    public static final Setting<Integer> TCP_KEEP_IDLE =
        intSetting("transport.tcp.keep_idle", NetworkService.TCP_KEEP_IDLE, -1, 300, Setting.Property.NodeScope);
    public static final Setting.AffixSetting<Integer> TCP_KEEP_IDLE_PROFILE =
        affixKeySetting("transport.profiles.", "tcp.keep_idle",
            key -> intSetting(key, TCP_KEEP_IDLE, -1, 300, Setting.Property.NodeScope));
    public static final Setting<Integer> TCP_KEEP_INTERVAL =
        intSetting("transport.tcp.keep_interval", NetworkService.TCP_KEEP_INTERVAL, -1, 300, Setting.Property.NodeScope);
    public static final Setting.AffixSetting<Integer> TCP_KEEP_INTERVAL_PROFILE =
        affixKeySetting("transport.profiles.", "tcp.keep_interval",
            key -> intSetting(key, TCP_KEEP_INTERVAL, -1, 300, Setting.Property.NodeScope));
    public static final Setting<Integer> TCP_KEEP_COUNT =
        intSetting("transport.tcp.keep_count", NetworkService.TCP_KEEP_COUNT, -1, Setting.Property.NodeScope);
    public static final Setting.AffixSetting<Integer> TCP_KEEP_COUNT_PROFILE =
        affixKeySetting("transport.profiles.", "tcp.keep_count",
            key -> intSetting(key, TCP_KEEP_COUNT, -1, Setting.Property.NodeScope));
    public static final Setting<Boolean> TCP_REUSE_ADDRESS =
        boolSetting("transport.tcp.reuse_address", NetworkService.TCP_REUSE_ADDRESS, Setting.Property.NodeScope);
    public static final Setting.AffixSetting<Boolean> OLD_TCP_REUSE_ADDRESS_PROFILE =
        affixKeySetting("transport.profiles.", "reuse_address", key -> boolSetting(key, TCP_REUSE_ADDRESS, Setting.Property.NodeScope,
            Setting.Property.Deprecated));
    public static final Setting.AffixSetting<Boolean> TCP_REUSE_ADDRESS_PROFILE =
        affixKeySetting("transport.profiles.", "tcp.reuse_address",
            key -> boolSetting(key,
                fallback(key, OLD_TCP_REUSE_ADDRESS_PROFILE, "tcp\\.reuse_address$", "reuse_address"),
                Setting.Property.NodeScope));
    public static final Setting<ByteSizeValue> TCP_SEND_BUFFER_SIZE =
        Setting.byteSizeSetting("transport.tcp.send_buffer_size", NetworkService.TCP_SEND_BUFFER_SIZE, Setting.Property.NodeScope);
    public static final Setting.AffixSetting<ByteSizeValue> OLD_TCP_SEND_BUFFER_SIZE_PROFILE =
        affixKeySetting("transport.profiles.", "send_buffer_size",
            key -> Setting.byteSizeSetting(key, TCP_SEND_BUFFER_SIZE, Setting.Property.NodeScope, Setting.Property.Deprecated));
    public static final Setting.AffixSetting<ByteSizeValue> TCP_SEND_BUFFER_SIZE_PROFILE =
        affixKeySetting("transport.profiles.", "tcp.send_buffer_size",
            key -> Setting.byteSizeSetting(key,
                fallback(key, OLD_TCP_SEND_BUFFER_SIZE_PROFILE, "tcp\\.send_buffer_size$", "send_buffer_size"),
                Setting.Property.NodeScope));
    public static final Setting<ByteSizeValue> TCP_RECEIVE_BUFFER_SIZE =
        Setting.byteSizeSetting("transport.tcp.receive_buffer_size", NetworkService.TCP_RECEIVE_BUFFER_SIZE, Setting.Property.NodeScope);
    public static final Setting.AffixSetting<ByteSizeValue> OLD_TCP_RECEIVE_BUFFER_SIZE_PROFILE =
        affixKeySetting("transport.profiles.", "receive_buffer_size",
            key -> Setting.byteSizeSetting(key, TCP_RECEIVE_BUFFER_SIZE, Setting.Property.NodeScope, Setting.Property.Deprecated));
    public static final Setting.AffixSetting<ByteSizeValue> TCP_RECEIVE_BUFFER_SIZE_PROFILE =
        affixKeySetting("transport.profiles.", "tcp.receive_buffer_size",
            key -> Setting.byteSizeSetting(key,
                fallback(key, OLD_TCP_RECEIVE_BUFFER_SIZE_PROFILE, "tcp\\.receive_buffer_size$", "receive_buffer_size"),
                Setting.Property.NodeScope));

    // Connections per node settings

    public static final Setting<Integer> CONNECTIONS_PER_NODE_RECOVERY =
        intSetting("transport.connections_per_node.recovery", 2, 1, Setting.Property.NodeScope);
    public static final Setting<Integer> CONNECTIONS_PER_NODE_BULK =
        intSetting("transport.connections_per_node.bulk", 3, 1, Setting.Property.NodeScope);
    public static final Setting<Integer> CONNECTIONS_PER_NODE_REG =
        intSetting("transport.connections_per_node.reg", 6, 1, Setting.Property.NodeScope);
    public static final Setting<Integer> CONNECTIONS_PER_NODE_STATE =
        intSetting("transport.connections_per_node.state", 1, 1, Setting.Property.NodeScope);
    public static final Setting<Integer> CONNECTIONS_PER_NODE_PING =
        intSetting("transport.connections_per_node.ping", 1, 1, Setting.Property.NodeScope);

    // Tracer settings

    public static final Setting<List<String>> TRACE_LOG_INCLUDE_SETTING =
        listSetting("transport.tracer.include", emptyList(), Function.identity(), Setting.Property.Dynamic, Setting.Property.NodeScope);
    public static final Setting<List<String>> TRACE_LOG_EXCLUDE_SETTING =
        listSetting("transport.tracer.exclude",
            Arrays.asList("internal:discovery/zen/fd*", "internal:coordination/fault_detection/*", TransportLivenessAction.NAME),
            Function.identity(), Setting.Property.Dynamic, Setting.Property.NodeScope);

    // Time that processing an inbound message on a transport thread may take at the most before a warning is logged
    public static final Setting<TimeValue> SLOW_OPERATION_THRESHOLD_SETTING =
            Setting.positiveTimeSetting("transport.slow_operation_logging_threshold", TimeValue.timeValueSeconds(5),
                    Setting.Property.Dynamic, Setting.Property.NodeScope);


    private TransportSettings() {
    }

    private static  <T> Setting<T> fallback(String key, Setting.AffixSetting<T> affixSetting, String regex, String replacement) {
        return "_na_".equals(key) ? affixSetting.getConcreteSettingForNamespace(key)
            : affixSetting.getConcreteSetting(key.replaceAll(regex, replacement));
    }
}
