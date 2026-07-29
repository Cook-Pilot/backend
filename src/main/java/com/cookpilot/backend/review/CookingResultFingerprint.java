package com.cookpilot.backend.review;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * JSON 직렬화 방식과 무관한 versioned, length-framed SHA-256 projection.
 */
final class CookingResultFingerprint {

	static final short SCHEMA_VERSION = 1;
	private static final byte[] DOMAIN = {'C', 'P', 'C', 'R'};
	private static final Pattern LOWERCASE_SHA256 =
			Pattern.compile("[0-9a-f]{64}");

	private CookingResultFingerprint() {
	}

	static String sha256(CookingResultPayload payload) {
		return sha256(SCHEMA_VERSION, payload);
	}

	static String sha256(short schemaVersion, CookingResultPayload payload) {
		if (schemaVersion != SCHEMA_VERSION) {
			throw new IllegalArgumentException(
					"지원하지 않는 조리 완료 payload schema version입니다: "
							+ schemaVersion);
		}
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes)) {
				output.write(DOMAIN);
				output.writeShort(schemaVersion);
				writeUuid(output, payload.recipeId());
				output.writeLong(payload.cookedAt().getEpochSecond());
				output.writeInt(payload.cookedAt().getNano() / 1_000);
				writeUuid(output, payload.sourcePersonalVersionId());
				writeText(output, payload.targetServings().toPlainString());

				output.writeInt(payload.ingredients().size());
				for (CookingResultPayload.IngredientSnapshot ingredient
						: payload.ingredients()) {
					writeUuid(output, ingredient.originalIngredientId());
					writeText(output, ingredient.name());
					writeText(output, decimal(ingredient.amount()));
					writeText(output, ingredient.unit());
					output.writeBoolean(ingredient.required());
					output.writeBoolean(ingredient.omitted());
					output.writeInt(ingredient.sortOrder());
				}

				output.writeInt(payload.steps().size());
				for (CookingResultPayload.StepSnapshot step : payload.steps()) {
					writeUuid(output, step.originalStepId());
					writeText(output, step.instruction());
					writeInteger(output, step.timerSeconds());
					writeText(output, step.cautionNote());
					output.writeInt(step.sortOrder());
				}
			}
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(bytes.toByteArray()));
		} catch (IOException | NoSuchAlgorithmException exception) {
			throw new IllegalStateException(
					"조리 완료 결과 지문을 만들 수 없습니다.", exception);
		}
	}

	static boolean matches(String left, String right) {
		if (left == null
				|| right == null
				|| !LOWERCASE_SHA256.matcher(left).matches()
				|| !LOWERCASE_SHA256.matcher(right).matches()) {
			return false;
		}
		return MessageDigest.isEqual(
				HexFormat.of().parseHex(left),
				HexFormat.of().parseHex(right));
	}

	private static String decimal(java.math.BigDecimal value) {
		return value == null ? null : value.toPlainString();
	}

	private static void writeUuid(DataOutputStream output, UUID value)
			throws IOException {
		if (value == null) {
			output.writeByte(0);
			return;
		}
		output.writeByte(1);
		output.writeLong(value.getMostSignificantBits());
		output.writeLong(value.getLeastSignificantBits());
	}

	private static void writeText(DataOutputStream output, String value)
			throws IOException {
		if (value == null) {
			output.writeByte(0);
			return;
		}
		output.writeByte(1);
		byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
		output.writeInt(encoded.length);
		output.write(encoded);
	}

	private static void writeInteger(DataOutputStream output, Integer value)
			throws IOException {
		if (value == null) {
			output.writeByte(0);
			return;
		}
		output.writeByte(1);
		output.writeInt(value);
	}
}
