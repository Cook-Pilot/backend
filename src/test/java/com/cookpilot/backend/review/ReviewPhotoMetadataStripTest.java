package com.cookpilot.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * 업로드 사진의 메타데이터 제거 검증.
 *
 * 후기 사진에는 촬영 위치(GPS)와 촬영 기기가 EXIF 로 실려 온다. 개인정보처리방침이
 * "저장 전에 제거한다"고 약속하는 지점이라 픽셀만 남는지를 바이트 수준에서 확인한다.
 */
class ReviewPhotoMetadataStripTest {

	/** JPEG 에서 EXIF 가 실리는 세그먼트 마커. */
	private static final byte[] APP1_MARKER = { (byte) 0xFF, (byte) 0xE1 };

	@Test
	void jpeg의_exif_세그먼트가_제거된다() throws IOException {
		byte[] withExif = jpegWithExif();
		assertThat(indexOf(withExif, APP1_MARKER)).isNotEqualTo(-1);

		byte[] stripped = ReviewPhotoService.stripMetadata(withExif, "jpg");

		assertThat(indexOf(stripped, APP1_MARKER)).isEqualTo(-1);
		assertThat(indexOf(stripped, "35.1796".getBytes())).isEqualTo(-1);
	}

	@Test
	void 제거_후에도_같은_크기의_이미지로_디코딩된다() throws IOException {
		byte[] stripped = ReviewPhotoService.stripMetadata(jpegWithExif(), "jpg");

		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(stripped));
		assertThat(decoded).isNotNull();
		assertThat(decoded.getWidth()).isEqualTo(40);
		assertThat(decoded.getHeight()).isEqualTo(30);
	}

	@Test
	void png의_텍스트_청크가_제거된다() throws IOException {
		byte[] withComment = pngWithTextChunk();
		assertThat(indexOf(withComment, "GPSLatitude".getBytes())).isNotEqualTo(-1);

		byte[] stripped = ReviewPhotoService.stripMetadata(withComment, "png");

		assertThat(indexOf(stripped, "GPSLatitude".getBytes())).isEqualTo(-1);
		assertThat(ImageIO.read(new ByteArrayInputStream(stripped))).isNotNull();
	}

	@Test
	void 투명한_이미지를_jpeg로_신고해도_인코딩된다() throws IOException {
		// 알파를 눕히지 않으면 jpg 인코더가 "Bogus input colorspace" 로 죽어 500 이 나간다.
		byte[] transparent = encode(
				new BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB), "png");

		byte[] stripped = ReviewPhotoService.stripMetadata(transparent, "jpg");

		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(stripped));
		assertThat(decoded).isNotNull();
		assertThat(decoded.getWidth()).isEqualTo(40);
	}

	@Test
	void 이미지가_아니면_400으로_이어지는_예외() {
		assertThatThrownBy(() -> ReviewPhotoService.stripMetadata("not an image".getBytes(), "jpg"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * EXIF 를 직접 만들어 붙인다. JPEG 는 SOI(FFD8) 다음에 APP 세그먼트가 오므로
	 * 그 자리에 APP1 을 끼워 넣으면 디코더가 읽는 정상적인 EXIF JPEG 가 된다.
	 */
	private byte[] jpegWithExif() throws IOException {
		byte[] plain = encode(image(), "jpg");
		// "Exif\0\0" + 위경도로 읽힐 문자열. 실제 TIFF 구조가 아니어도 세그먼트로는 유효하고,
		// 여기서 확인하려는 것은 "이 바이트들이 살아남는가"다.
		byte[] payload = ("Exif\0\0" + "GPS 35.1796 129.0756 iPhone 15 Pro").getBytes();
		int length = payload.length + 2;

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(plain, 0, 2);
		out.write(APP1_MARKER);
		out.write((length >> 8) & 0xFF);
		out.write(length & 0xFF);
		out.write(payload);
		out.write(plain, 2, plain.length - 2);
		return out.toByteArray();
	}

	/** PNG 는 tEXt 청크에 메타데이터가 들어간다. */
	private byte[] pngWithTextChunk() throws IOException {
		byte[] plain = encode(image(), "png");
		byte[] chunk = textChunk("GPSLatitude", "35.1796");

		// 첫 청크(IHDR) 뒤에 끼워 넣는다. 8바이트 시그니처 + IHDR(4+4+13+4=25).
		int insertAt = 8 + 25;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(plain, 0, insertAt);
		out.write(chunk);
		out.write(plain, insertAt, plain.length - insertAt);
		return out.toByteArray();
	}

	private byte[] textChunk(String keyword, String value) throws IOException {
		byte[] data = (keyword + "\0" + value).getBytes();
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write("tEXt".getBytes());
		body.write(data);
		byte[] typeAndData = body.toByteArray();

		java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		crc.update(typeAndData);
		long checksum = crc.getValue();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write((data.length >> 24) & 0xFF);
		out.write((data.length >> 16) & 0xFF);
		out.write((data.length >> 8) & 0xFF);
		out.write(data.length & 0xFF);
		out.write(typeAndData);
		out.write((int) ((checksum >> 24) & 0xFF));
		out.write((int) ((checksum >> 16) & 0xFF));
		out.write((int) ((checksum >> 8) & 0xFF));
		out.write((int) (checksum & 0xFF));
		return out.toByteArray();
	}

	private BufferedImage image() {
		BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(new Color(200, 120, 60));
		graphics.fillRect(0, 0, 40, 30);
		graphics.dispose();
		return image;
	}

	private byte[] encode(BufferedImage image, String format) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	private int indexOf(byte[] haystack, byte[] needle) {
		outer:
		for (int i = 0; i <= haystack.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (haystack[i + j] != needle[j]) {
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}

}
