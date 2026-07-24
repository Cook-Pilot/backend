package com.cookpilot.api;

import com.cookpilot.api.ApiModels.AnonymousInstallRequest;
import com.cookpilot.api.ApiModels.AnonymousInstallResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class InstallPrincipalService {
  private static final SecureRandom RANDOM = new SecureRandom();

  private final JdbcTemplate jdbc;

  InstallPrincipalService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  AnonymousInstallResponse bootstrap(AnonymousInstallRequest request) {
    UUID installId = request.installId();
    String token = newToken();
    Instant now = Instant.now();
    try {
      jdbc.update(
          "INSERT INTO users (id, display_name, created_at, updated_at) VALUES (?, ?, ?, ?)",
          installId,
          "CookPilot 사용자",
          Timestamp.from(now),
          Timestamp.from(now));
      jdbc.update(
          """
          INSERT INTO anonymous_installs
            (id, user_id, credential_hash, status, created_at)
          VALUES (?, ?, ?, 'active', ?)
          """,
          installId,
          installId,
          hash(token),
          Timestamp.from(now));
    } catch (DuplicateKeyException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 설치 ID입니다.");
    }
    return new AnonymousInstallResponse(installId, token);
  }

  InstallPrincipal authenticate(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "설치 인증 정보가 필요합니다.");
    }
    String token = authorization.substring("Bearer ".length()).trim();
    if (token.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "설치 인증 정보가 필요합니다.");
    }
    List<InstallPrincipal> rows =
        jdbc.query(
            """
            SELECT id, user_id
            FROM anonymous_installs
            WHERE credential_hash = ? AND status = 'active'
            """,
            (resultSet, rowNumber) ->
                new InstallPrincipal(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("user_id", UUID.class)),
            hash(token));
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 설치 인증 정보입니다.");
    }
    return rows.getFirst();
  }

  private String newToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

record InstallPrincipal(UUID installId, UUID userId) {}
