package com.cookpilot.backend.ai;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 폐쇄 베타용 사용자별 고정 1분 창 호출 제한.
 *
 * 다중 인스턴스로 확장하면 Redis 같은 공유 저장소로 교체해야 하지만, 현재 단일 VPS
 * 배포에서는 DB 변경 없이 무료 API 쿼터를 보호한다.
 */
@Component
class AiFeedbackRateLimiter {

	private static final long WINDOW_SECONDS = 60;

	private final int requestsPerMinute;
	private final Clock clock;
	private final ConcurrentHashMap<UUID, RequestWindow> windows = new ConcurrentHashMap<>();

	@Autowired
	AiFeedbackRateLimiter(AiFeedbackRateLimitProperties properties) {
		this(properties, Clock.systemUTC());
	}

	AiFeedbackRateLimiter(AiFeedbackRateLimitProperties properties, Clock clock) {
		this.requestsPerMinute = properties.requestsPerMinute();
		this.clock = clock;
	}

	void acquire(UUID userId) {
		long windowNumber = clock.instant().getEpochSecond() / WINDOW_SECONDS;
		RequestWindow result = windows.compute(userId, (ignored, current) -> {
			if (current == null || current.windowNumber() != windowNumber) {
				return new RequestWindow(windowNumber, 1);
			}
			return new RequestWindow(windowNumber, current.count() + 1);
		});
		if (result.count() > requestsPerMinute) {
			throw new AiFeedbackRateLimitExceededException(
					"AI 조리 도움 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
		}

		// 장시간 실행 시 이미 만료된 사용자 창이 무한히 쌓이지 않게 간헐적으로 정리한다.
		if (windows.size() > 1_000) {
			windows.entrySet().removeIf(
					entry -> entry.getValue().windowNumber() < windowNumber - 1);
		}
	}

	private record RequestWindow(long windowNumber, int count) {
	}
}
