package com.cookpilot.backend.recommendation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.cookpilot.backend.PostgresApiTestBase;
import com.cookpilot.backend.TestRecipeIds;
import com.cookpilot.backend.personalrecipe.AdjustmentType;
import com.cookpilot.backend.personalrecipe.PersonalIngredientAdjustmentEntity;
import com.cookpilot.backend.personalrecipe.PersonalIngredientAdjustmentRepository;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersionEntity;
import com.cookpilot.backend.personalrecipe.PersonalRecipeVersionRepository;
import com.cookpilot.backend.review.PostCookReviewEntity;
import com.cookpilot.backend.review.PostCookReviewRepository;
import com.cookpilot.backend.user.UserEntity;
import com.cookpilot.backend.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationApiTest extends PostgresApiTestBase {

	private static final UUID DEMO_USER_ID =
			UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID TOFU_SOY_SAUCE_ID =
			UUID.fromString("20000000-0000-0000-0000-000000000302");
	private static final UUID EGG_RICE_SOY_SAUCE_ID =
			UUID.fromString("20000000-0000-0000-0000-000000000504");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PostCookReviewRepository reviewRepository;

	@Autowired
	private PersonalRecipeVersionRepository versionRepository;

	@Autowired
	private PersonalIngredientAdjustmentRepository adjustmentRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void 비슷한_요리의_반복_변경으로_추천하고_피드백을_멱등_저장한다() throws Exception {
		PersonalRecipeVersionEntity savedVersion =
				savePositiveSoySauceEvidence(new BigDecimal("1.50"), 5, 1);
		savePositiveReuseEvidence(savedVersion, 4, 2);

		String responseBody = mockMvc.perform(get(
						"/api/v1/recipes/" + TestRecipeIds.EGG_FRIED_RICE_RECIPE_ID
								+ "/next-cook-recommendations"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		JsonNode body = objectMapper.readTree(responseBody);
		JsonNode recommendation = findRecommendation(
				body.path("recommendations"), EGG_RICE_SOY_SAUCE_ID.toString());

		assertThat(recommendation.path("ingredientName").asText()).isEqualTo("진간장");
		assertThat(recommendation.path("suggestedAmount").decimalValue())
				.isLessThan(new BigDecimal("0.5"));
		assertThat(recommendation.path("explanationSource").asText())
				.isEqualTo("FALLBACK");
		assertThat(recommendation.path("reason").asText())
				.contains("2회", "진간장");
		assertThat(recommendation.path("evidence").size())
				.isGreaterThanOrEqualTo(2);

		String recommendationId =
				recommendation.path("recommendationId").asText();
		String feedbackJson = """
				{
				  "originalIngredientId": "%s",
				  "decision": "ACCEPTED",
				  "originalAmount": %s,
				  "suggestedAmount": %s,
				  "appliedAmount": %s,
				  "unit": "%s",
				  "reason": %s,
				  "explanationSource": "%s",
				  "model": null,
				  "promptVersion": "%s",
				  "evidenceReviewIds": %s
				}
				""".formatted(
				recommendation.path("originalIngredientId").asText(),
				recommendation.path("originalAmount").asText(),
				recommendation.path("suggestedAmount").asText(),
				recommendation.path("suggestedAmount").asText(),
				recommendation.path("unit").asText(),
				objectMapper.writeValueAsString(recommendation.path("reason").asText()),
				recommendation.path("explanationSource").asText(),
				recommendation.path("promptVersion").asText(),
				objectMapper.writeValueAsString(
						recommendation.path("evidence").valueStream()
								.map(item -> item.path("reviewId").asText())
								.toList()));

		String first = mockMvc.perform(put(
						"/api/v1/recipes/" + TestRecipeIds.EGG_FRIED_RICE_RECIPE_ID
								+ "/recommendation-feedback/" + recommendationId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(feedbackJson))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String retried = mockMvc.perform(put(
						"/api/v1/recipes/" + TestRecipeIds.EGG_FRIED_RICE_RECIPE_ID
								+ "/recommendation-feedback/" + recommendationId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(feedbackJson))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(objectMapper.readTree(retried).path("id").asText())
				.isEqualTo(objectMapper.readTree(first).path("id").asText());

		JsonNode refreshed = objectMapper.readTree(mockMvc.perform(get(
						"/api/v1/recipes/" + TestRecipeIds.EGG_FRIED_RICE_RECIPE_ID
								+ "/next-cook-recommendations"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
		JsonNode rejectedRecommendation = findRecommendation(
				refreshed.path("recommendations"), EGG_RICE_SOY_SAUCE_ID.toString());
		String rejectedId = rejectedRecommendation.path("recommendationId").asText();
		String rejectJson = """
				{
				  "originalIngredientId": "%s",
				  "decision": "REJECTED",
				  "originalAmount": %s,
				  "suggestedAmount": %s,
				  "appliedAmount": null,
				  "unit": "%s",
				  "reason": %s,
				  "explanationSource": "%s",
				  "model": null,
				  "promptVersion": "%s",
				  "evidenceReviewIds": %s
				}
				""".formatted(
				rejectedRecommendation.path("originalIngredientId").asText(),
				rejectedRecommendation.path("originalAmount").asText(),
				rejectedRecommendation.path("suggestedAmount").asText(),
				rejectedRecommendation.path("unit").asText(),
				objectMapper.writeValueAsString(
						rejectedRecommendation.path("reason").asText()),
				rejectedRecommendation.path("explanationSource").asText(),
				rejectedRecommendation.path("promptVersion").asText(),
				objectMapper.writeValueAsString(
						rejectedRecommendation.path("evidence").valueStream()
								.map(item -> item.path("reviewId").asText())
								.toList()));
		mockMvc.perform(put(
						"/api/v1/recipes/" + TestRecipeIds.EGG_FRIED_RICE_RECIPE_ID
								+ "/recommendation-feedback/" + rejectedId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(rejectJson))
				.andExpect(status().isOk());

		JsonNode afterRejection = objectMapper.readTree(mockMvc.perform(get(
						"/api/v1/recipes/" + TestRecipeIds.EGG_FRIED_RICE_RECIPE_ID
								+ "/next-cook-recommendations"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
		assertThat(hasRecommendation(
				afterRejection.path("recommendations"),
				EGG_RICE_SOY_SAUCE_ID.toString())).isFalse();
	}

	@Test
	void 다른_사용자의_조리_기록은_추천_근거로_사용하지_않는다() throws Exception {
		savePositiveSoySauceEvidence(new BigDecimal("1.50"), 5, 3);
		savePositiveSoySauceEvidence(new BigDecimal("1.60"), 5, 4);
		UUID otherUserId = userRepository.saveAndFlush(
				new UserEntity(
						"recommendation-other-" + UUID.randomUUID() + "@test.com",
						"다른 사용자"))
				.getId();

		JsonNode response = objectMapper.readTree(mockMvc.perform(get(
						"/api/v1/recipes/" + TestRecipeIds.EGG_FRIED_RICE_RECIPE_ID
								+ "/next-cook-recommendations")
						.header(HttpHeaders.AUTHORIZATION, bearerFor(otherUserId)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());

		assertThat(response.path("recommendations").isEmpty()).isTrue();
	}

	private PersonalRecipeVersionEntity savePositiveSoySauceEvidence(
			BigDecimal amount, int rating, int sequence) {
		PostCookReviewEntity review = reviewRepository.saveAndFlush(
				new PostCookReviewEntity(
						DEMO_USER_ID,
						TestRecipeIds.BRAISED_TOFU_RECIPE_ID,
						UUID.randomUUID(),
						Instant.parse("2026-07-%02dT10:00:00Z".formatted(10 + sequence)),
						null,
						new BigDecimal("2"),
						rating,
						"맛있어요",
						null));
		int versionNumber = versionRepository
				.findByUserIdAndRecipeIdOrderByVersionNumberDesc(
						DEMO_USER_ID, TestRecipeIds.BRAISED_TOFU_RECIPE_ID)
				.stream()
				.findFirst()
				.map(version -> version.getVersionNumber() + 1)
				.orElse(1);
		PersonalRecipeVersionEntity version = versionRepository.saveAndFlush(
				new PersonalRecipeVersionEntity(
						DEMO_USER_ID,
						TestRecipeIds.BRAISED_TOFU_RECIPE_ID,
						versionNumber,
						"두부조림 v" + versionNumber,
						"진간장 조절",
						review.getId()));
		adjustmentRepository.saveAndFlush(
				new PersonalIngredientAdjustmentEntity(
						version.getId(),
						TOFU_SOY_SAUCE_ID,
						AdjustmentType.MODIFY,
						null,
						amount,
						null,
						null,
						1));
		return version;
	}

	private void savePositiveReuseEvidence(
			PersonalRecipeVersionEntity sourceVersion,
			int rating,
			int sequence) {
		reviewRepository.saveAndFlush(
				new PostCookReviewEntity(
						DEMO_USER_ID,
						TestRecipeIds.BRAISED_TOFU_RECIPE_ID,
						UUID.randomUUID(),
						Instant.parse("2026-07-%02dT10:00:00Z".formatted(10 + sequence)),
						sourceVersion.getId(),
						new BigDecimal("2"),
						rating,
						"같은 버전으로 다시 맛있게 먹었어요",
						null));
	}

	private JsonNode findRecommendation(JsonNode recommendations, String ingredientId) {
		for (JsonNode recommendation : recommendations) {
			if (ingredientId.equals(
					recommendation.path("originalIngredientId").asText())) {
				return recommendation;
			}
		}
		throw new AssertionError("진간장 추천을 찾지 못했습니다: " + recommendations);
	}

	private boolean hasRecommendation(JsonNode recommendations, String ingredientId) {
		for (JsonNode recommendation : recommendations) {
			if (ingredientId.equals(
					recommendation.path("originalIngredientId").asText())) {
				return true;
			}
		}
		return false;
	}
}
