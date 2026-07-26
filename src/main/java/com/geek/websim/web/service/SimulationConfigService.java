package com.geek.websim.web.service;

import com.geek.websim.web.model.entity.SimulationConfig;

import java.util.List;

public interface SimulationConfigService {
    void initDefaultConfigs();
    List<SimulationConfig> listAll();
    SimulationConfig getById(String id);
    SimulationConfig create(SimulationConfig config);
    SimulationConfig update(String id, SimulationConfig config);
    /**
     * Rollback-only restore that writes {@code config} with the exact {@code id}
     * whether or not the backing file currently exists, using normal validation
     * and conflict checks while excluding {@code id}.
     */
    SimulationConfig restore(String id, SimulationConfig config);
    void delete(String id);
    String rawJson(String id);
    List<SimulationConfig> importAll(List<SimulationConfig> configs);
    String exportAllJson();
}
