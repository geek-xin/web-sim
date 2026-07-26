package com.geek.websim.web.service;

import com.geek.websim.runtime.SimulationRuleSnapshot;
import com.geek.websim.runtime.tcp.TcpSimulationServerManager;
import com.geek.websim.web.service.impl.SimulationRuntimeReloaderImpl;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimulationRuntimeReloaderImplTest {

    @Test
    void reloadRefreshesTcpBeforePublishingRuntimeSnapshot() {
        SimulationRuntimeService runtimeService = mock(SimulationRuntimeService.class);
        TcpSimulationServerManager tcpManager = mock(TcpSimulationServerManager.class);
        SimulationRuleSnapshot snapshot = SimulationRuleSnapshot.empty();
        when(runtimeService.compile()).thenReturn(snapshot);

        new SimulationRuntimeReloaderImpl(runtimeService, tcpManager).reload();

        InOrder order = inOrder(runtimeService, tcpManager);
        order.verify(runtimeService).compile();
        order.verify(tcpManager).refreshServers(snapshot);
        order.verify(runtimeService).publish(snapshot);
    }
}
