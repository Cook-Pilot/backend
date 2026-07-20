package com.cookpilot.backend.cooksession;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * SessionStatus 와 DB의 소문자 status 문자열('ready', 'cooking', ...)을 상호 변환한다.
 * 엔티티는 enum 으로 타입 안전성을 얻고, DB에는 스키마 기본값('ready')과 동일한 소문자 규약을 유지한다.
 */
@Converter
public class SessionStatusConverter implements AttributeConverter<SessionStatus, String> {

	@Override
	public String convertToDatabaseColumn(SessionStatus status) {
		return status == null ? null : status.name().toLowerCase();
	}

	@Override
	public SessionStatus convertToEntityAttribute(String dbValue) {
		return dbValue == null ? null : SessionStatus.valueOf(dbValue.toUpperCase());
	}
}
