package com.cookpilot.backend.review;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.cookpilot.backend.user.UserService;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * 리뷰 사진 업로드. 클라이언트가 받은 참조를 {@code POST /reviews} 의 photoUrls 에 넣는다.
 *
 * 여러 장은 이 업로드를 장수만큼 반복한다 — 장당 한 요청이라 실패한 장만 재시도하면 된다.
 *
 * 다른 개인화 API 와 동일하게 사용자 세션을 요구한다. 업로드는 과금 지점이라
 * 무인증 호출을 막는다.
 *
 * 버킷이 설정되지 않으면 저장 없이 목 URL 을 돌려준다 — 로컬 개발은 AWS 자격증명 없이
 * 그대로 돌아간다(Gemini 키 미설정 시 폴백과 같은 방식).
 *
 * 저장하는 값은 공개 URL 이 아니라 <b>버킷 키</b>다. 사진은 본인만 볼 수 있어야 하는데
 * 공개 URL 은 한 번 새면 영구히 유효하다. 조회 시점에 {@link #presign} 이 짧은 수명의
 * 서명 URL 로 바꿔 돌려준다.
 */
@Service
public class ReviewPhotoService {

	static final String MOCK_URL_PREFIX = "https://mock-storage.cookpilot.local/review-photos/";

	static final String KEY_PREFIX = "review-photos/";

	/** 열람 URL 수명. 조리 기록 화면을 넘겨보는 동안은 유효하고, 새면 곧 만료된다. */
	static final Duration PRESIGN_TTL = Duration.ofMinutes(15);

	/**
	 * 허용 이미지 형식과 저장 확장자. content-type 은 클라이언트 신고값이라 위조할 수 있지만,
	 * 화이트리스트로 최소한 스크립트·실행파일이 이미지인 척 올라오는 건 막는다.
	 *
	 * webp·heic 는 뺐다 — 메타데이터 제거가 ImageIO 재인코딩에 기대는데 둘 다 ImageIO 가
	 * 읽지 못한다. 프론트 픽커가 촬영·선택 원본을 1600px·q80 JPEG 로 수렴시켜 보내므로
	 * 실사용 경로에는 영향이 없다.
	 */
	private static final Map<String, String> ALLOWED_TYPES = Map.of(
			"image/jpeg", "jpg",
			"image/png", "png");

	/** 재인코딩 품질. 프론트가 이미 q80 으로 줄여 보내므로 여기서 또 깎지 않는다. */
	private static final float JPEG_QUALITY = 0.9f;

	private final UserService userService;
	private final ObjectProvider<S3Client> s3ClientProvider;
	private final ObjectProvider<S3Presigner> s3PresignerProvider;
	private final String bucket;

	public ReviewPhotoService(
			UserService userService,
			ObjectProvider<S3Client> s3ClientProvider,
			ObjectProvider<S3Presigner> s3PresignerProvider,
			@Value("${cookpilot.photos.bucket:}") String bucket) {
		this.userService = userService;
		this.s3ClientProvider = s3ClientProvider;
		this.s3PresignerProvider = s3PresignerProvider;
		this.bucket = bucket;
	}

	public String upload(MultipartFile file) {
		UUID userId = userService.getCurrentUser().id();
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("파일이 비어 있습니다.");
		}
		String extension = ALLOWED_TYPES.get(file.getContentType());
		if (extension == null) {
			throw new IllegalArgumentException(
					"지원하지 않는 이미지 형식입니다: " + file.getContentType()
							+ " (jpeg, png 만 업로드할 수 있습니다)");
		}

		// 목 모드에서도 돌린다 — 결과는 버리지만 "디코딩되는 이미지인가" 검증이 여기서 끝나서
		// 로컬·CI 가 운영과 같은 거절 규칙으로 돈다.
		byte[] stripped = stripMetadata(readBytes(file), extension);
		// 키에 업로더를 박는다. 이래야 조회 시점에 "이 키가 이 사람 것인가"를
		// 별도 테이블 없이 판정할 수 있다(presign 참고).
		String key = KEY_PREFIX + userId + "/" + UUID.randomUUID() + "." + extension;
		if (!StringUtils.hasText(bucket)) {
			return MOCK_URL_PREFIX + key.substring(KEY_PREFIX.length());
		}
		putObject(stripped, key, file.getContentType());
		return key;
	}

	/**
	 * 저장된 참조를 열람 가능한 형태로 바꾼다. {@code ownerId} 의 키만 서명하고, 그 외
	 * (목 URL·남의 키·공개 URL 이 저장된 옛 리뷰)는 손대지 않고 그대로 통과시킨다.
	 */
	List<String> presign(UUID ownerId, List<String> storedRefs) {
		if (storedRefs.isEmpty() || !StringUtils.hasText(bucket)) {
			return List.copyOf(storedRefs);
		}
		String ownPrefix = KEY_PREFIX + ownerId + "/";
		return storedRefs.stream()
				.map(ref -> ref.startsWith(ownPrefix) ? presignGet(ref) : ref)
				.toList();
	}

	/**
	 * 탈퇴한 사용자의 사진 객체 전부 삭제(#79, 방침 제4조). #74 부터 키가
	 * {@code review-photos/{userId}/…} 라 리뷰 행을 읽지 않고 접두사로 일괄 삭제한다.
	 * #74 이전에 올라간 옛 공개 URL 행은 키를 역산할 수 없어 여기서 못 지운다 —
	 * 베타 데이터 정리(#52·#71 미결)와 함께 별도 판단.
	 *
	 * 실패는 {@link PhotoDeletionFailedException} 으로 올린다. 호출측이 S3 → DB 순서로
	 * 지우므로 여기서 실패하면 계정이 남아 있어 재시도가 된다. 부분 삭제 후 실패도
	 * 재시도로 수렴한다(지워진 키는 다음 목록에 안 나온다).
	 */
	public void deleteAllForUser(UUID userId) {
		if (!StringUtils.hasText(bucket)) {
			return;
		}
		String prefix = KEY_PREFIX + userId + "/";
		S3Client s3 = s3ClientProvider.getObject();
		try {
			String continuationToken = null;
			do {
				ListObjectsV2Response page = s3.listObjectsV2(ListObjectsV2Request.builder()
						.bucket(bucket)
						.prefix(prefix)
						.continuationToken(continuationToken)
						.build());
				if (!page.contents().isEmpty()) {
					List<ObjectIdentifier> keys = page.contents().stream()
							.map(object -> ObjectIdentifier.builder().key(object.key()).build())
							.toList();
					DeleteObjectsResponse deleted = s3.deleteObjects(DeleteObjectsRequest.builder()
							.bucket(bucket)
							.delete(Delete.builder().objects(keys).build())
							.build());
					if (deleted.hasErrors()) {
						// 일부만 지워진 채 조용히 성공하면 방침 위반이 잔존한다.
						throw new PhotoDeletionFailedException(
								"사진 일부를 삭제하지 못했습니다: " + deleted.errors().get(0).message(), null);
					}
				}
				continuationToken = page.nextContinuationToken();
			} while (continuationToken != null);
		} catch (SdkException exception) {
			throw new PhotoDeletionFailedException("사진 삭제에 실패했습니다.", exception);
		}
	}

	private String presignGet(String key) {
		GetObjectPresignRequest request = GetObjectPresignRequest.builder()
				.signatureDuration(PRESIGN_TTL)
				.getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
				.build();
		return s3PresignerProvider.getObject().presignGetObject(request).url().toString();
	}

	/**
	 * 디코드 후 재인코딩해 파일에 실린 메타데이터(EXIF GPS 좌표·촬영 기기·촬영 시각 등)를
	 * 통째로 떨어뜨린다. 픽셀만 남기는 방식이라 태그를 하나씩 지우는 것보다 확실하다.
	 */
	static byte[] stripMetadata(byte[] source, String extension) {
		BufferedImage image;
		try (InputStream in = new ByteArrayInputStream(source)) {
			image = ImageIO.read(in);
		} catch (IOException exception) {
			throw new PhotoUploadFailedException("사진을 읽지 못했습니다.", exception);
		}
		if (image == null) {
			// content-type 은 맞다고 신고했는데 디코더가 못 읽었다 = 이미지가 아니거나 깨졌다.
			throw new IllegalArgumentException("이미지를 읽을 수 없습니다. 파일이 손상되었을 수 있습니다.");
		}
		if ("jpg".equals(extension)) {
			image = flattenAlpha(image);
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
			Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(extension);
			if (!writers.hasNext()) {
				throw new PhotoUploadFailedException("이미지 인코더를 찾지 못했습니다: " + extension, null);
			}
			ImageWriter writer = writers.next();
			try {
				writer.setOutput(stream);
				writer.write(null, new IIOImage(image, null, null), jpegParam(writer, extension));
			} finally {
				writer.dispose();
			}
		} catch (IOException exception) {
			throw new PhotoUploadFailedException("사진을 변환하지 못했습니다.", exception);
		}
		return out.toByteArray();
	}

	private byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException exception) {
			throw new PhotoUploadFailedException("사진을 읽지 못했습니다.", exception);
		}
	}

	/**
	 * jpg 인코더는 알파 채널을 받지 못한다("Bogus input colorspace"). 알파가 있으면 흰 배경에
	 * 합성해 RGB 로 눕힌다 — content-type 을 jpeg 로 신고하고 실제로는 투명 png 를 보내는
	 * 경우가 여기로 온다. 없으면 클라이언트 잘못이 500 으로 나간다.
	 */
	private static BufferedImage flattenAlpha(BufferedImage image) {
		if (!image.getColorModel().hasAlpha()) {
			return image;
		}
		BufferedImage flattened = new BufferedImage(
				image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = flattened.createGraphics();
		try {
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, flattened.getWidth(), flattened.getHeight());
			graphics.drawImage(image, 0, 0, null);
		} finally {
			graphics.dispose();
		}
		return flattened;
	}

	/** png 는 무손실이라 기본값 그대로 두고, jpg 만 품질을 지정한다. */
	private static ImageWriteParam jpegParam(ImageWriter writer, String extension) {
		if (!"jpg".equals(extension)) {
			return null;
		}
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(JPEG_QUALITY);
		return param;
	}

	private void putObject(byte[] content, String key, String contentType) {
		PutObjectRequest request = PutObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.contentType(contentType)
				.build();
		try {
			s3ClientProvider.getObject().putObject(request, RequestBody.fromBytes(content));
		} catch (SdkException exception) {
			// SdkException 까지 잡는다 — 자격증명·권한·네트워크 실패가 그대로 새면 응답 형식이 제각각이 된다.
			throw new PhotoUploadFailedException("사진 업로드에 실패했습니다.", exception);
		}
	}

}
