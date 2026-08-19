package com.cookpilot.backend.review;

/**
 * 탈퇴 시 사진 객체 삭제 실패. 사진이 남은 채 계정 행만 지워지면 안 되므로
 * (방침 제4조 — 사진 파일까지 함께 삭제) 삼키지 않고 500 으로 올려 재시도를 유도한다.
 * 삭제가 S3 → DB 순서라, 여기서 실패하면 계정은 그대로 남아 다시 시도할 수 있다.
 */
public class PhotoDeletionFailedException extends RuntimeException {

	public PhotoDeletionFailedException(String message, Throwable cause) {
		super(message, cause);
	}
}
