package com.geek.websim.web.controller;

import com.geek.websim.web.model.dto.SimulationConfigDto;
import com.geek.websim.web.model.entity.HttpRule;
import com.geek.websim.web.model.entity.SimulationConfig;
import com.geek.websim.web.model.entity.SimulationResponse;
import com.geek.websim.web.model.enums.HttpMatchMode;
import com.geek.websim.web.model.enums.ProtocolType;
import com.geek.websim.web.service.SimulationConfigService;
import com.geek.websim.web.service.SimulationRuntimeReloader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimulationConfigControllerTest {

    @Test
    void createPublishesRuntimeSnapshotOnlyAfterTcpRefreshSucceeds() {
        SimulationConfigService configService = mock(SimulationConfigService.class);
        SimulationRuntimeReloader runtimeReloader = mock(SimulationRuntimeReloader.class);
        SimulationConfigController controller = new SimulationConfigController(configService, runtimeReloader);
        SimulationConfig config = httpConfig();
        when(configService.create(config)).thenReturn(config);

        controller.create(dto());

        verify(runtimeReloader).reload();
    }

    @Test
    void createDoesNotPublishRuntimeSnapshotWhenTcpRefreshFails() {
        SimulationConfigService configService = mock(SimulationConfigService.class);
        SimulationRuntimeReloader runtimeReloader = mock(SimulationRuntimeReloader.class);
        SimulationConfigController controller = new SimulationConfigController(configService, runtimeReloader);
        SimulationConfig createRequest = httpConfig();
        SimulationConfig created = httpConfig();
        created.setId("sim-created");
        RuntimeException bindFailure = new RuntimeException("bind failed");
        when(configService.create(createRequest)).thenReturn(created);
        when(runtimeReloader.reload()).thenThrow(bindFailure).thenReturn(null);

        assertThatThrownBy(() -> controller.create(dto())).isSameAs(bindFailure);

        verify(configService).delete("sim-created");
        verify(runtimeReloader, org.mockito.Mockito.times(2)).reload();
    }

    @Test
    void updateRestoresPreviousConfigAndPublishesPreviousSnapshotWhenTcpRefreshFails() {
        SimulationConfigService configService = mock(SimulationConfigService.class);
        SimulationRuntimeReloader runtimeReloader = mock(SimulationRuntimeReloader.class);
        SimulationConfigController controller = new SimulationConfigController(configService, runtimeReloader);
        SimulationConfig previous = httpConfig("sim-1", "http-old", "/old");
        SimulationConfig updateRequest = dto("http-new", "/new").toEntity();
        SimulationConfig updated = httpConfig("sim-1", "http-new", "/new");
        RuntimeException bindFailure = new RuntimeException("bind failed");
        when(configService.getById("sim-1")).thenReturn(previous);
        when(configService.update("sim-1", updateRequest)).thenReturn(updated);
        when(runtimeReloader.reload()).thenThrow(bindFailure).thenReturn(null);

        assertThatThrownBy(() -> controller.update("sim-1", dto("http-new", "/new"))).isSameAs(bindFailure);

        verify(configService).restore("sim-1", previous);
        verify(runtimeReloader, org.mockito.Mockito.times(2)).reload();
        verify(configService, never()).delete("sim-1");
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
