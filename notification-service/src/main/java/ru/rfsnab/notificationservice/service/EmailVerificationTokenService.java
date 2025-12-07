package ru.rfsnab.notificationservice.service;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rfsnab.notificationservice.models.EmailVerificationToken;
import ru.rfsnab.notificationservice.models.VerificationResponse;
import ru.rfsnab.notificationservice.repository.EmailVerificationTokenRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailVerificationTokenService {

    private final EmailVerificationTokenRepository tokenRepository;

    @Transactional
    public EmailVerificationToken save(EmailVerificationToken token){
        log.debug("Saving verification token for userId: {}", token.getUserId());
        return tokenRepository.save(token);
    }

    public Optional<EmailVerificationToken> findByToken(String token){
        return tokenRepository.findByToken(token);
    }

    public Optional<EmailVerificationToken> findByUserId(Long userId){
        return tokenRepository.findByUserId(userId);
    }

    public boolean isExpired(EmailVerificationToken token){
        return LocalDateTime.now().isAfter(token.getExpiresAt());
    }

    public boolean isValid(EmailVerificationToken token){
        return !isExpired(token) && !token.isVerified();
    }

    @Transactional
    public void markAsVerified(EmailVerificationToken token){
        token.setVerified(true);
        tokenRepository.save(token);
        log.info("Token verified successfully for userId: {}, email: {}",
                token.getUserId(), token.getEmail());
    }

    public boolean existsByToken(String token) {
        return tokenRepository.existsByToken(token);
    }

    public VerificationResponse verifyToken(String token){
        Optional<EmailVerificationToken> tokenOptional = tokenRepository.findByToken(token);

        if(tokenOptional.isEmpty()){
            return VerificationResponse.builder()
                    .valid(false)
                    .message("Token not found")
                    .build();
        }

        EmailVerificationToken tokenEntity = tokenOptional.get();

        if(tokenEntity.isVerified()){
            return VerificationResponse.builder()
                    .valid(false)
                    .message("Email already verified")
                    .build();
        }

        if( tokenEntity.getExpiresAt().isBefore(LocalDateTime.now())){
            return VerificationResponse.builder()
                    .valid(false)
                    .message("Token expired")
                    .build();
        }

        tokenEntity.setVerified(true);
        tokenRepository.save(tokenEntity);

        log.info("Token verified successfully for userId: {}", tokenEntity.getUserId());

        return VerificationResponse.builder()
                .valid(true)
                .userId(tokenEntity.getUserId())
                .email(tokenEntity.getEmail())
                .message("Email verified successfully")
                .build();
    }
}
