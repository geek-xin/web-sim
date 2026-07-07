package com.geek.websim.web.service;

import com.geek.websim.web.model.dto.SimulationLogEntry;
import com.geek.websim.web.model.dto.SimulationLogSnapshot;
import com.geek.websim.web.model.enums.ProtocolType;

import java.util.function.Supplier;

public interface SimulationMetricsService {
    void record(ProtocolType protocol,
                int status,
                long durationMs,
                Supplier<SimulationLogEntry> sampledEntrySupplier);

    default void record(SimulationLogEntry entry) {
        if (entry == null) {
            return;
        }
        record(entry.getProtocol(), entry.getStatus(), entry.getDurationMs(), () -> entry);
    }

    SimulationLogSnapshot snapshot();
}
