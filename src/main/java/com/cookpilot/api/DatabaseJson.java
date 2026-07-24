package com.cookpilot.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Component;

@Component
class DatabaseJson {
  private final ObjectMapper objectMapper;
  private final DataSource dataSource;
  private volatile Boolean postgres;

  DatabaseJson(ObjectMapper objectMapper, DataSource dataSource) {
    this.objectMapper = objectMapper;
    this.dataSource = dataSource;
  }

  Object value(Object value) {
    String json = stringify(value);
    if (!isPostgres()) {
      return json;
    }
    try {
      PGobject object = new PGobject();
      object.setType("jsonb");
      object.setValue(json);
      return object;
    } catch (SQLException exception) {
      throw new IllegalArgumentException("JSON payload could not be stored", exception);
    }
  }

  String stringify(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("JSON payload could not be serialized", exception);
    }
  }

  JsonNode parse(String value) {
    try {
      return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Stored JSON payload is invalid", exception);
    }
  }

  boolean isPostgres() {
    Boolean cached = postgres;
    if (cached != null) {
      return cached;
    }
    try (Connection connection = dataSource.getConnection()) {
      boolean detected = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
      postgres = detected;
      return detected;
    } catch (SQLException exception) {
      throw new IllegalStateException("Database type could not be detected", exception);
    }
  }
}
