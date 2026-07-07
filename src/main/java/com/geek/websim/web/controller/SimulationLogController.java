package com.geek.websim.web.controller;

import com.geek.websim.common.result.Result;
import com.geek.websim.web.model.dto.SimulationLogSnapshot;
import com.geek.websim.web.service.SimulationMetricsService;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;

@RestController
@RequestMapping("/admin/api/logs")
public class SimulationLogController {
    private final SimulationMetricsService metricsService;

    public SimulationLogController(SimulationMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/snapshot")
    public Result<SimulationLogSnapshot> snapshot() {
        return Result.success(metricsService.snapshot());
    }

    @GetMapping("/stream")
    public Flux<ServerSentEvent<SimulationLogSnapshot>> stream() {
        return Flux.interval(Duration.ZERO, Duration.ofSeconds(2))
                .map(ignored -> ServerSentEvent.builder(metricsService.snapshot()).event("snapshot").build());
    }
}
