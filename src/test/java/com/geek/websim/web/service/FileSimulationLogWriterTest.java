package com.geek.websim.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geek.websim.web.model.dto.SimulationLogEntry;
import com.geek.websim.web.model.enums.ProtocolType;
import com.geek.websim.web.service.impl.FileSimulationLogWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileSimulationLogWriterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    void appendsJsonLineLogEntryGroupedBySimulationCardAndDate() throws Exception {
        FileSimulationLogWriter writer = new FileSimulationLogWriter(tempDir, 7, OBJECT_MAPPER,
                Clock.fixed(Instant.parse("2026-07-11T10:00:00Z"), ZoneOffset.UTC));

        writer.append(entry("entry-1", "sim/card:alpha", "Alpha Card", Instant.parse("2026-07-11T08:00:00Z")));

        Path logFile = tempDir.resolve("sim_card_alpha").resolve("2026-07-11.log");
        assertThat(logFile).exists();
        List<String> lines = Files.readAllLines(logFile);
        assertThat(lines).hasSize(1);

        JsonNode json = OBJECT_MAPPER.readTree(lines.getFirst());
        assertThat(json.get("id").asText()).isEqualTo("entry-1");
        assertThat(json.get("simulationId").asText()).isEqualTo("sim/card:alpha");
        assertThat(json.get("simulationName").asText()).isEqualTo("Alpha Card");
        assertThat(json.get("requestSummary").asText()).isEqualTo("GET /hello");
        assertThat(json.get("responseSummary").asText()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void deletesDailyLogFilesOlderThanRetentionWindow() throws Exception {
        Files.createDirectories(tempDir.resolve("sim-a"));
        Path oldLog = tempDir.resolve("sim-a").resolve("2026-07-03.log");
        Path retainedLog = tempDir.resolve("sim-a").resolve("2026-07-05.log");
        Files.writeString(oldLog, "{}\n");
        Files.writeString(retainedLog, "{}\n");

        FileSimulationLogWriter writer = new FileSimulationLogWriter(tempDir, 7, OBJECT_MAPPER,
                Clock.fixed(Instant.parse("2026-07-11T10:00:00Z"), ZoneOffset.UTC));

        writer.cleanupExpiredLogs();

        assertThat(oldLog).doesNotExist();
        assertThat(retainedLog).exists();
    }

    @Test
    void loadsRetainedTextLogsSortedByTimestamp() throws Exception {
        FileSimulationLogWriter writer = new FileSimulationLogWriter(tempDir, 7, OBJECT_MAPPER,
                Clock.fixed(Instant.parse("2026-07-11T10:00:00Z"), ZoneOffset.UTC));
        writer.append(entry("new", "sim-a", "Alpha Card", Instant.parse("2026-07-11T09:00:00Z")));
        writer.append(entry("old", "sim-a", "Alpha Card", Instant.parse("2026-07-10T09:00:00Z")));

        List<SimulationLogEntry> logs = writer.loadRetainedLogs();

        assertThat(logs).extracting(SimulationLogEntry::getId)
                .containsExactly("old", "new");
    }

    private SimulationLogEntry entry(String id, String simulationId, String simulationName, Instant timestamp) {
        return SimulationLogEntry.builder()
                .id(id)
                .simulationId(simulationId)
                .simulationName(simulationName)
                .protocol(ProtocolType.HTTP)
                .status(200)
                .durationMs(12)
                .requestSummary("GET /hello")
                .responseSummary("{\"ok\":true}")
                .timestamp(timestamp)
                .build();
    }
}
