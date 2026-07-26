package com.geek.websim.config;

import com.geek.websim.common.constants.CommonConstants;
import com.geek.websim.web.service.SimulationRuntimeReloader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class SimulationConfigHotReloadWatcher implements SmartLifecycle {
    private final SimulationProperties properties;
    private final SimulationRuntimeReloader runtimeReloader;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object reloadLock = new Object();

    private WatchService watchService;
    private java.util.concurrent.ExecutorService watchExecutor;
    private ScheduledExecutorService reloadExecutor;
    private ScheduledFuture<?> pendingReload;

    public SimulationConfigHotReloadWatcher(SimulationProperties properties,
                                            SimulationRuntimeReloader runtimeReloader) {
        this.properties = properties;
        this.runtimeReloader = runtimeReloader;
    }

    @Override
    public void start() {
        if (!properties.hotReloadEnabled() || !running.compareAndSet(false, true)) {
            return;
        }

        try {
            Path configDir = Path.of(properties.configDir());
            Files.createDirectories(configDir);
            watchService = configDir.getFileSystem().newWatchService();
            configDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.OVERFLOW);
            watchExecutor = Executors.newSingleThreadExecutor(task -> daemonThread(task, "web-sim-config-watch"));
            reloadExecutor = Executors.newSingleThreadScheduledExecutor(task -> daemonThread(task, "web-sim-config-reload"));
            watchExecutor.submit(this::watchLoop);
            log.info("已启用模拟配置热部署监听: {}", configDir.toAbsolutePath().normalize());
        } catch (IOException | RuntimeException e) {
            running.set(false);
            closeWatchService();
            shutdownExecutors();
            throw new IllegalStateException("模拟配置热部署监听启动失败", e);
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        synchronized (reloadLock) {
            if (pendingReload != null) {
                pendingReload.cancel(false);
                pendingReload = null;
            }
        }
        closeWatchService();
        shutdownExecutors();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    private void watchLoop() {
        while (running.get()) {
            try {
                WatchKey key = watchService.take();
                boolean shouldReload = key.pollEvents().stream().anyMatch(this::isConfigChange);
                if (!key.reset()) {
                    log.warn("模拟配置热部署监听已失效");
                    stop();
                    return;
                }
                if (shouldReload) {
                    onConfigChanged();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ClosedWatchServiceException e) {
                return;
            } catch (RuntimeException e) {
                log.warn("模拟配置热部署监听处理失败", e);
            }
        }
    }

    private boolean isConfigChange(WatchEvent<?> event) {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            return true;
        }
        Object context = event.context();
        return context instanceof Path path
                && path.getFileName().toString().endsWith(CommonConstants.CONFIG_FILE_EXTENSION);
    }

    void onConfigChanged() {
        if (!running.get() || reloadExecutor == null) {
            return;
        }
        scheduleReload();
    }

    private void scheduleReload() {
        synchronized (reloadLock) {
            if (pendingReload != null) {
                pendingReload.cancel(false);
            }
            pendingReload = reloadExecutor.schedule(
                    this::reloadSafely,
                    properties.hotReloadDebounceMs(),
                    TimeUnit.MILLISECONDS);
        }
    }

    private void reloadSafely() {
        try {
            runtimeReloader.reload();
            log.info("模拟配置热部署已应用");
        } catch (RuntimeException e) {
            log.warn("模拟配置热部署失败，继续使用上一份运行时快照", e);
        }
    }

    private void closeWatchService() {
        if (watchService == null) {
            return;
        }
        try {
            watchService.close();
        } catch (IOException e) {
            log.warn("模拟配置热部署监听关闭失败", e);
        } finally {
            watchService = null;
        }
    }

    private void shutdownExecutors() {
        if (watchExecutor != null) {
            watchExecutor.shutdownNow();
            watchExecutor = null;
        }
        if (reloadExecutor != null) {
            reloadExecutor.shutdownNow();
            reloadExecutor = null;
        }
    }

    private Thread daemonThread(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }
}
