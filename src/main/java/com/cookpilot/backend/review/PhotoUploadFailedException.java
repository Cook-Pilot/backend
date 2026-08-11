package com.cookpilot.backend.review;

/**
 * 사진을 저장소에 올리지 못했다. 클라이언트 잘못이 아니라 저장소·네트워크 장애이므로 500 이다.
 * (IllegalStateException 은 핸들러가 409 로 매핑해 의미가 어긋난다.)
 */
public class PhotoUploadFailedException extends RuntimeException {

	public PhotoUploadFailedException(String message, Throwable cause) {
		super(message, cause);
	}
}
