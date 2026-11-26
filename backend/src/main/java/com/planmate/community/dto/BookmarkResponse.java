package com.planmate.community.dto;

/**
 * PUB-06-01 북마크 저장·해제 결과 (contracts/community.yaml BookmarkResponse).
 */
public record BookmarkResponse(
        boolean bookmarked) { // 저장 후 true, 해제 후 false
}
