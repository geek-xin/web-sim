package com.geek.websim.web.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geek.websim.config.SimulationProperties;
import com.geek.websim.web.model.dto.SimulationLogEntry;
import com.geek.websim.web.service.SimulationLogWriter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class FileSimulationLogWriter implements SimulationLogWriter {
    private static final Logger log = LoggerFactory.getLogger(FileSimulationLogWriter.class);
    private static final Pattern SAFE_PATH_CHARS = Pattern.compile("[^A-Za-z0-9._-]");

    private final Path logDir;
    private final int retentionDays;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<Path, Object> fileLocks = new ConcurrentHashMap<>();
    private final AtomicLong nextCleanupAtMillis = new AtomicLong();

    @Autowired
    public FileSimulationLogWriter(SimulationProperties properties, ObjectMapper objectMapper) {
        this(Paths.get(properties == null ? "logs/simulations" : properties.logDir()),
                properties == null ? 7 : properties.logRetentionDays(),
                objectMapper,
                Clock.systemDefaultZone());
    }

    public FileSimulationLogWriter(Path logDir, int retentionDays, ObjectMapper objectMapper, Clock clock) {
        this.logDir = logDir == null ? Paths.get("logs/simulations") : logDir;
        this.retentionDays = Math.max(1, retentionDays);
        this.objectMapper = objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    @PostConstruct
    void initialize() {
        cleanupExpiredLogs();
    }

    @Override
    public void append(SimulationLogEntry entry) {
        if (entry == null) {
            return;
        }
        try {
            Path logFile = logFile(entry);
            Files.createDirectories(logFile.getParent());
            String line = objectMapper.writeValueAsString(entry) + System.lineSeparator();
            Object lock = fileLocks.computeIfAbsent(logFile.toAbsolutePath().normalize(), ignored -> new Object());
            synchronized (lock) {
                Files.writeString(logFile, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            cleanupExpiredLogsIfDue();
        } catch (Exception error) {
            log.warn("Failed to append simulation text log", error);
        }
    }

    @Override
    public List<SimulationLogEntry> loadRetainedLogs() {
        cleanupExpiredLogs();
        if (!Files.exists(logDir)) {
            return List.of();
        }
        List<SimulationLogEntry> entries = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(logDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".log"))
                    .sorted()
                    .forEach(path -> readLogFile(path, entries));
        } catch (IOException error) {
            log.warn("Failed to load retained simulation text logs", error);
        }
        entries.sort(Comparator.comparing(entry -> entry.getTimestamp() == null ? Instant.EPOCH : entry.getTimestamp()));
        return entries;
    }

    @Override
    public void cleanupExpiredLogs() {
        if (!Files.exists(logDir)) {
            return;
        }
        nextCleanupAtMillis.set(clock.millis() + TimeUnit.HOURS.toMillis(1));
        LocalDate cutoff = LocalDate.now(clock).minusDays(retentionDays - 1L);
        try (Stream<Path> paths = Files.walk(logDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".log"))
                    .filter(path -> isExpired(path, cutoff))
                    .forEach(this::deleteQuietly);
        } catch (IOException error) {
            log.warn("Failed to clean expired simulation text logs", error);
        }
        removeEmptyDirectories();
    }

    private void cleanupExpiredLogsIfDue() {
        long now = clock.millis();
        long nextCleanup = nextCleanupAtMillis.get();
        if (now < nextCleanup) {
            return;
        }
        if (nextCleanupAtMillis.compareAndSet(nextCleanup, now + TimeUnit.HOURS.toMillis(1))) {
            cleanupExpiredLogs();
        }
    }

    private Path logFile(SimulationLogEntry entry) {
        String simulationId = sanitize(entry.getSimulationId());
        LocalDate date = LocalDate.ofInstant(timestamp(entry), clock.getZone());
        return logDir.resolve(simulationId).resolve(date + ".log");
    }

    private Instant timestamp(SimulationLogEntry entry) {
        return entry.getTimestamp() == null ? clock.instant() : entry.getTimestamp();
    }

    private String sanitize(String value) {
        String raw = value == null || value.isBlank() ? "unknown" : value.trim();
        String safe = SAFE_PATH_CHARS.matcher(raw).replaceAll("_");
        if (safe.isBlank()) {
            return "unknown";
        }
        return safe.length() > 120 ? safe.substring(0, 120) : safe;
    }

    private boolean isExpired(Path path, LocalDate cutoff) {
        String fileName = path.getFileName().toString();
        String datePart = fileName.substring(0, fileName.length() - ".log".length());
        try {
            return LocalDate.parse(datePart).isBefore(cutoff);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
            fileLocks.remove(path.toAbsolutePath().normalize());
        } catch (IOException error) {
            log.warn("Failed to delete expired simulation text log {}", path, error);
        }
    }

    private void readLogFile(Path path, List<SimulationLogEntry> entries) {
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            lines.filter(line -> !line.isBlank())
                    .forEach(line -> readLogLine(path, line, entries));
        } catch (IOException error) {
            log.warn("Failed to read simulation text log {}", path, error);
        }
    }

    private void readLogLine(Path path, String line, List<SimulationLogEntry> entries) {
        try {
            entries.add(objectMapper.readValue(line, SimulationLogEntry.class));
        } catch (IOException error) {
            log.warn("Skipped malformed simulation text log line in {}", path, error);
        }
    }

    private void removeEmptyDirectories() {
        if (!Files.exists(logDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(logDir)) {
            paths.filter(Files::isDirectory)
                    .filter(path -> !path.equals(logDir))
                    .sorted(Comparator.reverseOrder())
                    .forEach(this::deleteDirectoryIfEmpty);
        } catch (IOException error) {
            log.warn("Failed to remove empty simulation log directories", error);
        }
    }

    private void deleteDirectoryIfEmpty(Path path) {
        try (Stream<Path> children = Files.list(path)) {
            if (children.findAny().isEmpty()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException error) {
            log.debug("Skipped non-empty or inaccessible simulation log directory {}", path, error);
        }
    }
}
