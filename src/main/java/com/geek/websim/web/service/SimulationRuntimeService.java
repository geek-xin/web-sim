package com.geek.websim.web.service;

import com.geek.websim.runtime.SimulationRuleSnapshot;

public interface SimulationRuntimeService {
    SimulationRuleSnapshot current();

    /**
     * Builds a runtime snapshot from persisted configuration without publishing it.
     * Production mutation paths must call this, reconcile TCP listeners with the
     * returned snapshot, and publish only after TCP reconciliation succeeds.
     */
    SimulationRuleSnapshot compile();

    /**
     * Publishes a snapshot to HTTP/runtime matchers. Callers that mutate TCP rules
     * must reconcile {@code TcpSimulationServerManager} before invoking this method.
     */
    void publish(SimulationRuleSnapshot snapshot);

    /**
     * @deprecated Compatibility helper for legacy/non-TCP tests only. This compiles
     * and publishes without reconciling TCP listeners, so production mutation paths
     * must use {@link #compile()} followed by TCP reconciliation and {@link #publish(SimulationRuleSnapshot)}.
     */
    @Deprecated(forRemoval = false)
    SimulationRuleSnapshot refresh();
}
