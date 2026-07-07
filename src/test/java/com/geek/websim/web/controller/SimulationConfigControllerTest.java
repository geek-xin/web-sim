package com.geek.websim.web.controller;

import com.geek.websim.runtime.SimulationRuleSnapshot;
import com.geek.websim.runtime.tcp.TcpSimulationServerManager;
import com.geek.websim.web.model.dto.SimulationConfigDto;
import com.geek.websim.web.model.entity.HttpRule;
import com.geek.websim.web.model.entity.SimulationConfig;
import com.geek.websim.web.model.entity.SimulationResponse;
import com.geek.websim.web.model.enums.HttpMatchMode;
import com.geek.websim.web.model.enums.ProtocolType;
import com.geek.websim.web.service.SimulationConfigService;
import com.geek.websim.web.service.SimulationRuntimeService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimulationConfigControllerTest {

    @Test
    void createPublishesRuntimeSnapshotOnlyAfterTcpRefreshSucceeds() {
        SimulationConfigService configService = mock(SimulationConfigService.class);
        SimulationRuntimeService runtimeService = mock(SimulationRuntimeService.class);
        TcpSimulationServerManager tcpManager = mock(TcpSimulationServerManager.class);
        SimulationConfigController controller = new SimulationConfigController(configService, runtimeService, tcpManager);
        SimulationConfig config = httpConfig();
        SimulationRuleSnapshot snapshot = SimulationRuleSnapshot.from(java.util.List.of(config));
        when(configService.create(config)).thenReturn(config);
        when(runtimeService.compile()).thenReturn(snapshot);

        controller.create(dto());

        InOrder order = inOrder(runtimeService, tcpManager);
        order.verify(runtimeService).compile();
        order.verify(tcpManager).refreshServers(snapshot);
        order.verify(runtimeService).publish(snapshot);
    }

    @Test
    void createDoesNotPublishRuntimeSnapshotWhenTcpRefreshFails() {
        SimulationConfigService configService = mock(SimulationConfigService.class);
        SimulationRuntimeService runtimeService = mock(SimulationRuntimeService.class);
        TcpSimulationServerManager tcpManager = mock(TcpSimulationServerManager.class);
        SimulationConfigController controller = new SimulationConfigController(configService, runtimeService, tcpManager);
        SimulationConfig createRequest = httpConfig();
        SimulationConfig created = httpConfig();
        created.setId("sim-created");
        SimulationRuleSnapshot snapshot = SimulationRuleSnapshot.from(java.util.List.of(created));
        SimulationRuleSnapshot previousSnapshot = SimulationRuleSnapshot.empty();
        RuntimeException bindFailure = new RuntimeException("bind failed");
        when(configService.create(createRequest)).thenReturn(created);
        when(runtimeService.compile()).thenReturn(snapshot, previousSnapshot);
        doThrow(bindFailure).when(tcpManager).refreshServers(snapshot);

        assertThatThrownBy(() -> controller.create(dto())).isSameAs(bindFailure);

        verify(runtimeService, never()).publish(snapshot);
        verify(configService).delete("sim-created");
        verify(tcpManager).refreshServers(previousSnapshot);
        verify(runtimeService).publish(previousSnapshot);
    }

    @Test
    void updateRestoresPreviousConfigAndPublishesPreviousSnapshotWhenTcpRefreshFails() {
        SimulationConfigService configService = mock(SimulationConfigService.class);
        SimulationRuntimeService runtimeService = mock(SimulationRuntimeService.class);
        TcpSimulationServerManager tcpManager = mock(TcpSimulationServerManager.class);
        SimulationConfigController controller = new SimulationConfigController(configService, runtimeService, tcpManager);
        SimulationConfig previous = httpConfig("sim-1", "http-old", "/old");
        SimulationConfig updateRequest = dto("http-new", "/new").toEntity();
        SimulationConfig updated = httpConfig("sim-1", "http-new", "/new");
        SimulationRuleSnapshot updatedSnapshot = SimulationRuleSnapshot.from(java.util.List.of(updated));
        SimulationRuleSnapshot previousSnapshot = SimulationRuleSnapshot.from(java.util.List.of(previous));
        RuntimeException bindFailure = new RuntimeException("bind failed");
        when(configService.getById("sim-1")).thenReturn(previous);
        when(configService.update("sim-1", updateRequest)).thenReturn(updated);
        when(runtimeService.compile()).thenReturn(updatedSnapshot, previousSnapshot);
        doThrow(bindFailure).when(tcpManager).refreshServers(updatedSnapshot);

        assertThatThrownBy(() -> controller.update("sim-1", dto("http-new", "/new"))).isSameAs(bindFailure);

        verify(configService).restore("sim-1", previous);
        verify(runtimeService, never()).publish(updatedSnapshot);
        verify(tcpManager).refreshServers(previousSnapshot);
        verify(runtimeService).publish(previousSnapshot);
    }

    private SimulationConfigDto dto() {
        return dto("http", "/ok");
    }

    private SimulationConfigDto dto(String name, String path) {
        return SimulationConfigDto.builder()
                .name(name)
                .protocol(ProtocolType.HTTP)
                .enabled(true)
                .http(HttpRule.builder().method("GET").path(path).matchMode(HttpMatchMode.EXACT).build())
                .defaultResponse(SimulationResponse.builder().status(200).body("ok").build())
                .build();
    }

    private SimulationConfig httpConfig() {
        return dto().toEntity();
    }

    private SimulationConfig httpConfig(String id, String name, String path) {
        SimulationConfig config = dto(name, path).toEntity();
        config.setId(id);
        return config;
    }
}
