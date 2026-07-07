package com.geek.websim.runtime.tcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geek.websim.runtime.SimulationRuleSnapshot;
import com.geek.websim.web.model.entity.SimulationConfig;
import com.geek.websim.web.model.entity.SimulationResponse;
import com.geek.websim.web.model.entity.TcpRule;
import com.geek.websim.web.model.enums.ProtocolType;
import com.geek.websim.web.model.enums.TcpFrameMode;
import com.geek.websim.web.service.impl.SimulationConfigServiceImpl;
import com.geek.websim.web.service.impl.SimulationRuntimeServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class TcpSimulationServerManagerTest {
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(3);

    private TcpSimulationServerManager manager;

    @TempDir
    Path tempDir;

    @AfterEach
    void stopManager() {
        if (manager != null) {
            manager.stopAll();
        }
    }

    @Test
    void refreshedManagerServesConfiguredLineBasedTcpResponse() {
        int port = randomAvailablePort();
        SimulationRuleSnapshot snapshot = snapshot(tcpConfig("tcp random port", port, "PONG {{tcp.body}}\n"));
        manager = new TcpSimulationServerManager();

        manager.refreshServers(snapshot);

        String response = sendAndReceive(port, "PING ok\n", 1);

        assertThat(response).contains("PONG").contains("PING ok");
    }

    @Test
    void parserExtractsMultipleFramesFromOneChunkAndTrimsCrlf() {
        TcpSimulationServerManager.LineFrameParser parser = new TcpSimulationServerManager.LineFrameParser(16);

        List<String> frames = parser.accept("PING one\nPING two\r\npartial");
        frames.addAll(parser.accept(" three\n"));

        assertThat(frames).containsExactly("PING one", "PING two", "partial three");
    }

    @Test
    void parserDiscardsOversizedFrameUntilNextNewline() {
        TcpSimulationServerManager.LineFrameParser parser = new TcpSimulationServerManager.LineFrameParser(5);

        List<String> frames = parser.accept("123456");
        frames.addAll(parser.accept("789\nOK\n"));

        assertThat(frames).containsExactly("ERR frame too large\n", "OK");
    }

    @Test
    void refreshWithDisabledTcpConfigStopsServer() {
        int port = randomAvailablePort();
        SimulationConfig config = tcpConfig("tcp disable", port, "PONG\n");
        manager = new TcpSimulationServerManager();
        manager.refreshServers(SimulationRuleSnapshot.from(List.of(config)));

        assertThat(manager.hasServer(port)).isTrue();

        config.setEnabled(false);
        manager.refreshServers(SimulationRuleSnapshot.from(List.of(config)));

        assertThat(manager.hasServer(port)).isFalse();
    }

    @Test
    void failedNewBindDoesNotPublishBrokenSnapshotOrStopExistingServers() {
        int existingPort = randomAvailablePort();
        int blockedPort = randomAvailablePort();
        manager = new TcpSimulationServerManager();
        manager.refreshServers(SimulationRuleSnapshot.from(List.of(
                tcpConfig("existing", existingPort, "OLD {{tcp.body}}\n")
        )));

        assertThatThrownBy(() -> manager.refreshServers(SimulationRuleSnapshot.from(List.of(
                tcpConfig("existing", existingPort, "NEW {{tcp.body}}\n"),
                tcpConfig("blocked", blockedPort, "BLOCKED {{tcp.body}}\n", "not-a-real-host.invalid")
        )))).isInstanceOf(RuntimeException.class);

        assertThat(manager.hasServer(existingPort)).isTrue();
        assertThat(manager.hasServer(blockedPort)).isFalse();
        assertThat(sendAndReceive(existingPort, "PING old\n", 1)).contains("OLD PING old");
    }


    @Test
    void failedRemovedServerStopDoesNotPublishSnapshotOrRemoveServerMaps() {
        int existingPort = 12345;
        DisposableServer existingServer = mock(DisposableServer.class);
        RuntimeException stopFailure = new IllegalStateException("dispose failed");
        doThrow(stopFailure).when(existingServer).disposeNow(Duration.ofSeconds(2));
        SimulationRuleSnapshot oldSnapshot = SimulationRuleSnapshot.from(List.of(
                tcpConfig("existing", existingPort, "OLD {{tcp.body}}\n")
        ));
        manager = new TcpSimulationServerManager();
        installedServers(manager).put(existingPort, existingServer);
        installedHosts(manager).put(existingPort, "127.0.0.1");
        setCurrentSnapshot(manager, oldSnapshot);

        try {
            assertThatThrownBy(() -> manager.refreshServers(SimulationRuleSnapshot.empty()))
                    .isSameAs(stopFailure);

            assertThat(manager.hasServer(existingPort)).isTrue();
            assertThat(installedHosts(manager)).containsEntry(existingPort, "127.0.0.1");
            assertThat(currentSnapshot(manager)).isSameAs(oldSnapshot);
        } finally {
            manager = null;
        }
    }

    private SimulationRuleSnapshot snapshot(SimulationConfig... configs) {
        SimulationConfigServiceImpl configService = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        for (SimulationConfig config : configs) {
            configService.create(config);
        }
        SimulationRuntimeServiceImpl runtimeService = new SimulationRuntimeServiceImpl(configService);
        SimulationRuleSnapshot snapshot = runtimeService.compile();
        runtimeService.publish(snapshot);
        return snapshot;
    }


    @SuppressWarnings("unchecked")
    private Map<Integer, DisposableServer> installedServers(TcpSimulationServerManager target) {
        return (Map<Integer, DisposableServer>) org.springframework.test.util.ReflectionTestUtils.getField(target, "servers");
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, String> installedHosts(TcpSimulationServerManager target) {
        return (Map<Integer, String>) org.springframework.test.util.ReflectionTestUtils.getField(target, "serverHosts");
    }

    private SimulationRuleSnapshot currentSnapshot(TcpSimulationServerManager target) {
        return (SimulationRuleSnapshot) org.springframework.test.util.ReflectionTestUtils.getField(target, "currentSnapshot");
    }

    private void setCurrentSnapshot(TcpSimulationServerManager target, SimulationRuleSnapshot snapshot) {
        org.springframework.test.util.ReflectionTestUtils.setField(target, "currentSnapshot", snapshot);
    }

    private String sendAndReceive(int port, String request, int expectedFrames) {
        Connection connection = TcpClient.create()
                .host("127.0.0.1")
                .port(port)
                .connectNow(IO_TIMEOUT);
        try {
            connection.outbound()
                    .sendString(Mono.just(request))
                    .then()
                    .block(IO_TIMEOUT);
            AtomicInteger receivedLines = new AtomicInteger();
            return connection.inbound()
                    .receive()
                    .asString()
                    .takeUntil(chunk -> receivedLines.addAndGet(countNewlines(chunk)) >= expectedFrames)
                    .collectList()
                    .map(parts -> String.join("", parts))
                    .block(IO_TIMEOUT);
        } finally {
            connection.disposeNow();
        }
    }

    private int countNewlines(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < chunk.length(); i++) {
            if (chunk.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private SimulationConfig tcpConfig(String name, int port, String responseBody) {
        return tcpConfig(name, port, responseBody, "127.0.0.1");
    }

    private SimulationConfig tcpConfig(String name, int port, String responseBody, String host) {
        return SimulationConfig.builder()
                .name(name)
                .protocol(ProtocolType.TCP)
                .enabled(true)
                .tcp(TcpRule.builder()
                        .host(host)
                        .port(port)
                        .frameMode(TcpFrameMode.LINE)
                        .build())
                .defaultResponse(SimulationResponse.builder()
                        .status(200)
                        .body(responseBody)
                        .build())
                .build();
    }

    private int randomAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to allocate random test port", e);
        }
    }
}
