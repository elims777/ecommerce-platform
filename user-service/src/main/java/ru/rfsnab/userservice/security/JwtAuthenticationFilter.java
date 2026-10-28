package ru.rfsnab.userservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JWTService jwtService;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLIENT_TYPE_B2B = "B2B";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        //Если нет заголовка или он не начинается с префикса Bearer - то пропускаем
        if(authHeader==null || !authHeader.startsWith(BEARER_PREFIX)){
            filterChain.doFilter(request, response);
            return;
        }
        //Убираем префикс
        final String jwt = authHeader.substring(BEARER_PREFIX.length());

        try{
            //Проверяем валидность токена
            if(jwtService.isTokenValid(jwt)){
                Long userId = jwtService.extractUserId(jwt);

                // Проверяем что пользователь ещё не аутентифицирован в текущем контексте
                if(userId != null && SecurityContextHolder.getContext().getAuthentication() == null){

                    List<String> roles = jwtService.extractRolesFromToken(jwt);

                    // Преобразуем роли в GrantedAuthority
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>(roles.stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList());
                    // В B2B-токене sub = id юрлица, а не пользователя — без этого признака id неразличимы
                    if (CLIENT_TYPE_B2B.equals(jwtService.extractClientType(jwt))) {
                        authorities.add(new SimpleGrantedAuthority(LegalEntityAccessChecker.AUTHORITY_CLIENT_B2B));
                    }

                    // Создаём Authentication token
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userId.toString(),
                            null,
                            authorities
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Устанавливаем в SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e){
            log.debug("Ошибка аутентификации по JWT: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }
}
