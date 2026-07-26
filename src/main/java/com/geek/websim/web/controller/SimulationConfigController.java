package com.geek.websim.web.controller;

import com.geek.websim.common.result.Result;
import com.geek.websim.web.model.dto.RawConfigResponse;
import com.geek.websim.web.model.dto.SimulationConfigDto;
import com.geek.websim.web.model.entity.SimulationConfig;
import com.geek.websim.web.service.SimulationConfigService;
import com.geek.websim.web.service.SimulationRuntimeReloader;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/api/simulations")
@Slf4j
public class SimulationConfigController {
    private final SimulationConfigService configService;
    private final SimulationRuntimeReloader runtimeReloader;

    public SimulationConfigController(SimulationConfigService configService,
                                      SimulationRuntimeReloader runtimeReloader) {
        this.configService = configService;
        this.runtimeReloader = runtimeReloader;
    }

    @GetMapping
    public Result<List<SimulationConfig>> list() {
        return Result.success(configService.listAll());
    }

    @GetMapping("/{id}")
    public Result<SimulationConfig> get(@PathVariable String id) {
        return Result.success(configService.getById(id));
    }

    @GetMapping("/{id}/raw")
    public Result<RawConfigResponse> raw(@PathVariable String id) {
        return Result.success(RawConfigResponse.builder()
                .fileName(id + ".json")
                .content(configService.rawJson(id))
                .build());
    }

    @PostMapping
    public Result<SimulationConfig> create(@Valid @RequestBody SimulationConfigDto dto) {
        SimulationConfig created = configService.create(dto.toEntity());
        try {
            refreshRuntimeServers();
        } catch (RuntimeException e) {
            rollbackAfterRefreshFailure(e, () -> configService.delete(created.getId()));
            throw e;
        }
        return Result.success(created);
    }

    @PutMapping("/{id}")
    public Result<SimulationConfig> update(@PathVariable String id, @Valid @RequestBody SimulationConfigDto dto) {
        SimulationConfig previous = configService.getById(id);
        SimulationConfig updated = configService.update(id, dto.toEntity());
        try {
            refreshRuntimeServers();
        } catch (RuntimeException e) {
            rollbackAfterRefreshFailure(e, () -> configService.restore(id, previous));
            throw e;
        }
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        SimulationConfig previous = configService.getById(id);
        configService.delete(id);
        try {
            refreshRuntimeServers();
        } catch (RuntimeException e) {
            rollbackAfterRefreshFailure(e, () -> configService.restore(id, previous));
            throw e;
        }
        return Result.success();
    }

    @PostMapping("/{id}/toggle")
    public Result<SimulationConfig> toggle(@PathVariable String id) {
        SimulationConfig previous = configService.getById(id);
        SimulationConfig toggled = copyForToggle(previous);
        toggled.setEnabled(!previous.isEnabled());
        SimulationConfig updated = configService.update(id, toggled);
        try {
            refreshRuntimeServers();
        } catch (RuntimeException e) {
            rollbackAfterRefreshFailure(e, () -> configService.restore(id, previous));
            throw e;
        }
        return Result.success(updated);
    }

    private void rollbackAfterRefreshFailure(RuntimeException original, Runnable rollback) {
        if (!tryRollback(original, rollback)) {
            return;
        }
        try {
            refreshRuntimeServers();
        } catch (RuntimeException rollbackRefreshFailure) {
            original.addSuppressed(rollbackRefreshFailure);
            log.warn("配置回滚后刷新运行时失败", rollbackRefreshFailure);
        }
    }

    private boolean tryRollback(RuntimeException original, Runnable rollback) {
        try {
            rollback.run();
            return true;
        } catch (RuntimeException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
            log.warn("配置刷新失败后的回滚操作失败", rollbackFailure);
            return false;
        }
    }

    private SimulationConfig copyForToggle(SimulationConfig source) {
        return SimulationConfig.builder()
                .id(source.getId())
                .name(source.getName())
                .protocol(source.getProtocol())
                .enabled(source.isEnabled())
                .http(source.getHttp())
                .tcp(source.getTcp())
                .requestTemplate(source.getRequestTemplate())
                .branches(source.getBranches())
                .defaultResponse(source.getDefaultResponse())
                .build();
    }

    private void refreshRuntimeServers() {
        runtimeReloader.reload();
    }
}
