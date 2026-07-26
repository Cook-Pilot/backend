package com.cookpilot.backend;

import java.util.UUID;

/** Flyway V2 데모 seed를 테스트에서 참조하기 위한 고정 ID. */
public final class TestRecipeIds {

	public static final UUID RAMEN_RECIPE_ID =
			UUID.fromString("10000000-0000-0000-0000-000000000001");
	public static final UUID FRIED_RICE_RECIPE_ID =
			UUID.fromString("10000000-0000-0000-0000-000000000002");
	public static final UUID BRAISED_TOFU_RECIPE_ID =
			UUID.fromString("10000000-0000-0000-0000-000000000003");
	public static final UUID EGG_FRIED_RICE_RECIPE_ID =
			UUID.fromString("10000000-0000-0000-0000-000000000005");

	private TestRecipeIds() {
	}
}
