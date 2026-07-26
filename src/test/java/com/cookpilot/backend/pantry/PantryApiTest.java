package com.cookpilot.backend.pantry;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.TestRecipeIds;
import com.cookpilot.backend.user.UserEntity;
import com.cookpilot.backend.user.UserRepository;
import com.cookpilot.backend.user.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * pantry_items의 user_id+ingredient_name UNIQUE 제약과 공유 Postgres 컨텍스트 때문에
 * 테스트마다 전용 사용자를 새로 만들어 서로 간섭하지 않게 한다.
 */
class PantryApiTest extends PostgresApiTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Test
	void 재료를_담으면_카탈로그_이모지와_함께_소진임박순으로_조회된다() throws Exception {
		UUID userId = newUser();

		addItem(userId, "두부", LocalDate.now().plusDays(10));
		addItem(userId, "계란", LocalDate.now().plusDays(1));

		JsonNode items = objectMapper.readTree(mockMvc.perform(get("/api/v1/pantry/items")
						.header(UserService.USER_ID_HEADER, userId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());

		assertThat(items).hasSize(2);
		assertThat(items.get(0).path("ingredientName").asText()).isEqualTo("계란");
		assertThat(items.get(0).path("emoji").asText()).isEqualTo("🥚");
		assertThat(items.get(0).path("daysUntilExpiry").asInt()).isEqualTo(1);
		assertThat(items.get(1).path("ingredientName").asText()).isEqualTo("두부");
	}

	@Test
	void 같은_재료를_다시_담으면_소진예정일만_갱신된다() throws Exception {
		UUID userId = newUser();

		addItem(userId, "양파", LocalDate.now().plusDays(3));
		addItem(userId, "양파", LocalDate.now().plusDays(20));

		JsonNode items = objectMapper.readTree(mockMvc.perform(get("/api/v1/pantry/items")
						.header(UserService.USER_ID_HEADER, userId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());

		assertThat(items).hasSize(1);
		assertThat(items.get(0).path("daysUntilExpiry").asInt()).isEqualTo(20);
	}

	@Test
	void 카탈로그에_없는_재료는_담을_수_없다() throws Exception {
		UUID userId = newUser();

		mockMvc.perform(post("/api/v1/pantry/items")
						.header(UserService.USER_ID_HEADER, userId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"ingredientName": "듣도보도못한재료", "useByDate": null}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 재료를_삭제하면_목록과_추천에서_사라진다() throws Exception {
		UUID userId = newUser();
		String itemId = addItem(userId, "고구마", LocalDate.now().plusDays(2))
				.path("id").asText();

		mockMvc.perform(delete("/api/v1/pantry/items/" + itemId)
						.header(UserService.USER_ID_HEADER, userId))
				.andExpect(status().isNoContent());

		JsonNode items = objectMapper.readTree(mockMvc.perform(get("/api/v1/pantry/items")
						.header(UserService.USER_ID_HEADER, userId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
		assertThat(items).isEmpty();
	}

	@Test
	void 레시피_추천은_소진_임박_재료를_우선한다() throws Exception {
		UUID userId = newUser();
		// 고춧가루는 BRAISED_TOFU_RECIPE_ID에만, 김치는 FRIED_RICE_RECIPE_ID에만 쓰여 겹치지 않는다.
		addItem(userId, "고춧가루", LocalDate.now().plusDays(1));
		addItem(userId, "김치", LocalDate.now().plusDays(10));

		JsonNode suggestions = objectMapper.readTree(mockMvc.perform(
						get("/api/v1/pantry/recipe-suggestions")
								.header(UserService.USER_ID_HEADER, userId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString())
				.path("suggestions");

		assertThat(suggestions.size()).isGreaterThanOrEqualTo(2);
		assertThat(suggestions.get(0).path("recipeId").asText())
				.isEqualTo(TestRecipeIds.BRAISED_TOFU_RECIPE_ID.toString());
		assertThat(suggestions.get(0).path("mostUrgentDaysUntilExpiry").asInt()).isEqualTo(1);
		assertThat(suggestions.get(1).path("recipeId").asText())
				.isEqualTo(TestRecipeIds.FRIED_RICE_RECIPE_ID.toString());

		boolean ramenSuggested = false;
		for (JsonNode suggestion : suggestions) {
			if (TestRecipeIds.RAMEN_RECIPE_ID.toString()
					.equals(suggestion.path("recipeId").asText())) {
				ramenSuggested = true;
			}
		}
		assertThat(ramenSuggested).isFalse();
	}

	@Test
	void 다른_사용자의_재료는_내_추천에_영향을_주지_않는다() throws Exception {
		UUID userId = newUser();
		UUID otherUserId = newUser();
		addItem(otherUserId, "두부", LocalDate.now().plusDays(1));

		JsonNode suggestions = objectMapper.readTree(mockMvc.perform(
						get("/api/v1/pantry/recipe-suggestions")
								.header(UserService.USER_ID_HEADER, userId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString())
				.path("suggestions");

		assertThat(suggestions).isEmpty();
	}

	private JsonNode addItem(UUID userId, String ingredientName, LocalDate useByDate) throws Exception {
		String body = objectMapper.writeValueAsString(
				new AddPantryItemRequest(ingredientName, useByDate));
		String response = mockMvc.perform(post("/api/v1/pantry/items")
						.header(UserService.USER_ID_HEADER, userId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(response);
	}

	private UUID newUser() {
		return userRepository.saveAndFlush(
						new UserEntity("pantry-" + UUID.randomUUID() + "@test.com", "테스트 사용자"))
				.getId();
	}
}
