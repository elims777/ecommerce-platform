package ru.rfsnab.authservice.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Контроллер для OAuth2 регистрации
 * Маркирует в сессии что это регистрация, а не вход
 */
@Controller
@RequiredArgsConstructor
public class OAuth2RegisterController {
    /**
     * Endpoint для OAuth2 регистрации через Yandex
     * Сохраняет в сессии флаг что это регистрация
     */
    @GetMapping("/auth/oauth2/register/yandex")
    public String registerOAuth2(HttpServletRequest request){
        // Сохраняем в сессии флаг что это регистрация
        HttpSession session = request.getSession();
        session.setAttribute("oauth2_action", "register");
        // Редиректим на стандартный OAuth2 authorization endpoint
        return "redirect:/oauth2/authorization/yandex";
    }

}
