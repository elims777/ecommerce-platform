package ru.rfsnab.userservice.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import ru.rfsnab.userservice.services.LegalEntityService;

/**
 * Единая проверка доступа к данным юрлица и к статусу привязки пользователя.
 * Используется в @PreAuthorize как @legalEntityAccess.
 */
@Component("legalEntityAccess")
@RequiredArgsConstructor
public class LegalEntityAccessChecker {

    public static final String AUTHORITY_CLIENT_B2B = "CLIENT_B2B";
    private static final String AUTHORITY_ADMIN = "ROLE_ADMIN";
    private static final String AUTHORITY_INTERNAL = "ROLE_INTERNAL";

    private final LegalEntityService legalEntityService;

    /**
     * Админ, внутренний сервис, само юрлицо (B2B, id токена = id юрлица)
     * или B2C-пользователь с подтверждённой привязкой к юрлицу.
     */
    public boolean canAccessLegalEntity(Authentication authentication, Long legalEntityId) {
        if (isPrivileged(authentication)) {
            return true;
        }
        if (isB2b(authentication)) {
            return String.valueOf(legalEntityId).equals(authentication.getName());
        }
        return legalEntityService.isLinkConfirmed(Long.valueOf(authentication.getName()), legalEntityId);
    }

    /**
     * Админ, внутренний сервис или сам B2C-пользователь.
     */
    public boolean canAccessUser(Authentication authentication, Long userId) {
        if (isPrivileged(authentication)) {
            return true;
        }
        return !isB2b(authentication) && String.valueOf(userId).equals(authentication.getName());
    }

    private boolean isPrivileged(Authentication authentication) {
        return hasAuthority(authentication, AUTHORITY_ADMIN) || hasAuthority(authentication, AUTHORITY_INTERNAL);
    }

    private boolean isB2b(Authentication authentication) {
        return hasAuthority(authentication, AUTHORITY_CLIENT_B2B);
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
