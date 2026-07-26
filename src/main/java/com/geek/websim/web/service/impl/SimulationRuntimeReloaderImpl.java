package com.geek.websim.web.service.impl;

import com.geek.websim.runtime.SimulationRuleSnapshot;
import com.geek.websim.runtime.tcp.TcpSimulationServerManager;
import com.geek.websim.web.service.SimulationRuntimeReloader;
import com.geek.websim.web.service.SimulationRuntimeService;
import org.springframework.stereotype.Service;

@Service
public class SimulationRuntimeReloaderImpl implements SimulationRuntimeReloader {
    private final SimulationRuntimeService runtimeService;
    private final TcpSimulationServerManager tcpSimulationServerManager;

    public SimulationRuntimeReloaderImpl(SimulationRuntimeService runtimeService,
                                         TcpSimulationServerManager tcpSimulationServerManager) {
        this.runtimeService = runtimeService;
        this.tcpSimulationServerManager = tcpSimulationServerManager;
    }

    @Override
    public synchronized SimulationRuleSnapshot reload() {
        SimulationRuleSnapshot snapshot = runtimeService.compile();
        tcpSimulationServerManager.refreshServers(snapshot);
        runtimeService.publish(snapshot);
        return snapshot;
    }
}
