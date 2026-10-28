package ru.rfsnab.userservice.configuration;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("InternalTokenFilter")
class InternalTokenFilterTest {

    private static final String SECRET = "test-secret";
    private static final String INTERNAL_PATH = "/v1/users/authenticate";
    private static final String HEADER = "X-Internal-Token";

    private final InternalTokenFilter filter = new InternalTokenFilter(SECRET);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Authentication run(String path, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        if (token != null) {
            request.addHeader(HEADER, token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("валидный токен на внутреннем пути выдаёт ROLE_INTERNAL")
    void validTokenOnInternalPathGrantsRole() throws Exception {
        Authentication auth = run(INTERNAL_PATH, SECRET);

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_INTERNAL");
    }

    @Test
    @DisplayName("валидный токен на пути с шаблоном (/api/v1/legal-entities/*) выдаёт ROLE_INTERNAL")
    void validTokenOnWildcardPathGrantsRole() throws Exception {
        assertThat(run("/api/v1/legal-entities/5", SECRET)).isNotNull();
    }

    @Test
    @DisplayName("невалидный токен роль не выдаёт, запрос идёт дальше по цепочке")
    void invalidTokenDoesNotGrantRole() throws Exception {
        assertThat(run(INTERNAL_PATH, "wrong")).isNull();
    }

    @Test
    @DisplayName("без заголовка роль не выдаётся")
    void missingTokenDoesNotGrantRole() throws Exception {
        assertThat(run(INTERNAL_PATH, null)).isNull();
    }

    @Test
    @DisplayName("путь вне INTERNAL_PATHS: фильтр пропускается целиком (shouldNotFilter), даже с валидным токеном роли нет")
    void validTokenOnNonInternalPathDoesNotGrantRole() throws Exception {
        assertThat(run("/v1/users/1", SECRET)).isNull();
    }
}
