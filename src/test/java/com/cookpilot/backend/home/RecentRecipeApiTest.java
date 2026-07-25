package com.cookpilot.backend.home;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.TestRecipeIds;
import com.cookpilot.backend.review.PostCookReviewRepository;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecentRecipeApiTest extends PostgresApiTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PostCookReviewRepository postCookReviewRepository;

	@BeforeEach
	void clearReviews() {
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
		mockMvc.perform(post("/api/v1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "recipeId": "%s",
								  "rating": 5,
								  "comment": "다시 만들고 싶다"
								}
								""".formatted(TestRecipeIds.BRAISED_TOFU_RECIPE_ID)))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/home/recent-recipes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(TestRecipeIds.BRAISED_TOFU_RECIPE_ID.toString()))
				.andExpect(jsonPath("$[0].lastCookedAt").exists())
				.andExpect(jsonPath("$[0].lastRating").value(5))
				.andExpect(jsonPath("$[0].hasPersonalVersion").value(true));
	}
}
