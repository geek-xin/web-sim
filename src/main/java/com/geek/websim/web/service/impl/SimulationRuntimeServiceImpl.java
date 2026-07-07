package com.geek.websim.web.service.impl;

import com.geek.websim.runtime.SimulationRuleSnapshot;
import com.geek.websim.web.service.SimulationConfigService;
import com.geek.websim.web.service.SimulationRuntimeService;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class SimulationRuntimeServiceImpl implements SimulationRuntimeService {
    private final SimulationConfigService configService;
    private final AtomicReference<SimulationRuleSnapshot> snapshotRef = new AtomicReference<>(SimulationRuleSnapshot.empty());

    public SimulationRuntimeServiceImpl(SimulationConfigService configService) {
        this.configService = configService;
    }

    @Override
    public SimulationRuleSnapshot current() {
        return snapshotRef.get();
    }

    @Override
    public SimulationRuleSnapshot compile() {
        return SimulationRuleSnapshot.from(configService.listAll());
    }

    @Override
    public void publish(SimulationRuleSnapshot snapshot) {
        snapshotRef.set(snapshot == null ? SimulationRuleSnapshot.empty() : snapshot);
    }

    @Deprecated(forRemoval = false)
    @Override
    public SimulationRuleSnapshot refresh() {
        SimulationRuleSnapshot snapshot = compile();
        publish(snapshot);
        return snapshot;
    }
}
