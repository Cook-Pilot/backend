package com.cookpilot.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
class IdempotencyService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final DatabaseJson databaseJson;
  private final TransactionTemplate transactions;
  private final Object h2Lock = new Object();

  IdempotencyService(
      JdbcTemplate jdbc,
      ObjectMapper objectMapper,
      DatabaseJson databaseJson,
      PlatformTransactionManager transactionManager) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.databaseJson = databaseJson;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  <T> T execute(
      InstallPrincipal principal,
      String rawKey,
      String operation,
      Object request,
      int httpStatus,
      Class<T> responseType,
      Supplier<T> action) {
    if (!databaseJson.isPostgres()) {
      synchronized (h2Lock) {
        return transactions.execute(
            status -> executeInTransaction(principal, rawKey, operation, request, httpStatus, responseType, action, false));
      }
    }
    return transactions.execute(
        status -> executeInTransaction(principal, rawKey, operation, request, httpStatus, responseType, action, true));
  }

  private <T> T executeInTransaction(
      InstallPrincipal principal,
      String rawKey,
      String operation,
      Object request,
      int httpStatus,
      Class<T> responseType,
      Supplier<T> action,
      boolean postgres) {
    UUID key = parseKey(rawKey);
    String requestHash = hash(operation + "\n" + databaseJson.stringify(request));

    int inserted;
    if (postgres) {
      inserted =
          jdbc.update(
              """
              INSERT INTO idempotency_operations
                (install_id, idempotency_key, operation, request_hash, created_at, expires_at)
              VALUES (?, ?, ?, ?, ?, ?)
              ON CONFLICT (install_id, idempotency_key) DO NOTHING
              """,
              principal.installId(),
              key,
              operation,
              requestHash,
              Timestamp.from(Instant.now()),
              Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS)));
    } else if (findOptional(principal.installId(), key).isPresent()) {
      inserted = 0;
    } else {
      inserted =
          jdbc.update(
              """
              INSERT INTO idempotency_operations
                (install_id, idempotency_key, operation, request_hash, created_at, expires_at)
              VALUES (?, ?, ?, ?, ?, ?)
              """,
              principal.installId(),
              key,
              operation,
              requestHash,
              Timestamp.from(Instant.now()),
              Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS)));
    }

    if (inserted == 0) {
      StoredOperation stored = find(principal.installId(), key);
      if (!stored.operation().equals(operation) || !stored.requestHash().equals(requestHash)) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 Idempotency-Key를 다른 요청에 사용할 수 없습니다.");
      }
      if (stored.responseBody() == null) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 요청이 처리 중입니다.");
      }
      try {
        return objectMapper.readValue(stored.responseBody(), responseType);
      } catch (Exception exception) {
        throw new IllegalStateException("Stored idempotent response is invalid", exception);
      }
    }

    T response = action.get();
    jdbc.update(
        """
        UPDATE idempotency_operations
        SET http_status = ?, response_body = ?
        WHERE install_id = ? AND idempotency_key = ?
        """,
        httpStatus,
        databaseJson.value(response),
        principal.installId(),
        key);
    return response;
  }

  private StoredOperation find(UUID installId, UUID key) {
    return findOptional(installId, key)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "멱등 요청 상태를 확인할 수 없습니다."));
  }

  private java.util.Optional<StoredOperation> findOptional(UUID installId, UUID key) {
    List<StoredOperation> rows =
        jdbc.query(
            """
            SELECT operation, request_hash, response_body
            FROM idempotency_operations
            WHERE install_id = ? AND idempotency_key = ?
            """,
            (resultSet, rowNumber) ->
                new StoredOperation(
                    resultSet.getString("operation"),
                    resultSet.getString("request_hash"),
                    resultSet.getString("response_body")),
            installId,
            key);
    return rows.stream().findFirst();
  }

  private UUID parseKey(String rawKey) {
    if (rawKey == null || rawKey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key가 필요합니다.");
    }
    try {
      return UUID.fromString(rawKey);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key는 UUID여야 합니다.");
    }
  }

  private String hash(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}

record StoredOperation(String operation, String requestHash, String responseBody) {}
