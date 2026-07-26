package com.geek.websim.web.service;

import com.geek.websim.web.model.dto.SimulationLogEntry;

import java.util.List;

public interface SimulationLogWriter {
    void append(SimulationLogEntry entry);

    default List<SimulationLogEntry> loadRetainedLogs() {
        return List.of();
    }

    void cleanupExpiredLogs();
}
