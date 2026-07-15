package com.bridgework.auth.service;

import com.bridgework.admin.auth.entity.AdminAccount;
import com.bridgework.admin.auth.repository.AdminAccountRepository;
import com.bridgework.auth.config.BridgeWorkAuthProperties;
import com.bridgework.auth.dto.TokenPairResponseDto;
import com.bridgework.auth.entity.AppUser;
import com.bridgework.auth.entity.UserRole;
import com.bridgework.auth.entity.UserStatus;
import com.bridgework.auth.exception.InvalidRefreshTokenException;
import com.bridgework.auth.exception.RefreshTokenRotationInProgressException;
import com.bridgework.auth.repository.AppUserRepository;
import com.bridgework.auth.security.JwtTokenProvider;
import com.bridgework.auth.security.ParsedJwtToken;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

@Service
public class TokenSessionService {

    private final AppUserRepository appUserRepository;
    private final AdminAccountRepository adminAccountRepository;
    private final RefreshTokenStoreService refreshTokenStoreService;
    private final JwtTokenProvider jwtTokenProvider;
    private final BridgeWorkAuthProperties authProperties;

    public TokenSessionService(AppUserRepository appUserRepository,
                               AdminAccountRepository adminAccountRepository,
                               RefreshTokenStoreService refreshTokenStoreService,
                               JwtTokenProvider jwtTokenProvider,
                               BridgeWorkAuthProperties authProperties) {
        this.appUserRepository = appUserRepository;
        this.adminAccountRepository = adminAccountRepository;
        this.refreshTokenStoreService = refreshTokenStoreService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authProperties = authProperties;
    }

    public TokenPairResponseDto issue(Long userId, UserRole role) {
        JwtTokenPair tokenPair = jwtTokenProvider.issueTokenPair(userId, role);
        refreshTokenStoreService.save(
                userId,
                role,
                tokenPair.refreshTokenId(),
                tokenPair.sessionId(),
                tokenPair.refreshToken(),
                authProperties.getJwt().getRefreshTokenValidity()
        );
        return toResponse(tokenPair);
    }

    public TokenPairResponseDto refresh(String refreshToken) {
        ParsedJwtToken parsedToken = parseRefreshToken(refreshToken);
        UserRole currentRole = resolveCurrentRole(parsedToken);
        JwtTokenPair nextTokenPair = jwtTokenProvider.rotateTokenPair(
                parsedToken.userId(), currentRole, parsedToken.sessionId()
        );

        RefreshTokenStoreService.RotationResult rotationResult = refreshTokenStoreService.rotate(
                parsedToken.userId(),
                parsedToken.role(),
                parsedToken.tokenId(),
                refreshToken,
                nextTokenPair.refreshTokenId(),
                nextTokenPair.refreshToken(),
                parsedToken.sessionId(),
                authProperties.getJwt().getRefreshTokenValidity()
        );
        if (rotationResult == RefreshTokenStoreService.RotationResult.IN_PROGRESS) {
            throw new RefreshTokenRotationInProgressException();
        }
        if (rotationResult != RefreshTokenStoreService.RotationResult.ROTATED) {
            throw new InvalidRefreshTokenException();
        }
        return toResponse(nextTokenPair);
    }

    public void revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        ParsedJwtToken parsedToken;
        try {
            parsedToken = parseRefreshToken(refreshToken);
        } catch (InvalidRefreshTokenException ignored) {
            // 로그아웃은 이미 만료되거나 폐기된 토큰에도 멱등적으로 성공한다.
            return;
        }
        refreshTokenStoreService.delete(
                parsedToken.userId(), parsedToken.role(), parsedToken.tokenId(), parsedToken.sessionId()
        );
    }

    public void revokeAll(Long userId, UserRole role) {
        refreshTokenStoreService.deleteAllByUserId(userId, role);
    }

    private ParsedJwtToken parseRefreshToken(String refreshToken) {
        ParsedJwtToken parsedToken;
        try {
            parsedToken = jwtTokenProvider.parse(refreshToken);
        } catch (RuntimeException exception) {
            throw new InvalidRefreshTokenException();
        }
        if (!JwtTokenProvider.TOKEN_TYPE_REFRESH.equals(parsedToken.tokenType())) {
            throw new InvalidRefreshTokenException();
        }
        return parsedToken;
    }

    private UserRole resolveCurrentRole(ParsedJwtToken parsedToken) {
        if (parsedToken.role() == UserRole.USER) {
            AppUser user = appUserRepository.findByIdAndStatus(parsedToken.userId(), UserStatus.ACTIVE)
                    .orElseThrow(InvalidRefreshTokenException::new);
            if (!user.isSignupCompleted() || user.getRole() != UserRole.USER) {
                throw new InvalidRefreshTokenException();
            }
            return user.getRole();
        }
        if (parsedToken.role() == UserRole.ADMIN) {
            AdminAccount admin = adminAccountRepository.findById(parsedToken.userId())
                    .filter(AdminAccount::isActive)
                    .filter(account -> account.getRole() == UserRole.ADMIN)
                    .orElseThrow(InvalidRefreshTokenException::new);
            return admin.getRole();
        }
        throw new InvalidRefreshTokenException();
    }

    private TokenPairResponseDto toResponse(JwtTokenPair tokenPair) {
        return new TokenPairResponseDto(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                "Bearer",
                tokenPair.accessTokenExpiresAt().withOffsetSameInstant(ZoneOffset.UTC),
                tokenPair.refreshTokenExpiresAt().withOffsetSameInstant(ZoneOffset.UTC)
        );
    }
}
