package ru.rfsnab.notificationservice.service;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rfsnab.notificationservice.models.EmailVerificationToken;
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
}
