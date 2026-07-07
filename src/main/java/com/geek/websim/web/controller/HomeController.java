package com.geek.websim.web.controller;

import com.geek.websim.config.SimulationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.file.Paths;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class HomeController {
    private final SimulationProperties properties;

    @GetMapping("/")
    public String root() {
        return "redirect:/admin";
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        model.addAttribute("simulationsConfigDir", Paths.get(properties.configDir()).toAbsolutePath().normalize().toString());
        return "index";
    }

    @GetMapping("/admin/api/info")
    @ResponseBody
    public Map<String, String> info() {
        return Map.of("name", "web-sim", "configDir", properties.configDir());
    }
}
