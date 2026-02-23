package com.example.order.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class StructuredLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(StructuredLogger.class);
    private final ObjectMapper objectMapper;

    public StructuredLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void info(String event, Map<String, Object> fields) {
        write("INFO", event, fields);
    }

    public void warn(String event, Map<String, Object> fields) {
        write("WARN", event, fields);
    }

    public void error(String event, Map<String, Object> fields) {
        write("ERROR", event, fields);
    }

    private void write(String level, String event, Map<String, Object> fields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ts", Instant.now().toString());
        payload.put("level", level);
        payload.put("event", event);
        payload.putAll(fields);

        try {
            String json = objectMapper.writeValueAsString(payload);
            switch (level) {
                case "ERROR" -> LOGGER.error(json);
                case "WARN" -> LOGGER.warn(json);
                default -> LOGGER.info(json);
            }
        } catch (JsonProcessingException e) {
            LOGGER.error("{\"level\":\"ERROR\",\"event\":\"logger.serialization_failed\",\"message\":\"{}\"}", e.getMessage());
        }
    }
}
