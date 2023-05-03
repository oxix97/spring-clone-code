package com.example.noticeboard.dto;

import com.example.noticeboard.domain.UserAccount;

import java.time.LocalDateTime;

/**
 * A DTO for the {@link com.example.noticeboard.domain.UserAccount} entity
 */
public record UserAccountDto(
        String userId,
        String userPassword,
        String email,
        String nickname,
        String memo,
        LocalDateTime createdAt,
        String createdBy,
        LocalDateTime modifiedAt,
        String modifiedBy
) {
    public static UserAccountDto of(
            String userId,
            String userPassword,
            String email,
            String nickname,
            String memo,
            LocalDateTime createdAt,
            String createdBy,
            LocalDateTime modifiedAt,
            String modifiedBy
    ) {
        return new UserAccountDto(userId, userPassword, email, nickname, memo, createdAt, createdBy, modifiedAt, modifiedBy);
    }

    public static UserAccountDto of(
            String userId,
            String userPassword,
            String email,
            String nickname,
            String memo
    ) {
        return new UserAccountDto(userId, userPassword, email, nickname, memo, null, null, null, null);
    }

    public UserAccount toEntity(UserAccountDto dto) {
        return UserAccount.of(
                dto.userId,
                dto.userPassword,
                dto.email,
                dto.nickname,
                dto.memo,
                dto.createdBy
        );
    }
}