package com.cookpilot.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DartStringTrimTest {

	private static final String DART_TRIM_CODE_POINTS =
			"com.cookpilot.backend.review."
					+ "DartStringTrimTestCases#dartTrimCodePoints";
	private static final String PRESERVED_CONTROL_CODE_POINTS =
			"com.cookpilot.backend.review."
					+ "DartStringTrimTestCases#preservedControlCodePoints";

	@ParameterizedTest
	@MethodSource(DART_TRIM_CODE_POINTS)
	void Dart_trim의_26개_문자만_양끝에서_제거하고_내부는_보존한다(
			int codePoint) {
		String character = DartStringTrimTestCases.character(codePoint);

		assertThat(DartStringTrim.trim(
				character + "가운데" + character))
				.isEqualTo("가운데");
		assertThat(DartStringTrim.trim(
				"왼쪽" + character + "오른쪽"))
				.isEqualTo("왼쪽" + character + "오른쪽");
	}

	@ParameterizedTest
	@MethodSource(PRESERVED_CONTROL_CODE_POINTS)
	void Dart_trim_대상이_아닌_U001C부터_U001F는_양끝에서도_보존한다(
			int codePoint) {
		String character = DartStringTrimTestCases.character(codePoint);
		String value = character + "가운데" + character;

		assertThat(DartStringTrim.trim(value)).isEqualTo(value);
	}

	@Test
	void 유효한_보충문자와_null_계약을_바꾸지_않는다() {
		assertThat(DartStringTrim.trim("🍚밥🍚"))
				.isEqualTo("🍚밥🍚");
		assertThatThrownBy(() -> DartStringTrim.trim(null))
				.isInstanceOf(NullPointerException.class);
	}
}
