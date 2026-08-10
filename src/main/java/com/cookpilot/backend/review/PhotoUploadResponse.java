package com.cookpilot.backend.review;

/** 리뷰 사진 업로드 응답. url 을 SubmitReviewRequest.photoUrls 에 그대로 넣는다. */
public record PhotoUploadResponse(String url) {
}
