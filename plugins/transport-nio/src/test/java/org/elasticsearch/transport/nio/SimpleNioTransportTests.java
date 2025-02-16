/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.transport.nio;

import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.jdk.JavaVersion;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.util.MockPageCacheRecycler;
import org.elasticsearch.core.internal.io.IOUtils;
import org.elasticsearch.core.internal.net.NetUtils;
import org.elasticsearch.indices.breaker.NoneCircuitBreakerService;
import org.elasticsearch.test.transport.MockTransportService;
import org.elasticsearch.test.transport.StubbableTransport;
import org.elasticsearch.transport.AbstractSimpleTransportTestCase;
import org.elasticsearch.transport.ConnectTransportException;
import org.elasticsearch.transport.ConnectionProfile;
import org.elasticsearch.transport.TcpChannel;
import org.elasticsearch.transport.TcpTransport;
import org.elasticsearch.transport.TestProfiles;
import org.elasticsearch.transport.Transport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.Collections;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

public class SimpleNioTransportTests extends AbstractSimpleTransportTestCase {

    @Override
    protected Transport build(Settings settings, final Version version, ClusterSettings clusterSettings, boolean doHandshake) {
        NamedWriteableRegistry namedWriteableRegistry = new NamedWriteableRegistry(Collections.emptyList());
        NetworkService networkService = new NetworkService(Collections.emptyList());
        return new NioTransport(settings, version, threadPool, networkService, new MockPageCacheRecycler(settings),
            namedWriteableRegistry, new NoneCircuitBreakerService(), new NioGroupFactory(settings, logger)) {

            @Override
            public void executeHandshake(DiscoveryNode node, TcpChannel channel, ConnectionProfile profile,
                                         ActionListener<Version> listener) {
                if (doHandshake) {
                    super.executeHandshake(node, channel, profile, listener);
                } else {
                    listener.onResponse(version.minimumCompatibilityVersion());
                }
            }
        };
    }

    public void testConnectException() throws UnknownHostException {
        try {
            serviceA.connectToNode(new DiscoveryNode("C", new TransportAddress(InetAddress.getByName("localhost"), 9876),
                emptyMap(), emptySet(),Version.CURRENT));
            fail("Expected ConnectTransportException");
        } catch (ConnectTransportException e) {
            assertThat(e.getMessage(), containsString("connect_exception"));
            assertThat(e.getMessage(), containsString("[127.0.0.1:9876]"));
            Throwable cause = e.getCause();
            assertThat(cause, instanceOf(IOException.class));
        }
    }

    public void testDefaultKeepAliveSettings() throws IOException {
        assumeTrue("setting default keepalive options not supported on this platform",
            (IOUtils.LINUX || IOUtils.MAC_OS_X) &&
                JavaVersion.current().compareTo(JavaVersion.parse("11")) >= 0);
        try (MockTransportService serviceC = buildService("TS_C", Version.CURRENT, Settings.EMPTY);
             MockTransportService serviceD = buildService("TS_D", Version.CURRENT, Settings.EMPTY);
             Transport.Connection connection = serviceC.openConnection(serviceD.getLocalDiscoNode(), TestProfiles.LIGHT_PROFILE)) {

            assertThat(connection, instanceOf(StubbableTransport.WrappedConnection.class));
            Transport.Connection conn = ((StubbableTransport.WrappedConnection) connection).getConnection();
            assertThat(conn, instanceOf(TcpTransport.NodeChannels.class));
            TcpTransport.NodeChannels nodeChannels = (TcpTransport.NodeChannels) conn;
            for (TcpChannel channel : nodeChannels.getChannels()) {
                assertFalse(channel.isServerChannel());
                checkDefaultKeepAliveOptions(channel);
            }

            assertThat(serviceD.getOriginalTransport(), instanceOf(TcpTransport.class));
            for (TcpChannel channel : getAcceptedChannels((TcpTransport) serviceD.getOriginalTransport())) {
                assertTrue(channel.isServerChannel());
                checkDefaultKeepAliveOptions(channel);
            }
        }
    }

    private void checkDefaultKeepAliveOptions(TcpChannel channel) throws IOException {
        assertThat(channel, instanceOf(NioTcpChannel.class));
        NioTcpChannel nioChannel = (NioTcpChannel) channel;
        SocketChannel socketChannel = nioChannel.getRawChannel();
        assertThat(socketChannel.supportedOptions(), hasItem(NetUtils.getTcpKeepIdleSocketOptionOrNull()));
        Integer keepIdle = socketChannel.getOption(NetUtils.getTcpKeepIdleSocketOptionOrNull());
        assertNotNull(keepIdle);
        assertThat(keepIdle, lessThanOrEqualTo(500));
        assertThat(socketChannel.supportedOptions(), hasItem(NetUtils.getTcpKeepIntervalSocketOptionOrNull()));
        Integer keepInterval = socketChannel.getOption(NetUtils.getTcpKeepIntervalSocketOptionOrNull());
        assertNotNull(keepInterval);
        assertThat(keepInterval, lessThanOrEqualTo(500));
    }
}
