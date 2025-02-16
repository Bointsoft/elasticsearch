/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.datastreams;

import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.cluster.metadata.ComposableIndexTemplate;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.indices.ExecutorNames;
import org.elasticsearch.indices.SystemDataStreamDescriptor;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.SystemIndexPlugin;
import org.elasticsearch.snapshots.AbstractSnapshotIntegTestCase;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.mockstore.MockRepository;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.core.action.CreateDataStreamAction;
import org.elasticsearch.xpack.core.action.DeleteDataStreamAction;
import org.elasticsearch.xpack.core.action.GetDataStreamAction;
import org.elasticsearch.xpack.datastreams.DataStreamsPlugin;
import org.junit.After;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

import static org.elasticsearch.datastreams.SystemDataStreamSnapshotIT.SystemDataStreamTestPlugin.SYSTEM_DATA_STREAM_NAME;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.oneOf;

@ESIntegTestCase.ClusterScope(transportClientRatio = 0)
public class SystemDataStreamSnapshotIT extends AbstractSnapshotIntegTestCase {

    public static final String REPO = "repo";
    public static final String SNAPSHOT = "snap";

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return org.elasticsearch.core.List.of(MockRepository.Plugin.class, DataStreamsPlugin.class, SystemDataStreamTestPlugin.class);
    }

    @After
    public void cleanUp() throws Exception {
        DeleteDataStreamAction.Request request = new DeleteDataStreamAction.Request(new String[] { SYSTEM_DATA_STREAM_NAME });
        AcknowledgedResponse response = client().execute(DeleteDataStreamAction.INSTANCE, request).get();
        assertTrue(response.isAcknowledged());
    }

    public void testSystemDataStreamSnapshotIT() throws Exception {
        Path location = randomRepoPath();
        createRepository(REPO, "fs", location);

        {
            CreateDataStreamAction.Request request = new CreateDataStreamAction.Request(SYSTEM_DATA_STREAM_NAME);
            final AcknowledgedResponse response = client().execute(CreateDataStreamAction.INSTANCE, request).get();
            assertTrue(response.isAcknowledged());
        }

        // Index a doc so that a concrete backing index will be created
        IndexResponse indexRepsonse = client().prepareIndex(SYSTEM_DATA_STREAM_NAME, "_doc")
            .setId("42")
            .setSource("{ \"@timestamp\": \"2099-03-08T11:06:07.000Z\", \"name\": \"my-name\" }", XContentType.JSON)
            .setOpType(DocWriteRequest.OpType.CREATE)
            .execute()
            .actionGet();
        assertThat(indexRepsonse.status().getStatus(), oneOf(200, 201));

        {
            GetDataStreamAction.Request request = new GetDataStreamAction.Request(new String[] { SYSTEM_DATA_STREAM_NAME });
            GetDataStreamAction.Response response = client().execute(GetDataStreamAction.INSTANCE, request).get();
            assertThat(response.getDataStreams(), hasSize(1));
            assertTrue(response.getDataStreams().get(0).getDataStream().isSystem());
        }

        assertSuccessful(
            client().admin()
                .cluster()
                .prepareCreateSnapshot(REPO, SNAPSHOT)
                .setWaitForCompletion(true)
                .setIncludeGlobalState(false)
                .execute()
        );

        // We have to delete the data stream directly, as the feature reset API doesn't clean up system data streams yet
        // See https://github.com/elastic/elasticsearch/issues/75818
        {
            DeleteDataStreamAction.Request request = new DeleteDataStreamAction.Request(new String[] { SYSTEM_DATA_STREAM_NAME });
            AcknowledgedResponse response = client().execute(DeleteDataStreamAction.INSTANCE, request).get();
            assertTrue(response.isAcknowledged());
        }

        {
            GetIndexResponse indicesRemaining = client().admin().indices().prepareGetIndex().addIndices("_all").get();
            assertThat(indicesRemaining.indices(), arrayWithSize(0));
        }

        RestoreSnapshotResponse restoreSnapshotResponse = client().admin()
            .cluster()
            .prepareRestoreSnapshot(REPO, SNAPSHOT)
            .setWaitForCompletion(true)
            .setRestoreGlobalState(false)
            .get();
        assertEquals(restoreSnapshotResponse.getRestoreInfo().totalShards(), restoreSnapshotResponse.getRestoreInfo().successfulShards());

        {
            GetDataStreamAction.Request request = new GetDataStreamAction.Request(new String[] { SYSTEM_DATA_STREAM_NAME });
            GetDataStreamAction.Response response = client().execute(GetDataStreamAction.INSTANCE, request).get();
            assertThat(response.getDataStreams(), hasSize(1));
            assertTrue(response.getDataStreams().get(0).getDataStream().isSystem());
        }
    }

    public void testSystemDataStreamInFeatureState() throws Exception {
        Path location = randomRepoPath();
        createRepository(REPO, "fs", location);

        {
            CreateDataStreamAction.Request request = new CreateDataStreamAction.Request(SYSTEM_DATA_STREAM_NAME);
            final AcknowledgedResponse response = client().execute(CreateDataStreamAction.INSTANCE, request).get();
            assertTrue(response.isAcknowledged());
        }

        // Index a doc so that a concrete backing index will be created
        IndexResponse indexToDataStreamResponse = client().prepareIndex(SYSTEM_DATA_STREAM_NAME, "_doc")
            .setId("42")
            .setSource("{ \"@timestamp\": \"2099-03-08T11:06:07.000Z\", \"name\": \"my-name\" }", XContentType.JSON)
            .setOpType(DocWriteRequest.OpType.CREATE)
            .execute()
            .actionGet();
        assertThat(indexToDataStreamResponse.status().getStatus(), oneOf(200, 201));

        // Index a doc so that a concrete backing index will be created
        IndexResponse indexResponse = client().prepareIndex("my-index", "_doc")
            .setId("42")
            .setSource("{ \"name\": \"my-name\" }", XContentType.JSON)
            .setOpType(DocWriteRequest.OpType.CREATE)
            .execute()
            .get();
        assertThat(indexResponse.status().getStatus(), oneOf(200, 201));

        {
            GetDataStreamAction.Request request = new GetDataStreamAction.Request(new String[] { SYSTEM_DATA_STREAM_NAME });
            GetDataStreamAction.Response response = client().execute(GetDataStreamAction.INSTANCE, request).get();
            assertThat(response.getDataStreams(), hasSize(1));
            assertTrue(response.getDataStreams().get(0).getDataStream().isSystem());
        }

        SnapshotInfo snapshotInfo = assertSuccessful(
            client().admin()
                .cluster()
                .prepareCreateSnapshot(REPO, SNAPSHOT)
                .setIndices("my-index")
                .setFeatureStates(SystemDataStreamTestPlugin.class.getSimpleName())
                .setWaitForCompletion(true)
                .setIncludeGlobalState(false)
                .execute()
        );

        assertThat(snapshotInfo.dataStreams(), not(empty()));

        // We have to delete the data stream directly, as the feature reset API doesn't clean up system data streams yet
        // See https://github.com/elastic/elasticsearch/issues/75818
        {
            DeleteDataStreamAction.Request request = new DeleteDataStreamAction.Request(new String[] { SYSTEM_DATA_STREAM_NAME });
            AcknowledgedResponse response = client().execute(DeleteDataStreamAction.INSTANCE, request).get();
            assertTrue(response.isAcknowledged());
        }

        assertAcked(client().admin().indices().prepareDelete("my-index"));

        {
            GetIndexResponse indicesRemaining = client().admin().indices().prepareGetIndex().addIndices("_all").get();
            assertThat(indicesRemaining.indices(), arrayWithSize(0));
        }

        RestoreSnapshotResponse restoreSnapshotResponse = client().admin()
            .cluster()
            .prepareRestoreSnapshot(REPO, SNAPSHOT)
            .setWaitForCompletion(true)
            .setIndices("my-index")
            .setFeatureStates(SystemDataStreamTestPlugin.class.getSimpleName())
            .get();
        assertEquals(restoreSnapshotResponse.getRestoreInfo().totalShards(), restoreSnapshotResponse.getRestoreInfo().successfulShards());

        {
            GetDataStreamAction.Request request = new GetDataStreamAction.Request(new String[] { SYSTEM_DATA_STREAM_NAME });
            GetDataStreamAction.Response response = client().execute(GetDataStreamAction.INSTANCE, request).get();
            assertThat(response.getDataStreams(), hasSize(1));
            assertTrue(response.getDataStreams().get(0).getDataStream().isSystem());
        }
    }

    public static class SystemDataStreamTestPlugin extends Plugin implements SystemIndexPlugin {

        static final String SYSTEM_DATA_STREAM_NAME = ".test-data-stream";

        @Override
        public Collection<SystemDataStreamDescriptor> getSystemDataStreamDescriptors() {
            return org.elasticsearch.core.List.of(
                new SystemDataStreamDescriptor(
                    SYSTEM_DATA_STREAM_NAME,
                    "a system data stream for testing",
                    SystemDataStreamDescriptor.Type.EXTERNAL,
                    new ComposableIndexTemplate(
                        org.elasticsearch.core.List.of(".system-data-stream"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new ComposableIndexTemplate.DataStreamTemplate()
                    ),
                    org.elasticsearch.core.Map.of(),
                    Collections.singletonList("test"),
                    new ExecutorNames(ThreadPool.Names.SYSTEM_CRITICAL_READ, ThreadPool.Names.SYSTEM_READ, ThreadPool.Names.SYSTEM_WRITE)
                )
            );
        }

        @Override
        public String getFeatureName() {
            return SystemDataStreamTestPlugin.class.getSimpleName();
        }

        @Override
        public String getFeatureDescription() {
            return "A plugin for testing snapshots of system data streams";
        }
    }
}
