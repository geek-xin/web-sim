package com.geek.websim.config;

import com.geek.websim.web.service.SimulationRuntimeReloader;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class WebSimRuntimeInitializer implements ApplicationRunner {
    private final SimulationRuntimeReloader runtimeReloader;

    public WebSimRuntimeInitializer(SimulationRuntimeReloader runtimeReloader) {
        this.runtimeReloader = runtimeReloader;
    }

    @Override
    public void run(ApplicationArguments args) {
        runtimeReloader.reload();
    }
}
