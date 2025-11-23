package ru.rfsnab.userservice.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.services.UserService;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JWTService jwtService;
    private final UserService userService;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        log.debug("Processing request: {} {}", request.getMethod(), request.getRequestURI());
        log.debug("Authorization header present: {}", authHeader != null);

        //Если нет заголовка или он не начинается с префикса Bearer - то пропускаем
        if(authHeader==null || !authHeader.startsWith(BEARER_PREFIX)){
            log.debug("No Bearer token found, skipping JWT filter");
            filterChain.doFilter(request, response);
            return;
        }
        //Убираем префикс
        final String jwt = authHeader.substring(BEARER_PREFIX.length());
        log.debug("JWT token extracted, length: {}", jwt.length());

        try{
            //Проверяем валидность токена
            if(jwtService.isTokenValid(jwt)){
                String email = jwtService.extractEmail(jwt);
                log.debug("Token valid for user: {}", email);

                // Проверяем что пользователь ещё не аутентифицирован в текущем контексте
                if(email!=null && SecurityContextHolder.getContext().getAuthentication()==null){

                    // Загружаем пользователя из БД для получения ролей
                    UserEntity user = userService.findUserByEmail(email).orElse(null);

                    if(user!=null){
                        // Преобразуем роли в GrantedAuthority
                        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                                .map(role -> new SimpleGrantedAuthority(role.getName()))
                                .toList();

                        // Создаём Authentication token
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                email,
                                null,
                                authorities
                        );

                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        // Устанавливаем в SecurityContext
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        log.info("Authenticated user: {} with roles: {}", email, authorities);
                    } else {
                        log.warn("User not found in DB: {}", email);
                    }
                } else {
                    log.warn("Token validation failed");
                }
            }
        } catch (Exception e){
            log.error("Cannot set user authentication: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }
}
