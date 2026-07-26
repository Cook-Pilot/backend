package com.cookpilot.backend.home;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.TestRecipeIds;
import com.cookpilot.backend.favorite.RecipeFavoriteRepository;
import com.cookpilot.backend.recipe.RecipeEntity;
import com.cookpilot.backend.recipe.RecipeRepository;
import com.cookpilot.backend.review.PostCookReviewEntity;
import com.cookpilot.backend.review.PostCookReviewRepository;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecentRecipeApiTest extends PostgresApiTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PostCookReviewRepository postCookReviewRepository;

	@Autowired
	private RecipeRepository recipeRepository;

	@Autowired
	private RecipeFavoriteRepository recipeFavoriteRepository;

	@BeforeEach
	void clearReviews() {
		recipeFavoriteRepository.deleteAll();
		postCookReviewRepository.deleteAll();
	}

	@Test
	void 신규_사용자의_최근_조리는_빈_목록이다() throws Exception {
		mockMvc.perform(get("/api/v1/home/recent-recipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void 조리_후기를_남기면_최근_조리에_표시된다() throws Exception {
		mockMvc.perform(put("/api/v1/recipes/"
						+ TestRecipeIds.BRAISED_TOFU_RECIPE_ID + "/favorite"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "clientSessionId": "%s",
								  "recipeId": "%s",
								  "rating": 5,
								  "comment": "다시 만들고 싶다"
								}
								""".formatted(UUID.randomUUID(), TestRecipeIds.BRAISED_TOFU_RECIPE_ID)))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/home/recent-recipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(TestRecipeIds.BRAISED_TOFU_RECIPE_ID.toString()))
				.andExpect(jsonPath("$[0].lastCookedAt").exists())
				.andExpect(jsonPath("$[0].lastRating").value(5))
				.andExpect(jsonPath("$[0].hasPersonalVersion").value(false))
				.andExpect(jsonPath("$[0].favorite").value(true));
	}

	@Test
	void 비활성_레시피는_최근_조리에서_제외한다() throws Exception {
		RecipeEntity inactive = new RecipeEntity(
				"비활성 레시피", "최근 조리에 노출되면 안 됨", BigDecimal.ONE);
		inactive.setStatus("inactive");
		inactive = recipeRepository.save(inactive);
		postCookReviewRepository.save(new PostCookReviewEntity(
				UUID.fromString("00000000-0000-0000-0000-000000000001"),
				inactive.getId(),
				5,
				"과거 조리",
				null));

		mockMvc.perform(get("/api/v1/home/recent-recipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void 최근_조리는_DB에서_최대_10개로_제한한다() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		for (int index = 0; index < 11; index++) {
			RecipeEntity recipe = recipeRepository.save(new RecipeEntity(
					"최근 조리 제한 " + index,
					"DB 제한 검증",
					BigDecimal.ONE));
			postCookReviewRepository.save(new PostCookReviewEntity(
					userId,
					recipe.getId(),
					5,
					"조리 완료",
					null));
		}

		mockMvc.perform(get("/api/v1/home/recent-recipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(10)));
	}
}
