package com.cookpilot.backend.review;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 리뷰 사진 업로드용 S3 클라이언트와 열람 URL 서명기.
 *
 * 자격증명은 명시하지 않는다 — EC2 인스턴스 역할을 default provider chain 이 자동으로 집는다.
 * {@code @Lazy} 라서 버킷이 설정된(=실제 업로드를 하는) 환경에서만 만들어진다.
 */
@Configuration(proxyBeanMethods = false)
public class ReviewPhotoStorageConfig {

	@Bean
	@Lazy
	S3Client reviewPhotoS3Client(@Value("${cookpilot.photos.region}") String region) {
		return S3Client.builder().region(Region.of(region)).build();
	}

	@Bean
	@Lazy
	S3Presigner reviewPhotoS3Presigner(@Value("${cookpilot.photos.region}") String region) {
		return S3Presigner.builder().region(Region.of(region)).build();
	}
}
