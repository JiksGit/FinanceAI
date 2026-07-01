package com.finance.dashboard.service;

import com.finance.dashboard.dto.request.LoginRequest;
import com.finance.dashboard.dto.request.RefreshTokenRequest;
import com.finance.dashboard.dto.request.SignupRequest;
import com.finance.dashboard.dto.response.LoginResponse;
import com.finance.dashboard.dto.response.TokenResponse;
import com.finance.dashboard.entity.RefreshToken;
import com.finance.dashboard.entity.User;
import com.finance.dashboard.exception.CustomException;
import com.finance.dashboard.exception.ErrorCode;
import com.finance.dashboard.repository.RefreshTokenRepository;
import com.finance.dashboard.repository.UserRepository;
import com.finance.dashboard.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        userRepository.save(User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .build());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getEmail());
        String rawRefreshToken = jwtTokenProvider.createRefreshToken(user.getEmail());

        // 기존 토큰 삭제 후 새로 저장 (단일 세션)
        refreshTokenRepository.deleteByUserId(user.getId());
        Claims claims = jwtTokenProvider.parseClaims(rawRefreshToken);
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .token(rawRefreshToken)
                .expiresAt(jwtTokenProvider.getExpiration(claims))
                .build());

        return new LoginResponse(accessToken, rawRefreshToken, user.getNickname());
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parseClaims(request.refreshToken());
        } catch (ExpiredJwtException e) {
            refreshTokenRepository.deleteByToken(request.refreshToken());
            throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        if (!jwtTokenProvider.isRefreshToken(claims)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        // DB에 존재하는지 확인 (로그아웃된 토큰 차단)
        RefreshToken stored = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        String email = jwtTokenProvider.getEmail(claims);
        String newAccessToken = jwtTokenProvider.createAccessToken(email);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(email);

        // 토큰 교체
        refreshTokenRepository.delete(stored);
        Claims newClaims = jwtTokenProvider.parseClaims(newRefreshToken);
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(stored.getUserId())
                .token(newRefreshToken)
                .expiresAt(jwtTokenProvider.getExpiration(newClaims))
                .build());

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByToken(refreshToken);
    }
}
