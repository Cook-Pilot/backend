package com.cookpilot.backend.review;

import java.util.Objects;

/**
 * Dart {@code String.trim()}과 동일한 Unicode 코드포인트만 양끝에서 제거한다.
 */
final class DartStringTrim {

	private DartStringTrim() {
	}

	static String trim(String value) {
		Objects.requireNonNull(value, "value");
		int start = 0;
		int end = value.length();
		while (start < end) {
			int codePoint = value.codePointAt(start);
			if (!isTrimCodePoint(codePoint)) {
				break;
			}
			start += Character.charCount(codePoint);
		}
		while (start < end) {
			int codePoint = value.codePointBefore(end);
			if (!isTrimCodePoint(codePoint)) {
				break;
			}
			end -= Character.charCount(codePoint);
		}
		return start == 0 && end == value.length()
				? value
				: value.substring(start, end);
	}

	private static boolean isTrimCodePoint(int codePoint) {
		return codePoint >= 0x0009 && codePoint <= 0x000D
				|| codePoint == 0x0020
				|| codePoint == 0x0085
				|| codePoint == 0x00A0
				|| codePoint == 0x1680
				|| codePoint >= 0x2000 && codePoint <= 0x200A
				|| codePoint == 0x2028
				|| codePoint == 0x2029
				|| codePoint == 0x202F
				|| codePoint == 0x205F
				|| codePoint == 0x3000
				|| codePoint == 0xFEFF;
	}
}
