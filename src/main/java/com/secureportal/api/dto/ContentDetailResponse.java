package com.secureportal.api.dto;

/** A content item plus a freshly minted, session-bound ticket for fetching its bytes. */
public record ContentDetailResponse(ContentItemDto item, String ticket) {
}
