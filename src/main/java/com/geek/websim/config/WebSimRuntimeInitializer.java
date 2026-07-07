package com.geek.websim.config;

import com.geek.websim.runtime.SimulationRuleSnapshot;
import com.geek.websim.runtime.tcp.TcpSimulationServerManager;
import com.geek.websim.web.service.SimulationRuntimeService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class WebSimRuntimeInitializer implements ApplicationRunner {
    private final SimulationRuntimeService runtimeService;
    private final TcpSimulationServerManager tcpSimulationServerManager;

    public WebSimRuntimeInitializer(SimulationRuntimeService runtimeService,
                                    TcpSimulationServerManager tcpSimulationServerManager) {
        this.runtimeService = runtimeService;
        this.tcpSimulationServerManager = tcpSimulationServerManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        SimulationRuleSnapshot snapshot = runtimeService.compile();
        tcpSimulationServerManager.refreshServers(snapshot);
        runtimeService.publish(snapshot);
    }
}
