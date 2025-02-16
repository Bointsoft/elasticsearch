/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.eql.plugin;

import org.apache.lucene.util.SetOnce;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.breaker.CircuitBreaker;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.indices.breaker.BreakerSettings;
import org.elasticsearch.license.XPackLicenseState;
import org.elasticsearch.monitor.jvm.JvmInfo;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.CircuitBreakerPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.repositories.RepositoriesService;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.watcher.ResourceWatcherService;
import org.elasticsearch.xpack.core.XPackPlugin;
import org.elasticsearch.xpack.eql.EqlFeatureSet;
import org.elasticsearch.xpack.eql.action.EqlSearchAction;
import org.elasticsearch.xpack.eql.execution.PlanExecutor;
import org.elasticsearch.xpack.ql.index.IndexResolver;
import org.elasticsearch.xpack.ql.type.DefaultDataTypeRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class EqlPlugin extends Plugin implements ActionPlugin, CircuitBreakerPlugin {

    private static final String CIRCUIT_BREAKER_NAME = "eql_sequence";
    private static final long CIRCUIT_BREAKER_LIMIT = (long)((0.50) * JvmInfo.jvmInfo().getMem().getHeapMax().getBytes());
    private static final double CIRCUIT_BREAKER_OVERHEAD = 1.0D;
    private final SetOnce<CircuitBreaker> circuitBreaker = new SetOnce<>();

    public static final Setting<Boolean> EQL_ENABLED_SETTING = Setting.boolSetting(
        "xpack.eql.enabled",
        true,
        Setting.Property.NodeScope,
        Setting.Property.Deprecated
    );

    public EqlPlugin() {
    }

    @Override
    public Collection<Object> createComponents(Client client, ClusterService clusterService, ThreadPool threadPool,
            ResourceWatcherService resourceWatcherService, ScriptService scriptService, NamedXContentRegistry xContentRegistry,
            Environment environment, NodeEnvironment nodeEnvironment, NamedWriteableRegistry namedWriteableRegistry,
            IndexNameExpressionResolver expressionResolver, Supplier<RepositoriesService> repositoriesServiceSupplier) {
        return createComponents(client, clusterService.getClusterName().value());
    }

    private Collection<Object> createComponents(Client client, String clusterName) {
        IndexResolver indexResolver = new IndexResolver(client, clusterName, DefaultDataTypeRegistry.INSTANCE);
        PlanExecutor planExecutor = new PlanExecutor(client, indexResolver, circuitBreaker.get());
        return Collections.singletonList(planExecutor);
    }

    @Override
    public Collection<Module> createGuiceModules() {
        List<Module> modules = new ArrayList<>();
        modules.add(b -> XPackPlugin.bindFeatureSet(b, EqlFeatureSet.class));
        return modules;
    }

    /**
     * The settings defined by EQL plugin.
     *
     * @return the settings
     */
    @Override
    public List<Setting<?>> getSettings() {
        return Collections.singletonList(EQL_ENABLED_SETTING);
    }

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return org.elasticsearch.core.List.of(
            new ActionHandler<>(EqlSearchAction.INSTANCE, TransportEqlSearchAction.class),
            new ActionHandler<>(EqlStatsAction.INSTANCE, TransportEqlStatsAction.class),
            new ActionHandler<>(EqlAsyncGetResultAction.INSTANCE, TransportEqlAsyncGetResultsAction.class),
            new ActionHandler<>(EqlAsyncGetStatusAction.INSTANCE, TransportEqlAsyncGetStatusAction.class)
        );
    }

    @Override
    public List<RestHandler> getRestHandlers(Settings settings,
                                             RestController restController,
                                             ClusterSettings clusterSettings,
                                             IndexScopedSettings indexScopedSettings,
                                             SettingsFilter settingsFilter,
                                             IndexNameExpressionResolver indexNameExpressionResolver,
                                             Supplier<DiscoveryNodes> nodesInCluster) {

        return Arrays.asList(
            new RestEqlSearchAction(),
            new RestEqlStatsAction(),
            new RestEqlGetAsyncResultAction(),
            new RestEqlGetAsyncStatusAction(),
            new RestEqlDeleteAsyncResultAction()
        );
    }

    // overridable by tests
    protected XPackLicenseState getLicenseState() {
        return XPackPlugin.getSharedLicenseState();
    }

    @Override
    public BreakerSettings getCircuitBreaker(Settings settings) {
        return BreakerSettings.updateFromSettings(
                new BreakerSettings(
                        CIRCUIT_BREAKER_NAME,
                        CIRCUIT_BREAKER_LIMIT,
                        CIRCUIT_BREAKER_OVERHEAD,
                        CircuitBreaker.Type.MEMORY,
                        CircuitBreaker.Durability.TRANSIENT
                ),
                settings);
    }

    @Override
    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        assert circuitBreaker.getName().equals(CIRCUIT_BREAKER_NAME);
        this.circuitBreaker.set(circuitBreaker);
    }
}
