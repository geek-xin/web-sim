package com.geek.websim.web.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geek.websim.common.constants.CommonConstants;
import com.geek.websim.common.enums.ErrorCodeEnum;
import com.geek.websim.common.exception.BusinessException;
import com.geek.websim.config.SimulationProperties;
import com.geek.websim.web.model.entity.HttpRule;
import com.geek.websim.web.model.entity.RequestTemplate;
import com.geek.websim.web.model.entity.SimulationBranch;
import com.geek.websim.web.model.entity.SimulationCondition;
import com.geek.websim.web.model.entity.SimulationConfig;
import com.geek.websim.web.model.entity.SimulationResponse;
import com.geek.websim.web.model.entity.TcpRule;
import com.geek.websim.web.model.enums.ConditionOperator;
import com.geek.websim.web.model.enums.ConditionSource;
import com.geek.websim.web.model.enums.HttpMatchMode;
import com.geek.websim.web.model.enums.ProtocolType;
import com.geek.websim.web.model.enums.ResponseVariantStrategy;
import com.geek.websim.web.model.enums.TcpFrameMode;
import com.geek.websim.web.service.SimulationConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class SimulationConfigServiceImpl implements SimulationConfigService {
    private static final Pattern SAFE_ID = Pattern.compile("[a-zA-Z0-9_-]+");
    private static final Pattern RESPONSE_HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String DEFAULT_TCP_HOST = "127.0.0.1";
    private static final Set<String> DISALLOWED_RESPONSE_HEADERS = Set.of("content-length", "transfer-encoding");

    private final ObjectMapper objectMapper;
    private final Path configDir;

    @Autowired
    public SimulationConfigServiceImpl(ObjectMapper objectMapper, SimulationProperties properties) {
        this(objectMapper, Path.of(properties.configDir()));
    }

    public SimulationConfigServiceImpl(ObjectMapper objectMapper, Path configDir) {
        this.objectMapper = objectMapper;
        this.configDir = configDir;
    }

    @Override
    public void initDefaultConfigs() {
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, "配置目录创建失败");
        }
    }

    @Override
    public List<SimulationConfig> listAll() {
        initDefaultConfigs();
        try (Stream<Path> paths = Files.list(configDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(CommonConstants.CONFIG_FILE_EXTENSION))
                    .sorted(Comparator.comparingLong(this::lastModifiedMillis).reversed())
                    .map(this::readConfig)
                    .toList();
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, "配置列表读取失败");
        }
    }

    @Override
    public SimulationConfig getById(String id) {
        return readConfig(fileForId(validateId(id)));
    }

    @Override
    public synchronized SimulationConfig create(SimulationConfig config) {
        initDefaultConfigs();
        SimulationConfig normalized = normalizeForWrite(config);
        ensureUniqueName(normalized.getName(), null);
        ensureUniqueTcpBinding(normalized, null);
        normalized.setId(generateId());
        writeConfig(normalized);
        return normalized;
    }

    @Override
    public synchronized SimulationConfig update(String id, SimulationConfig config) {
        String safeId = validateId(id);
        Path file = fileForId(safeId);
        if (!Files.exists(file)) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "配置不存在");
        }
        SimulationConfig normalized = normalizeForWrite(config);
        normalized.setId(safeId);
        ensureUniqueName(normalized.getName(), safeId);
        ensureUniqueTcpBinding(normalized, safeId);
        writeConfig(normalized);
        return normalized;
    }

    @Override
    public synchronized SimulationConfig restore(String id, SimulationConfig config) {
        String safeId = validateId(id);
        SimulationConfig normalized = normalizeForWrite(config);
        normalized.setId(safeId);
        ensureUniqueName(normalized.getName(), safeId);
        ensureUniqueTcpBinding(normalized, safeId);
        writeConfig(normalized);
        return normalized;
    }

    @Override
    public void delete(String id) {
        String safeId = validateId(id);
        try {
            boolean deleted = Files.deleteIfExists(fileForId(safeId));
            if (!deleted) {
                throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "配置不存在");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, "配置删除失败");
        }
    }

    @Override
    public String rawJson(String id) {
        String safeId = validateId(id);
        try {
            return Files.readString(fileForId(safeId), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "配置不存在");
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, "配置读取失败");
        }
    }

    private SimulationConfig normalizeForWrite(SimulationConfig config) {
        if (config == null) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "配置不能为空");
        }
        config = copyConfig(config);
        if (isBlank(config.getName())) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "模拟名称不能为空");
        }
        config.setName(config.getName().trim());
        if (config.getProtocol() == null) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "协议不能为空");
        }
        if (config.getDefaultResponse() == null) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "默认响应不能为空");
        }
        if (config.getRequestTemplate() == null) {
            config.setRequestTemplate(new RequestTemplate());
        }
        normalizeRequestTemplate(config.getRequestTemplate());
        if (config.getBranches() == null) {
            config.setBranches(new ArrayList<>());
        }
        normalizeResponse(config.getDefaultResponse());
        for (SimulationBranch branch : config.getBranches()) {
            normalizeBranch(branch);
        }
        if (config.getProtocol() == ProtocolType.HTTP) {
            normalizeHttp(config);
        } else if (config.getProtocol() == ProtocolType.TCP) {
            normalizeTcp(config);
        }
        return config;
    }

    private void normalizeHttp(SimulationConfig config) {
        HttpRule http = config.getHttp();
        if (http == null) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "HTTP配置不能为空");
        }
        if (isBlank(http.getMethod())) {
            http.setMethod("ANY");
        } else {
            http.setMethod(http.getMethod().trim().toUpperCase(Locale.ROOT));
        }
        String path = http.getPath() == null ? null : http.getPath().trim();
        if (isBlank(path) || !path.startsWith("/")) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "HTTP路径必须以/开头");
        }
        http.setPath(path);
        if (http.getMatchMode() == null) {
            http.setMatchMode(HttpMatchMode.EXACT);
        }
    }

    private void normalizeTcp(SimulationConfig config) {
        TcpRule tcp = config.getTcp();
        if (tcp == null) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "TCP配置不能为空");
        }
        if (isBlank(tcp.getHost())) {
            tcp.setHost(DEFAULT_TCP_HOST);
        } else {
            tcp.setHost(tcp.getHost().trim());
        }
        if (tcp.getPort() == null || tcp.getPort() < 1 || tcp.getPort() > 65_535) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "TCP端口必须在1到65535之间");
        }
        if (tcp.getFrameMode() == null) {
            tcp.setFrameMode(TcpFrameMode.LINE);
        }
    }

    private void normalizeRequestTemplate(RequestTemplate requestTemplate) {
        if (requestTemplate.getHeaders() == null) {
            requestTemplate.setHeaders(new LinkedHashMap<>());
        }
        if (requestTemplate.getQuery() == null) {
            requestTemplate.setQuery(new LinkedHashMap<>());
        }
    }

    private void normalizeBranch(SimulationBranch branch) {
        if (branch == null) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "分支配置不能为空");
        }
        if (branch.getConditions() == null) {
            branch.setConditions(new ArrayList<>());
        }
        for (SimulationCondition condition : branch.getConditions()) {
            normalizeCondition(condition);
        }
        if (branch.getResponse() == null) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "分支响应不能为空");
        }
        normalizeResponse(branch.getResponse());
        if (branch.getResponseVariants() == null) {
            branch.setResponseVariants(new ArrayList<>());
        }
        for (SimulationResponse responseVariant : branch.getResponseVariants()) {
            if (responseVariant == null) {
                throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "分支响应变体不能为空");
            }
            normalizeResponse(responseVariant);
        }
        if (branch.getVariantStrategy() == null) {
            branch.setVariantStrategy(ResponseVariantStrategy.ROUND_ROBIN);
        }
    }

    private void normalizeCondition(SimulationCondition condition) {
        if (condition == null) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "分支条件不能为空");
        }
        ConditionSource source = condition.getSource();
        ConditionOperator operator = condition.getOperator();
        if (source == null) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "条件来源不能为空");
        }
        if (operator == null) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "条件操作符不能为空");
        }
        if (requiresConditionKey(source, operator) && isBlank(condition.getKey())) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "条件键不能为空");
        }
        if (!isBlank(condition.getKey())) {
            condition.setKey(condition.getKey().trim());
        }
        if (operator != ConditionOperator.EXISTS && isBlank(condition.getValue())) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "条件值不能为空");
        }
        if (condition.getValue() != null) {
            condition.setValue(condition.getValue().trim());
        }
    }

    private boolean requiresConditionKey(ConditionSource source, ConditionOperator operator) {
        return source == ConditionSource.QUERY
                || source == ConditionSource.HEADER
                || source == ConditionSource.PATH
                || operator == ConditionOperator.JSON_PATH;
    }

    private void normalizeResponse(SimulationResponse response) {
        if (response.getStatus() == null) {
            response.setStatus(200);
        }
        if (response.getStatus() < 100 || response.getStatus() > 999) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "响应状态码必须在100到999之间");
        }
        if (response.getHeaders() == null) {
            response.setHeaders(new LinkedHashMap<>());
        } else {
            response.setHeaders(normalizedResponseHeaders(response.getHeaders()));
        }
        if (response.getBody() == null) {
            response.setBody("");
        }
        if (response.getDelayMs() == null || response.getDelayMs() < 0) {
            response.setDelayMs(0L);
        }
    }

    private Map<String, String> normalizedResponseHeaders(Map<String, String> headers) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey();
            validateResponseHeaderName(name);
            normalized.put(name, normalizedResponseHeaderValue(entry.getValue()));
        }
        return normalized;
    }

    private void validateResponseHeaderName(String name) {
        if (isBlank(name)) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "响应头名称不能为空");
        }
        if (!RESPONSE_HEADER_NAME.matcher(name).matches()) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "响应头名称不合法");
        }
        if (DISALLOWED_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "响应头不允许包含" + name);
        }
    }

    private String normalizedResponseHeaderValue(String value) {
        if (value == null) {
            return "";
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 0x20 || ch == 0x7f) {
                throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "响应头值不合法");
            }
        }
        return value;
    }

    private void ensureUniqueName(String name, String excludedId) {
        for (SimulationConfig existing : listAll()) {
            if (isSameId(existing.getId(), excludedId)) {
                continue;
            }
            if (existing.getName() != null && existing.getName().trim().equals(name)) {
                throw new BusinessException(ErrorCodeEnum.DUPLICATE_NAME, "模拟名称已存在");
            }
        }
    }

    private void ensureUniqueTcpBinding(SimulationConfig candidate, String excludedId) {
        if (!candidate.isEnabled() || candidate.getProtocol() != ProtocolType.TCP || candidate.getTcp() == null) {
            return;
        }
        Integer port = candidate.getTcp().getPort();
        for (SimulationConfig existing : listAll()) {
            if (isSameId(existing.getId(), excludedId)
                    || !existing.isEnabled()
                    || existing.getProtocol() != ProtocolType.TCP
                    || existing.getTcp() == null) {
                continue;
            }
            TcpRule existingTcp = existing.getTcp();
            if (port.equals(existingTcp.getPort())) {
                throw new BusinessException(ErrorCodeEnum.DUPLICATE_BINDING, "TCP监听端口已被占用");
            }
        }
    }

    private boolean isSameId(String id, String expectedId) {
        return expectedId != null && expectedId.equals(id);
    }

    private SimulationConfig readConfig(Path file) {
        if (!Files.exists(file)) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "配置不存在");
        }
        try {
            SimulationConfig config = objectMapper.readValue(file.toFile(), SimulationConfig.class);
            if (isBlank(config.getId())) {
                config.setId(idFromFile(file));
            }
            return config;
        } catch (FileNotFoundException | NoSuchFileException e) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "配置不存在");
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, "配置读取失败");
        }
    }

    private void writeConfig(SimulationConfig config) {
        Path tempFile = null;
        try {
            initDefaultConfigs();
            Path target = fileForId(config.getId());
            tempFile = Files.createTempFile(configDir, config.getId() + "-", ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), config);
            moveIntoPlace(tempFile, target);
            tempFile = null;
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.CONFIG_IO_ERROR, "配置写入失败");
        } finally {
            deleteTempFile(tempFile);
        }
    }

    private SimulationConfig copyConfig(SimulationConfig config) {
        try {
            return objectMapper.convertValue(config, SimulationConfig.class);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "配置格式不正确");
        }
    }

    private void moveIntoPlace(Path tempFile, Path target) throws IOException {
        try {
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteTempFile(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    private Path fileForId(String id) {
        return configDir.resolve(id + CommonConstants.CONFIG_FILE_EXTENSION);
    }

    private String idFromFile(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - CommonConstants.CONFIG_FILE_EXTENSION.length());
    }

    private String validateId(String id) {
        if (isBlank(id) || !SAFE_ID.matcher(id).matches()) {
            throw new BusinessException(ErrorCodeEnum.BAD_REQUEST, "配置ID不合法");
        }
        return id;
    }

    private String generateId() {
        String time = LocalDateTime.now().format(ID_TIME);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return "sim-" + time + "-" + suffix;
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
