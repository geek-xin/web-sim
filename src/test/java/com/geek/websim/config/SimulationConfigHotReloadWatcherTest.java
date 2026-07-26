package com.geek.websim.config;

import com.geek.websim.web.service.SimulationRuntimeReloader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class SimulationConfigHotReloadWatcherTest {
    @TempDir
    Path tempDir;

    @Test
    void configChangesTriggerRuntimeReloadAfterDebounce() {
        SimulationRuntimeReloader runtimeReloader = mock(SimulationRuntimeReloader.class);
        SimulationConfigHotReloadWatcher watcher = new SimulationConfigHotReloadWatcher(
                properties(true, 50),
                runtimeReloader);

        watcher.start();
        try {
            watcher.onConfigChanged();

            verify(runtimeReloader, timeout(2_000)).reload();
        } finally {
            watcher.stop();
        }
    }

    @Test
    void disabledHotReloadDoesNotWatchConfigDirectory() throws Exception {
        SimulationRuntimeReloader runtimeReloader = mock(SimulationRuntimeReloader.class);
        SimulationConfigHotReloadWatcher watcher = new SimulationConfigHotReloadWatcher(
                properties(false, 50),
                runtimeReloader);

        watcher.start();
        try {
            Files.writeString(tempDir.resolve("ignored.json"), "{\"name\":\"ignored\"}");

            verify(runtimeReloader, never()).reload();
        } finally {
            watcher.stop();
        }
    }

    private SimulationProperties properties(boolean hotReloadEnabled, long debounceMs) {
        return new SimulationProperties(
                tempDir.toString(),
                1_048_576,
                1.0,
                200,
                hotReloadEnabled,
                debounceMs,
                new SimulationProperties.Tcp("127.0.0.1", 65_536));
    }
}
