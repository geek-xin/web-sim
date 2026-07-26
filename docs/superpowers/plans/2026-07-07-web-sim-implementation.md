# web-sim Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a full-stack HTTP/TCP simulator whose React admin console manages card-based simulation rules backed by local JSON files and a high-concurrency Reactor Netty runtime.

**Architecture:** The backend is a Spring Boot WebFlux application with a JSON-backed configuration service, an immutable in-memory runtime snapshot, shared HTTP/TCP matching logic, a template renderer, and sampled metrics/logging. The frontend is a React/Vite/Tailwind admin console modeled after `web-router`, with simulation cards, filters, dialogs, detail drawer, raw JSON preview, and rule branch editing.

**Tech Stack:** Java 21, Spring Boot 3.5.2, WebFlux, Reactor Netty, Jackson, Maven, JUnit 5, React 19, Vite 7, TypeScript, Tailwind CSS, Vitest, lucide-react, sonner, Radix UI primitives.

---

## File Structure

### Backend files to create

- `pom.xml` — Maven build, Java 21, Spring Boot WebFlux, Thymeleaf, validation, actuator, tests.
- `src/main/java/com/geek/websim/Application.java` — Spring Boot entry point.
- `src/main/resources/application.yml` — server, actuator, simulator, logging, body limit, metrics settings.
- `src/main/resources/templates/index.html` — admin shell that serves built React assets.
- `src/main/resources/static/admin/.gitkeep` — placeholder for frontend build output.
- `src/main/java/com/geek/websim/common/result/Result.java` — unified API response.
- `src/main/java/com/geek/websim/common/enums/ErrorCodeEnum.java` — business error codes.
- `src/main/java/com/geek/websim/common/exception/BusinessException.java` — typed business exception.
- `src/main/java/com/geek/websim/common/exception/GlobalExceptionHandler.java` — WebFlux exception mapping.
- `src/main/java/com/geek/websim/common/constants/CommonConstants.java` — config paths and reserved paths.
- `src/main/java/com/geek/websim/config/SimulationProperties.java` — typed configuration properties.
- `src/main/java/com/geek/websim/config/WebSimRuntimeInitializer.java` — boot-time runtime refresh and TCP startup.
- `src/main/java/com/geek/websim/web/controller/HomeController.java` — `/` redirect and `/admin` page.
- `src/main/java/com/geek/websim/web/controller/SimulationConfigController.java` — simulation CRUD API.
- `src/main/java/com/geek/websim/web/controller/SimulationLogController.java` — metrics snapshot and SSE logs.
- `src/main/java/com/geek/websim/web/controller/HttpSimulationController.java` — catch-all HTTP simulation endpoint.
- `src/main/java/com/geek/websim/web/model/entity/SimulationConfig.java` — persisted simulation rule.
- `src/main/java/com/geek/websim/web/model/entity/HttpRule.java` — HTTP matching config.
- `src/main/java/com/geek/websim/web/model/entity/TcpRule.java` — TCP binding and frame config.
- `src/main/java/com/geek/websim/web/model/entity/RequestTemplate.java` — editable request parameter hints.
- `src/main/java/com/geek/websim/web/model/entity/SimulationBranch.java` — branch priority, conditions, response.
- `src/main/java/com/geek/websim/web/model/entity/SimulationCondition.java` — branch condition model.
- `src/main/java/com/geek/websim/web/model/entity/SimulationResponse.java` — status, headers, body, delay.
- `src/main/java/com/geek/websim/web/model/enums/ProtocolType.java` — `HTTP` and `TCP`.
- `src/main/java/com/geek/websim/web/model/enums/HttpMatchMode.java` — `EXACT`, `PREFIX`, `TEMPLATE`.
- `src/main/java/com/geek/websim/web/model/enums/TcpFrameMode.java` — `LINE`, `LENGTH_HEADER`, `HEX`.
- `src/main/java/com/geek/websim/web/model/enums/ConditionSource.java` — `QUERY`, `HEADER`, `PATH`, `BODY`, `TCP_BODY`.
- `src/main/java/com/geek/websim/web/model/enums/ConditionOperator.java` — `EQ`, `NOT_EQ`, `CONTAINS`, `REGEX`, `EXISTS`, `JSON_PATH`.
- `src/main/java/com/geek/websim/web/model/dto/SimulationConfigDto.java` — validation DTO for create/update.
- `src/main/java/com/geek/websim/web/model/dto/RawConfigResponse.java` — raw JSON API response.
- `src/main/java/com/geek/websim/web/model/dto/SimulationLogEntry.java` — sampled request log item.
- `src/main/java/com/geek/websim/web/model/dto/SimulationLogSnapshot.java` — metrics snapshot.
- `src/main/java/com/geek/websim/web/service/SimulationConfigService.java` — config CRUD contract.
- `src/main/java/com/geek/websim/web/service/SimulationRuntimeService.java` — runtime snapshot refresh contract.
- `src/main/java/com/geek/websim/web/service/SimulationMetricsService.java` — counters and sampled logs contract.
- `src/main/java/com/geek/websim/web/service/impl/SimulationConfigServiceImpl.java` — JSON-backed config service.
- `src/main/java/com/geek/websim/web/service/impl/SimulationRuntimeServiceImpl.java` — snapshot compiler and atomic swap.
- `src/main/java/com/geek/websim/web/service/impl/InMemorySimulationMetricsService.java` — low-overhead metrics implementation.
- `src/main/java/com/geek/websim/runtime/CompiledSimulationRule.java` — compiled rule with pre-parsed path/conditions/templates.
- `src/main/java/com/geek/websim/runtime/CompiledResponseTemplate.java` — compiled response template segments.
- `src/main/java/com/geek/websim/runtime/SimulationRuleSnapshot.java` — immutable HTTP/TCP rule indexes.
- `src/main/java/com/geek/websim/runtime/SimulationRequest.java` — normalized HTTP/TCP request context.
- `src/main/java/com/geek/websim/runtime/SimulationMatchResult.java` — matched config, branch, variables, response.
- `src/main/java/com/geek/websim/runtime/SimulationMatcher.java` — protocol and branch matcher.
- `src/main/java/com/geek/websim/runtime/BranchMatcher.java` — condition evaluation.
- `src/main/java/com/geek/websim/runtime/ResponseRenderer.java` — template and delay rendering.
- `src/main/java/com/geek/websim/runtime/RandomValueProvider.java` — random template functions.
- `src/main/java/com/geek/websim/runtime/http/PathTemplate.java` — HTTP template path parser.
- `src/main/java/com/geek/websim/runtime/http/HttpSimulationHandler.java` — HTTP request adaptation and response creation.
- `src/main/java/com/geek/websim/runtime/tcp/TcpSimulationServerManager.java` — Reactor Netty TCP server lifecycle.

### Backend tests to create

- `src/test/java/com/geek/websim/web/service/SimulationConfigServiceImplTest.java`
- `src/test/java/com/geek/websim/runtime/http/PathTemplateTest.java`
- `src/test/java/com/geek/websim/runtime/BranchMatcherTest.java`
- `src/test/java/com/geek/websim/runtime/ResponseRendererTest.java`
- `src/test/java/com/geek/websim/runtime/SimulationMatcherTest.java`
- `src/test/java/com/geek/websim/web/controller/HttpSimulationControllerTest.java`
- `src/test/java/com/geek/websim/runtime/tcp/TcpSimulationServerManagerTest.java`

### Frontend files to create

- `frontend/package.json` — Vite/React scripts and dependencies.
- `frontend/tsconfig.json`, `frontend/tsconfig.node.json`, `frontend/vite.config.ts`, `frontend/postcss.config.js`, `frontend/tailwind.config.ts`, `frontend/index.html` — frontend toolchain.
- `frontend/src/main.tsx`, `frontend/src/App.tsx`, `frontend/src/styles.css` — app shell.
- `frontend/src/lib/api.ts`, `frontend/src/lib/utils.ts` — request helper and class merge helper.
- `frontend/src/components/ui/button.tsx`, `card.tsx`, `badge.tsx`, `input.tsx`, `textarea.tsx`, `checkbox.tsx`, `dialog.tsx`, `alert-dialog.tsx`, `tabs.tsx`, `table.tsx` — shadcn-style UI primitives adapted from `web-router`.
- `frontend/src/features/simulations/types.ts` — frontend simulation types.
- `frontend/src/features/simulations/sim-utils.ts` — display helpers, validation helpers, payload conversion.
- `frontend/src/features/simulations/SimulationToolbar.tsx` — search/filter/create toolbar.
- `frontend/src/features/simulations/SimulationCard.tsx` — card representation for one simulation.
- `frontend/src/features/simulations/SimulationFormDialog.tsx` — create/edit/copy form.
- `frontend/src/features/simulations/SimulationDetailDrawer.tsx` — rich detail editor and raw JSON preview.
- `frontend/src/features/simulations/DeleteConfirmDialog.tsx` — delete confirmation.
- `frontend/src/features/logs/types.ts`, `frontend/src/features/logs/SimulationLogDialog.tsx` — sampled logs.
- `frontend/src/features/simulations/sim-utils.test.ts`, `frontend/src/lib/api.test.ts` — Vitest coverage.

### Docs and scripts to create

- `README.md` — replace stub with architecture, quickstart, HTTP/TCP examples, performance notes.
- `scripts/build-dist.sh` — optional distribution build modeled after `web-router`.
- `config/simulations/.gitkeep` — local JSON config directory placeholder.

---

## Task 1: Scaffold Spring Boot backend

**Files:**
- Create: `/Users/xin/Documents/CodexProjects/web-sim/pom.xml`
- Create: `/Users/xin/Documents/CodexProjects/web-sim/src/main/java/com/geek/websim/Application.java`
- Create: `/Users/xin/Documents/CodexProjects/web-sim/src/main/resources/application.yml`
- Create: `/Users/xin/Documents/CodexProjects/web-sim/src/main/resources/templates/index.html`
- Create: `/Users/xin/Documents/CodexProjects/web-sim/src/main/resources/static/admin/.gitkeep`
- Create: `/Users/xin/Documents/CodexProjects/web-sim/config/simulations/.gitkeep`

- [ ] **Step 1: Create Maven build file**

Write `/Users/xin/Documents/CodexProjects/web-sim/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.2</version>
        <relativePath/>
    </parent>

    <groupId>com.geek</groupId>
    <artifactId>web-sim</artifactId>
    <version>0.1.0</version>
    <packaging>jar</packaging>
    <name>web-sim</name>
    <description>High-concurrency HTTP/TCP simulator with a React admin console</description>

    <properties>
        <java.version>21</java.version>
        <lombok.version>1.18.38</lombok.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                    <compilerArgs>
                        <arg>-parameters</arg>
                    </compilerArgs>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create Spring Boot entry point**

Write `/Users/xin/Documents/CodexProjects/web-sim/src/main/java/com/geek/websim/Application.java`:

```java
package com.geek.websim;

import com.geek.websim.config.SimulationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SimulationProperties.class)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

- [ ] **Step 3: Create application configuration**

Write `/Users/xin/Documents/CodexProjects/web-sim/src/main/resources/application.yml`:

```yaml
server:
  address: 127.0.0.1
  port: 9998

spring:
  application:
    name: web-sim
  main:
    web-application-type: reactive
  thymeleaf:
    cache: false

management:
  endpoints:
    web:
      exposure:
        include: health,info

web-sim:
  config-dir: config/simulations
  max-body-bytes: 1048576
  log-sample-rate: 0.01
  recent-log-size: 200
  tcp:
    default-host: 127.0.0.1
    max-frame-bytes: 65536
```

- [ ] **Step 4: Create admin HTML shell**

Write `/Users/xin/Documents/CodexProjects/web-sim/src/main/resources/templates/index.html`:

```html
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>web-sim 管理台</title>
  <script th:inline="javascript">
    window.__WEB_SIM_CONFIG_DIR__ = [[${simulationsConfigDir}]];
  </script>
  <script type="module" src="/admin/assets/index.js"></script>
  <link rel="stylesheet" href="/admin/assets/index.css" />
</head>
<body>
  <div id="root"></div>
</body>
</html>
```

- [ ] **Step 5: Create placeholder directories**

Run:

```bash
mkdir -p src/main/resources/static/admin config/simulations
: > src/main/resources/static/admin/.gitkeep
: > config/simulations/.gitkeep
```

Expected: directories exist and are tracked by `.gitkeep` files.

- [ ] **Step 6: Verify Maven project resolves**

Run:

```bash
mvn -q -DskipTests compile
```

Expected before later tasks: compilation may fail because `SimulationProperties` is referenced but not created. That is acceptable for this step; Task 2 creates it and makes compile pass.

- [ ] **Step 7: Commit backend scaffold**

```bash
git add pom.xml src/main/java/com/geek/websim/Application.java src/main/resources/application.yml src/main/resources/templates/index.html src/main/resources/static/admin/.gitkeep config/simulations/.gitkeep
git commit -m "build: scaffold web-sim backend"
```

---

## Task 2: Add common API, configuration properties, and home controller

**Files:**
- Create: `/Users/xin/Documents/CodexProjects/web-sim/src/main/java/com/geek/websim/common/constants/CommonConstants.java`
- Create: `/Users/xin/Documents/CodexProjects/web-sim/src/main/java/com/geek/websim/common/result/Result.java`
- Create: `/Users/xin/Documents/CodexProjects/web-sim/src/main/java/com/geek/websim/common/enums/ErrorCodeEnum.java`
- Create: `/Users/xin/Documents/CodexProjects/web-sim/src/main/java/com/geek/websim/common/exception/BusinessException.java`
- Create: `/Users/xin/Documents/CodexProjects/web-sim/src/main/java/com/geek/websim/common/exception/GlobalExceptionHandler.java`
- Create: `/Users/xin/Documents/CodexProjects/web-sim/src/main/java/com/geek/websim/config/SimulationProperties.java`
- Create: `/Users/xin/Documents/CodexProjects/web-sim/src/main/java/com/geek/websim/web/controller/HomeController.java`

- [ ] **Step 1: Add common constants**

Write `CommonConstants.java`:

```java
package com.geek.websim.common.constants;

import java.util.List;

public final class CommonConstants {
    public static final String CONFIG_FILE_EXTENSION = ".json";
    public static final List<String> RESERVED_PATH_PREFIXES = List.of("/admin", "/actuator", "/assets", "/favicon.ico");

    private CommonConstants() {
    }
}
```

- [ ] **Step 2: Add unified result type**

Write `Result.java`:

```java
package com.geek.websim.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private boolean success;
    private String code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(true, "0", "success", data);
    }

    public static Result<Void> success() {
        return new Result<>(true, "0", "success", null);
    }

    public static <T> Result<T> failure(String code, String message) {
        return new Result<>(false, code, message, null);
    }
}
```

- [ ] **Step 3: Add error codes and business exception**

Write `ErrorCodeEnum.java`:

```java
package com.geek.websim.common.enums;

import lombok.Getter;

@Getter
public enum ErrorCodeEnum {
    BAD_REQUEST("400", "请求参数不正确"),
    NOT_FOUND("404", "资源不存在"),
    DUPLICATE_NAME("409_NAME", "名称已存在"),
    DUPLICATE_BINDING("409_BINDING", "监听地址已被占用"),
    CONFIG_IO_ERROR("CONFIG_IO_ERROR", "配置文件读写失败"),
    RUNTIME_REFRESH_ERROR("RUNTIME_REFRESH_ERROR", "运行时刷新失败"),
    INTERNAL_ERROR("500", "系统异常");

    private final String code;
    private final String message;

    ErrorCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
```

Write `BusinessException.java`:

```java
package com.geek.websim.common.exception;

import com.geek.websim.common.enums.ErrorCodeEnum;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCodeEnum errorCode;

    public BusinessException(ErrorCodeEnum errorCode, String message) {
        super(message == null || message.isBlank() ? errorCode.getMessage() : message);
        this.errorCode = errorCode;
    }
}
```

- [ ] **Step 4: Add global exception handler**

Write `GlobalExceptionHandler.java`:

```java
package com.geek.websim.common.exception;

import com.geek.websim.common.enums.ErrorCodeEnum;
import com.geek.websim.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    @ResponseBody
    public Result<Void> handleBusiness(BusinessException ex) {
        return Result.failure(ex.getErrorCode().getCode(), ex.getMessage());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseBody
    public Result<Void> handleValidation(WebExchangeBindException ex) {
        String message = ex.getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return Result.failure(ErrorCodeEnum.BAD_REQUEST.getCode(), message);
    }

    @ExceptionHandler(ServerWebInputException.class)
    @ResponseBody
    public Result<Void> handleInput(ServerWebInputException ex) {
        return Result.failure(ErrorCodeEnum.BAD_REQUEST.getCode(), ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Result<Void> handleUnknown(Exception ex) {
        log.error("Unhandled error", ex);
        return Result.failure(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), ErrorCodeEnum.INTERNAL_ERROR.getMessage());
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
```

- [ ] **Step 5: Add typed properties**

Write `SimulationProperties.java`:

```java
package com.geek.websim.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "web-sim")
public record SimulationProperties(
        String configDir,
        int maxBodyBytes,
        double logSampleRate,
        int recentLogSize,
        Tcp tcp
) {
    public SimulationProperties {
        if (configDir == null || configDir.isBlank()) {
            configDir = "config/simulations";
        }
        if (maxBodyBytes <= 0) {
            maxBodyBytes = 1_048_576;
        }
        if (logSampleRate < 0 || logSampleRate > 1) {
            logSampleRate = 0.01;
        }
        if (recentLogSize <= 0) {
            recentLogSize = 200;
        }
        if (tcp == null) {
            tcp = new Tcp("127.0.0.1", 65_536);
        }
    }

    public record Tcp(String defaultHost, int maxFrameBytes) {
        public Tcp {
            if (defaultHost == null || defaultHost.isBlank()) {
                defaultHost = "127.0.0.1";
            }
            if (maxFrameBytes <= 0) {
                maxFrameBytes = 65_536;
            }
        }
    }
}
```

- [ ] **Step 6: Add home controller**

Write `HomeController.java`:

```java
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
```

- [ ] **Step 7: Verify compile passes**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: build succeeds.

- [ ] **Step 8: Commit common backend foundation**

```bash
git add src/main/java/com/geek/websim/common src/main/java/com/geek/websim/config src/main/java/com/geek/websim/web/controller/HomeController.java
git commit -m "feat: add web-sim backend foundation"
```

---

## Task 3: Add simulation domain model and JSON config service

**Files:**
- Create all model/enums/dto/service files listed in File Structure for domain and config service.
- Test: `/Users/xin/Documents/CodexProjects/web-sim/src/test/java/com/geek/websim/web/service/SimulationConfigServiceImplTest.java`

- [ ] **Step 1: Write failing config service tests**

Write `SimulationConfigServiceImplTest.java` with these test cases:

```java
package com.geek.websim.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geek.websim.common.exception.BusinessException;
import com.geek.websim.web.model.entity.*;
import com.geek.websim.web.model.enums.*;
import com.geek.websim.web.service.impl.SimulationConfigServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationConfigServiceImplTest {
    @TempDir Path tempDir;

    @Test
    void createPersistsSimulationWithGeneratedId() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();

        SimulationConfig saved = service.create(httpConfig("用户查询", "/api/users/{id}"));

        assertThat(saved.getId()).startsWith("sim-");
        assertThat(service.listAll()).extracting(SimulationConfig::getName).containsExactly("用户查询");
        assertThat(tempDir.resolve(saved.getId() + ".json")).exists();
    }

    @Test
    void updateRejectsDuplicateName() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();
        SimulationConfig one = service.create(httpConfig("用户查询", "/api/users/{id}"));
        service.create(httpConfig("订单查询", "/api/orders/{id}"));

        SimulationConfig renamed = httpConfig("订单查询", "/api/users/{id}");
        assertThatThrownBy(() -> service.update(one.getId(), renamed)).isInstanceOf(BusinessException.class);
    }

    @Test
    void createRejectsDuplicateTcpBinding() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();
        service.create(tcpConfig("TCP A", 19001));

        assertThatThrownBy(() -> service.create(tcpConfig("TCP B", 19001))).isInstanceOf(BusinessException.class);
    }

    private SimulationConfig httpConfig(String name, String path) {
        return SimulationConfig.builder()
                .name(name)
                .protocol(ProtocolType.HTTP)
                .enabled(true)
                .http(HttpRule.builder().method("GET").path(path).matchMode(HttpMatchMode.TEMPLATE).build())
                .branches(List.of())
                .defaultResponse(SimulationResponse.builder().status(404).body("not matched").build())
                .build();
    }

    private SimulationConfig tcpConfig(String name, int port) {
        return SimulationConfig.builder()
                .name(name)
                .protocol(ProtocolType.TCP)
                .enabled(true)
                .tcp(TcpRule.builder().host("127.0.0.1").port(port).frameMode(TcpFrameMode.LINE).build())
                .branches(List.of())
                .defaultResponse(SimulationResponse.builder().status(200).body("OK\\n").build())
                .build();
    }
}
```

- [ ] **Step 2: Run config service test and verify it fails**

Run:

```bash
mvn -q -Dtest=SimulationConfigServiceImplTest test
```

Expected: FAIL because model and service classes do not exist.

- [ ] **Step 3: Create enums**

Create enum files with exact values:

```java
// ProtocolType.java
package com.geek.websim.web.model.enums;
public enum ProtocolType { HTTP, TCP }
```

```java
// HttpMatchMode.java
package com.geek.websim.web.model.enums;
public enum HttpMatchMode { EXACT, PREFIX, TEMPLATE }
```

```java
// TcpFrameMode.java
package com.geek.websim.web.model.enums;
public enum TcpFrameMode { LINE, LENGTH_HEADER, HEX }
```

```java
// ConditionSource.java
package com.geek.websim.web.model.enums;
public enum ConditionSource { QUERY, HEADER, PATH, BODY, TCP_BODY }
```

```java
// ConditionOperator.java
package com.geek.websim.web.model.enums;
public enum ConditionOperator { EQ, NOT_EQ, CONTAINS, REGEX, EXISTS, JSON_PATH }
```

- [ ] **Step 4: Create entity classes with Lombok builders**

Each entity uses `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`.

`SimulationConfig` fields:

```java
private String id;
@NotBlank(message = "模拟名称不能为空")
private String name;
@NotNull(message = "协议不能为空")
private ProtocolType protocol;
private boolean enabled;
private HttpRule http;
private TcpRule tcp;
@Builder.Default
private RequestTemplate requestTemplate = new RequestTemplate();
@Builder.Default
private List<SimulationBranch> branches = new ArrayList<>();
@NotNull(message = "默认响应不能为空")
private SimulationResponse defaultResponse;
```

`HttpRule` fields:

```java
private String method;
private String path;
private HttpMatchMode matchMode;
```

`TcpRule` fields:

```java
private String host;
private Integer port;
private TcpFrameMode frameMode;
```

`RequestTemplate` fields:

```java
@Builder.Default private Map<String, String> headers = new LinkedHashMap<>();
@Builder.Default private Map<String, String> query = new LinkedHashMap<>();
private String body;
```

`SimulationBranch` fields:

```java
private String name;
private int priority;
@Builder.Default private List<SimulationCondition> conditions = new ArrayList<>();
private SimulationResponse response;
```

`SimulationCondition` fields:

```java
private ConditionSource source;
private String key;
private ConditionOperator operator;
private String value;
```

`SimulationResponse` fields:

```java
private Integer status;
@Builder.Default private Map<String, String> headers = new LinkedHashMap<>();
private String body;
private Long delayMs;
```

- [ ] **Step 5: Create service interface and implementation**

`SimulationConfigService` methods:

```java
void initDefaultConfigs();
List<SimulationConfig> listAll();
SimulationConfig getById(String id);
SimulationConfig create(SimulationConfig config);
SimulationConfig update(String id, SimulationConfig config);
void delete(String id);
String rawJson(String id);
```

`SimulationConfigServiceImpl` behavior:

- Constructor accepts `ObjectMapper` and `SimulationProperties`; package-private constructor accepts `ObjectMapper` and `Path` for tests.
- `initDefaultConfigs()` creates config directory.
- `create()` validates name/protocol/protocol config/default response, assigns `sim-yyyyMMddHHmmss-xxxxxx`, checks duplicate name and TCP host:port among enabled TCP configs, writes pretty JSON.
- `update()` preserves id, validates, checks conflicts excluding itself, writes JSON.
- `delete()` removes `id.json` or throws `BusinessException(NOT_FOUND)`.
- `listAll()` reads `*.json`, sorts by modified time descending.
- `rawJson()` returns file content.

- [ ] **Step 6: Run config service test and verify it passes**

Run:

```bash
mvn -q -Dtest=SimulationConfigServiceImplTest test
```

Expected: PASS.

- [ ] **Step 7: Commit domain and config service**

```bash
git add src/main/java/com/geek/websim/web/model src/main/java/com/geek/websim/web/service src/test/java/com/geek/websim/web/service/SimulationConfigServiceImplTest.java
git commit -m "feat: add simulation config service"
```

---

## Task 4: Add path templates, branch matching, and response rendering

**Files:**
- Create runtime files listed for `PathTemplate`, `BranchMatcher`, `ResponseRenderer`, `RandomValueProvider`, `SimulationRequest`, `SimulationMatchResult`, `CompiledResponseTemplate`.
- Tests: `PathTemplateTest.java`, `BranchMatcherTest.java`, `ResponseRendererTest.java`.

- [ ] **Step 1: Write failing path template tests**

`PathTemplateTest` must assert:

```java
PathTemplate template = PathTemplate.compile("/api/users/{id}/orders/{orderId}");
assertThat(template.match("/api/users/u1/orders/o9")).containsEntry("id", "u1").containsEntry("orderId", "o9");
assertThat(template.match("/api/users/u1/orders")).isEmpty();
```

- [ ] **Step 2: Implement `PathTemplate`**

`PathTemplate.compile(String pattern)` splits by `/`, records literal and variable segments, rejects blank or non-leading-slash patterns, and `match(String path)` returns `Map<String,String>` when all segment counts and literals match.

- [ ] **Step 3: Write failing branch matcher tests**

`BranchMatcherTest` must cover:

```java
// EQ query condition matches
// CONTAINS tcp body condition matches
// REGEX body condition matches
// EXISTS header condition matches when present
// NOT_EQ fails when value is equal
```

Use `SimulationRequest.builder()` with maps for query/header/path and body/tcpBody strings.

- [ ] **Step 4: Implement `SimulationRequest` and `BranchMatcher`**

`SimulationRequest` fields:

```java
private ProtocolType protocol;
private String method;
private String path;
@Builder.Default private Map<String, String> query = Map.of();
@Builder.Default private Map<String, String> headers = Map.of();
@Builder.Default private Map<String, String> pathVariables = Map.of();
private String body;
private String tcpBody;
```

`BranchMatcher.matches(List<SimulationCondition>, SimulationRequest)` returns true when all conditions pass. For `JSON_PATH`, first version supports only top-level JSON keys using Jackson `ObjectMapper.readTree(body).path(key).asText()`.

- [ ] **Step 5: Write failing response renderer tests**

`ResponseRendererTest` must verify:

```java
SimulationRequest request = SimulationRequest.builder()
    .pathVariables(Map.of("id", "u1"))
    .query(Map.of("trace", "t1"))
    .headers(Map.of("X-Trace-Id", "h1"))
    .tcpBody("PING")
    .build();
SimulationResponse response = SimulationResponse.builder()
    .status(200)
    .body("{\"id\":\"{{path.id}}\",\"trace\":\"{{query.trace}}\",\"uuid\":\"{{random.uuid}}\"}")
    .build();
String rendered = new ResponseRenderer(new RandomValueProvider()).renderBody(response, request);
assertThat(rendered).contains("\"id\":\"u1\"").contains("\"trace\":\"t1\"");
assertThat(rendered).doesNotContain("{{random.uuid}}");
```

- [ ] **Step 6: Implement random value provider and renderer**

`RandomValueProvider.resolve(String expression)` supports:

- `random.uuid`
- `random.int:min,max`
- `random.float:min,max`
- `random.bool`
- `random.timestamp`
- `random.pick:A,B,C`
- `random.name` using a small built-in list: `张三`, `李四`, `Alice`, `Bob`.

`ResponseRenderer` replaces variables using regex `\{\{([^}]+)}}` and leaves unknown variables as empty strings.

- [ ] **Step 7: Run runtime unit tests**

```bash
mvn -q -Dtest=PathTemplateTest,BranchMatcherTest,ResponseRendererTest test
```

Expected: PASS.

- [ ] **Step 8: Commit matcher and renderer**

```bash
git add src/main/java/com/geek/websim/runtime src/test/java/com/geek/websim/runtime
git commit -m "feat: add simulation matcher primitives"
```

---

## Task 5: Add runtime snapshot compiler and simulation matcher

**Files:**
- Create: runtime `CompiledSimulationRule`, `SimulationRuleSnapshot`, `SimulationMatcher`, `SimulationMatchResult`.
- Create: service `SimulationRuntimeService`, `SimulationRuntimeServiceImpl`.
- Test: `SimulationMatcherTest.java`.

- [ ] **Step 1: Write failing matcher tests**

`SimulationMatcherTest` must verify:

- HTTP TEMPLATE `/api/users/{id}` matches `/api/users/u1` and extracts `id`.
- HTTP EXACT does not match a longer path.
- HTTP PREFIX matches nested path.
- Branch with lower priority is selected first.
- Disabled configs are not in the snapshot.
- TCP rule matches by port and body condition.

- [ ] **Step 2: Implement compiled rule and snapshot**

`CompiledSimulationRule` stores:

```java
private final SimulationConfig config;
private final PathTemplate pathTemplate;
private final List<SimulationBranch> sortedBranches;
```

`SimulationRuleSnapshot` stores:

```java
private final List<CompiledSimulationRule> httpRules;
private final Map<Integer, List<CompiledSimulationRule>> tcpRulesByPort;
```

Factory method `from(List<SimulationConfig>)` filters disabled configs, compiles HTTP template rules, sorts branches by priority, groups TCP by port.

- [ ] **Step 3: Implement simulation matcher**

`SimulationMatcher.matchHttp(SimulationRequest)` loops over `snapshot.httpRules()` and tests method/path/match mode. `SimulationMatcher.matchTcp(int port, SimulationRequest)` loops over TCP rules for the port. Both use `BranchMatcher` to select a branch, or use `defaultResponse` if the rule-level match succeeds and no branch conditions match.

- [ ] **Step 4: Implement runtime service**

`SimulationRuntimeServiceImpl` holds `AtomicReference<SimulationRuleSnapshot>`. Methods:

```java
SimulationRuleSnapshot current();
SimulationRuleSnapshot refresh();
```

`refresh()` calls `configService.listAll()`, compiles snapshot, swaps reference, and returns it.

- [ ] **Step 5: Run matcher tests**

```bash
mvn -q -Dtest=SimulationMatcherTest test
```

Expected: PASS.

- [ ] **Step 6: Commit runtime snapshot**

```bash
git add src/main/java/com/geek/websim/runtime src/main/java/com/geek/websim/web/service/SimulationRuntimeService.java src/main/java/com/geek/websim/web/service/impl/SimulationRuntimeServiceImpl.java src/test/java/com/geek/websim/runtime/SimulationMatcherTest.java
git commit -m "feat: add simulation runtime snapshot"
```

---

## Task 6: Add HTTP simulation API and config CRUD controller

**Files:**
- Create: `SimulationConfigController.java`, `HttpSimulationController.java`, DTOs, `HttpSimulationHandler.java`.
- Test: `HttpSimulationControllerTest.java`.

- [ ] **Step 1: Write failing HTTP integration tests**

Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` and `WebTestClient`. Test flow:

1. POST `/admin/api/simulations` with HTTP config for `GET /api/users/{id}`.
2. GET `/api/users/u1?status=ok` returns 200 and body contains `u1`.
3. GET `/api/users/u1?status=fail` returns 500.
4. GET `/api/unknown` returns 404.
5. PUT disables config; subsequent GET returns 404.

- [ ] **Step 2: Implement CRUD controller**

`SimulationConfigController` endpoints:

```java
GET /admin/api/simulations
GET /admin/api/simulations/{id}
GET /admin/api/simulations/{id}/raw
POST /admin/api/simulations
PUT /admin/api/simulations/{id}
DELETE /admin/api/simulations/{id}
POST /admin/api/simulations/{id}/toggle
```

After create/update/delete/toggle, call `runtimeService.refresh()`.

- [ ] **Step 3: Implement HTTP handler**

`HttpSimulationHandler.handle(ServerWebExchange exchange)`:

- Reject reserved path prefixes using `CommonConstants.RESERVED_PATH_PREFIXES`.
- Read request body up to `SimulationProperties.maxBodyBytes()`.
- Build `SimulationRequest` with method, path, query params, headers, and body.
- Call `runtimeService.current()` and `SimulationMatcher.matchHttp()`.
- If no match, return HTTP 404 with `{"error":"未匹配模拟规则"}`.
- If match, delay using `Mono.delay` when `delayMs > 0`.
- Render headers/body/status with `ResponseRenderer`.

- [ ] **Step 4: Implement catch-all controller**

`HttpSimulationController` maps:

```java
@RequestMapping(path = "/{*path}")
public Mono<Void> simulate(ServerWebExchange exchange)
```

It delegates to `HttpSimulationHandler`.

- [ ] **Step 5: Run HTTP integration tests**

```bash
mvn -q -Dtest=HttpSimulationControllerTest test
```

Expected: PASS.

- [ ] **Step 6: Commit HTTP simulation**

```bash
git add src/main/java/com/geek/websim/web/controller src/main/java/com/geek/websim/runtime/http src/main/java/com/geek/websim/web/model/dto src/test/java/com/geek/websim/web/controller/HttpSimulationControllerTest.java
git commit -m "feat: add HTTP simulation runtime"
```

---

## Task 7: Add TCP line-based simulator

**Files:**
- Create: `TcpSimulationServerManager.java`
- Modify: `SimulationRuntimeServiceImpl.java`
- Create: `WebSimRuntimeInitializer.java`
- Test: `TcpSimulationServerManagerTest.java`

- [ ] **Step 1: Write failing TCP test**

Test should:

- Create a TCP simulation config on a random available port.
- Refresh runtime and start TCP manager.
- Connect with Reactor Netty `TcpClient`.
- Send `PING ok\n`.
- Assert response contains configured `PONG` body.
- Stop manager in `@AfterEach`.

- [ ] **Step 2: Implement TCP manager**

`TcpSimulationServerManager` holds `Map<Integer, DisposableServer> servers`. On `refreshServers(SimulationRuleSnapshot snapshot)`:

- Compute enabled TCP ports from snapshot.
- Stop servers for removed ports.
- Start missing servers with `TcpServer.create().host(host).port(port)`.
- Use `in.receive().asString().bufferUntil(line -> line.endsWith("\n"))` style line handling, or Netty line decoder if preferred.
- For each frame, build `SimulationRequest` with `ProtocolType.TCP` and `tcpBody`.
- Match with `SimulationMatcher.matchTcp(port, request)`.
- Render body and write it back.

- [ ] **Step 3: Wire boot initializer**

`WebSimRuntimeInitializer` implements `ApplicationRunner`:

```java
@Override
public void run(ApplicationArguments args) {
    SimulationRuleSnapshot snapshot = runtimeService.refresh();
    tcpSimulationServerManager.refreshServers(snapshot);
}
```

After config changes in `SimulationConfigController`, call both `runtimeService.refresh()` and `tcpSimulationServerManager.refreshServers(snapshot)`.

- [ ] **Step 4: Run TCP test**

```bash
mvn -q -Dtest=TcpSimulationServerManagerTest test
```

Expected: PASS.

- [ ] **Step 5: Commit TCP simulator**

```bash
git add src/main/java/com/geek/websim/runtime/tcp src/main/java/com/geek/websim/config/WebSimRuntimeInitializer.java src/main/java/com/geek/websim/web/service/impl/SimulationRuntimeServiceImpl.java src/test/java/com/geek/websim/runtime/tcp/TcpSimulationServerManagerTest.java
git commit -m "feat: add TCP line simulator"
```

---

## Task 8: Add lightweight metrics and sampled logs

**Files:**
- Create: `SimulationMetricsService.java`, `InMemorySimulationMetricsService.java`, `SimulationLogEntry.java`, `SimulationLogSnapshot.java`, `SimulationLogController.java`.
- Modify: `HttpSimulationHandler.java`, `TcpSimulationServerManager.java`.

- [ ] **Step 1: Write metrics service unit test**

Test records one HTTP success, one HTTP error, one TCP success, then asserts snapshot counters and recent logs size.

- [ ] **Step 2: Implement metrics DTOs and service**

`SimulationLogEntry` fields:

```java
private String id;
private String simulationId;
private String simulationName;
private ProtocolType protocol;
private int status;
private long durationMs;
private String requestSummary;
private String responseSummary;
private Instant timestamp;
```

`SimulationLogSnapshot` fields:

```java
private long totalRequests;
private long httpRequests;
private long tcpRequests;
private long errorRequests;
private double averageDurationMs;
private List<SimulationLogEntry> recentLogs;
```

Use `LongAdder` counters, bounded `ArrayDeque` protected by a small synchronized block, and sample using `ThreadLocalRandom.current().nextDouble() < logSampleRate`.

- [ ] **Step 3: Wire metrics into HTTP/TCP**

Record start time before match; record status and duration after response rendering. For no-match HTTP use status 404 and simulation id `unmatched`.

- [ ] **Step 4: Add logs controller**

Endpoints:

```java
GET /admin/api/logs/snapshot
GET /admin/api/logs/stream
```

SSE emits a snapshot every two seconds using `Flux.interval`.

- [ ] **Step 5: Run backend tests**

```bash
mvn test
```

Expected: PASS.

- [ ] **Step 6: Commit metrics and logs**

```bash
git add src/main/java/com/geek/websim/web/service/SimulationMetricsService.java src/main/java/com/geek/websim/web/service/impl/InMemorySimulationMetricsService.java src/main/java/com/geek/websim/web/model/dto/SimulationLogEntry.java src/main/java/com/geek/websim/web/model/dto/SimulationLogSnapshot.java src/main/java/com/geek/websim/web/controller/SimulationLogController.java src/main/java/com/geek/websim/runtime src/test/java/com/geek/websim
git commit -m "feat: add simulation metrics and sampled logs"
```

---

## Task 9: Scaffold React/Vite frontend

**Files:**
- Create all frontend toolchain files and UI primitives listed in File Structure.

- [ ] **Step 1: Create frontend package**

Write `frontend/package.json` using `web-router` dependency versions and app name `web-sim-admin-frontend`.

Scripts:

```json
{
  "dev": "vite --host 127.0.0.1 --port 5174",
  "test": "vitest",
  "typecheck": "tsc --noEmit",
  "build": "tsc --noEmit && vite build"
}
```

- [ ] **Step 2: Copy frontend config patterns from web-router**

Create TypeScript, Vite, PostCSS, Tailwind, and path alias config matching `web-router/frontend`, with output directory `../src/main/resources/static/admin` and asset file names `assets/[name].[hash][extname]` or update `index.html` if hashed assets are used.

- [ ] **Step 3: Create UI primitives**

Copy the UI primitive structure from `web-router/frontend/src/components/ui` and keep the same exported component names. Update import aliases to `@/`.

- [ ] **Step 4: Create API helpers**

`fetchJson<T>(url, options)` unwraps backend `Result<T>`, throws when `success=false`, and returns `data`.

- [ ] **Step 5: Create app shell**

`App.tsx` renders:

- Hero with `WEB SIMULATOR CONTROL`.
- Four overview cards.
- Simulation toolbar.
- Empty state card before API data exists.

- [ ] **Step 6: Verify frontend scaffold**

Run:

```bash
cd frontend && npm install && npm run typecheck && npm run build
```

Expected: install succeeds, typecheck passes, build writes admin assets.

- [ ] **Step 7: Commit frontend scaffold**

```bash
git add frontend src/main/resources/static/admin
git commit -m "feat: scaffold web-sim admin frontend"
```

---

## Task 10: Add simulation cards, filters, and form dialog

**Files:**
- Create: frontend simulation feature files listed in File Structure.
- Tests: `frontend/src/features/simulations/sim-utils.test.ts`, `frontend/src/lib/api.test.ts`.

- [ ] **Step 1: Define frontend types**

`types.ts` mirrors backend JSON:

```ts
export type ProtocolType = 'HTTP' | 'TCP';
export type HttpMatchMode = 'EXACT' | 'PREFIX' | 'TEMPLATE';
export type TcpFrameMode = 'LINE' | 'LENGTH_HEADER' | 'HEX';
export type ConditionSource = 'QUERY' | 'HEADER' | 'PATH' | 'BODY' | 'TCP_BODY';
export type ConditionOperator = 'EQ' | 'NOT_EQ' | 'CONTAINS' | 'REGEX' | 'EXISTS' | 'JSON_PATH';
```

Define `SimulationConfig`, `SimulationBranch`, `SimulationCondition`, `SimulationResponse`, and `SimulationConfigPayload`.

- [ ] **Step 2: Write utility tests**

Tests cover:

- `displayEndpoint()` returns `GET /api/users/{id}` for HTTP.
- `displayEndpoint()` returns `127.0.0.1:19001 LINE` for TCP.
- `defaultPayload('HTTP')` includes one success branch and 404 default response.
- `validatePayload()` rejects missing name, missing HTTP path, missing TCP port.

- [ ] **Step 3: Implement simulation utilities**

`sim-utils.ts` exports:

```ts
displayEndpoint(config: SimulationConfig): string
countBranches(config: SimulationConfig): number
defaultPayload(protocol: ProtocolType): SimulationConfigPayload
validatePayload(payload: SimulationConfigPayload): string[]
configToPayload(config: SimulationConfig): SimulationConfigPayload
```

- [ ] **Step 4: Implement toolbar and card**

`SimulationToolbar` props: heading id, search, protocol filter, enabled filter, selected count, callbacks.

`SimulationCard` props: config, selected, metrics summary, callbacks for selected/view/copy/toggle/delete.

- [ ] **Step 5: Implement form dialog**

`SimulationFormDialog` supports create/edit/copy. It includes fields:

- name
- protocol
- enabled
- HTTP method/path/match mode
- TCP host/port/frame mode
- default status/body
- editable JSON textarea for branches

Validate before submit and show `toast.error` for validation failures.

- [ ] **Step 6: Wire App data flow**

`App.tsx` loads `/admin/api/simulations`, filters by search/protocol/enabled, creates/updates/toggles/deletes via backend API, and shows cards.

- [ ] **Step 7: Run frontend tests**

```bash
cd frontend && npm run test -- --run && npm run typecheck && npm run build
```

Expected: PASS.

- [ ] **Step 8: Commit cards and form**

```bash
git add frontend/src/features/simulations frontend/src/App.tsx frontend/src/lib
git commit -m "feat: add simulation cards and editor"
```

---

## Task 11: Add detail drawer, raw JSON preview, and logs dialog

**Files:**
- Create/modify: `SimulationDetailDrawer.tsx`, `SimulationLogDialog.tsx`, `features/logs/types.ts`, `App.tsx`.

- [ ] **Step 1: Add raw JSON loading to App**

When a card detail opens, call `/admin/api/simulations/{id}/raw` and keep `fileName`, `content`, `loading`, and `error` state.

- [ ] **Step 2: Implement detail drawer sections**

Drawer contains tabs:

- `概览` — protocol, endpoint, enabled, branch count, default status.
- `编辑` — embeds same editing controls as form dialog.
- `分支` — formatted branch JSON textarea with validation.
- `原始 JSON` — file path and formatted raw content.

- [ ] **Step 3: Implement logs dialog**

`SimulationLogDialog` fetches `/admin/api/logs/snapshot` and renders total requests, HTTP/TCP counts, error count, average duration, and recent logs table.

- [ ] **Step 4: Add SSE refresh**

Open `EventSource('/admin/api/logs/stream')` while logs dialog is open; parse snapshot events and update table. Close EventSource on unmount.

- [ ] **Step 5: Run frontend verification**

```bash
cd frontend && npm run test -- --run && npm run typecheck && npm run build
```

Expected: PASS.

- [ ] **Step 6: Commit detail and logs UI**

```bash
git add frontend/src/features frontend/src/App.tsx
git commit -m "feat: add simulation detail and logs UI"
```

---

## Task 12: Add README, examples, and final verification

**Files:**
- Modify: `/Users/xin/Documents/CodexProjects/web-sim/README.md`
- Create: `/Users/xin/Documents/CodexProjects/web-sim/scripts/build-dist.sh`

- [ ] **Step 1: Replace README with user-facing documentation**

README sections:

- Overview and feature badges.
- Architecture diagram.
- Quick start:

```bash
mvn test
mvn spring-boot:run
```

- Admin URL: `http://localhost:9998/admin`.
- HTTP simulation example with curl create and curl invoke.
- TCP simulation example with JSON config and `nc 127.0.0.1 19001`.
- Random value template reference.
- Error code examples: 400, 401, 403, 404, 429, 500, 502, 503, 504.
- Performance notes: single-node high concurrency, million-level requires multiple instances, load balancer, OS tuning, sampled logs.
- Development commands for backend and frontend.

- [ ] **Step 2: Add build script**

`scripts/build-dist.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -d frontend ]]; then
  (cd frontend && npm install && npm run build)
fi

mvn -q test package
mkdir -p target/dist
cp target/web-sim-*.jar target/dist/web-sim.jar
cp -R config target/dist/config
cp README.md target/dist/README.md

echo "Distribution written to target/dist"
```

Make executable:

```bash
chmod +x scripts/build-dist.sh
```

- [ ] **Step 3: Run full backend verification**

```bash
mvn test
```

Expected: PASS.

- [ ] **Step 4: Run frontend verification**

```bash
cd frontend && npm run test -- --run && npm run typecheck && npm run build
```

Expected: PASS.

- [ ] **Step 5: Run packaged build**

```bash
scripts/build-dist.sh
```

Expected: `target/dist/web-sim.jar` exists.

- [ ] **Step 6: Commit docs and build script**

```bash
git add README.md scripts/build-dist.sh
git commit -m "docs: document web-sim usage"
```

---

## Self-Review

### Spec coverage

- Full-stack Spring Boot + React implementation: covered by Tasks 1, 2, 9, 10, 11.
- JSON-backed simulation config: covered by Task 3.
- HTTP matching with EXACT/PREFIX/TEMPLATE: covered by Tasks 4, 5, 6.
- TCP line-based simulator with reserved frame modes: covered by Tasks 3 and 7.
- Request/response branch parameters: covered by Tasks 3, 4, 5, 10, 11.
- Random values and template variables: covered by Task 4.
- Error status responses: covered by Tasks 5, 6, 12.
- Card-based UI: covered by Tasks 9, 10, 11.
- Metrics and sampled logs: covered by Task 8 and Task 11.
- High-concurrency strategy: covered by Tasks 5, 8, 12.
- Verification and documentation: covered by Task 12.

### Placeholder scan

This plan intentionally avoids unresolved placeholders. Protocol modes `LENGTH_HEADER` and `HEX` are explicit reserved enum values, while first-version runtime support is limited to `LINE` as specified.

### Type consistency

Frontend enum names mirror backend enum names. Backend DTO/entity fields mirror the JSON spec. Runtime request fields are referenced consistently across matcher, branch matcher, renderer, HTTP handler, and TCP manager tasks.
