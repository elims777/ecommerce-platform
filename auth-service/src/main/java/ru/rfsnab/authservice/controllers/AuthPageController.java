package ru.rfsnab.authservice.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthPageController {
    /**
     * Главная страница входа с формой и кнопкой OAuth2
     */
    @GetMapping("/")
    public String home() {
        return "login";
    }

    /**
     * Страница логина с формой (альтернативный URL)
     */
    @GetMapping("/auth/login-form")
    public String loginPage() {
        return "login";
    }

    /**
     * Страница регистрации
     */
    @GetMapping("/auth/register")
    public String registerPage() {
        return "register";
    }

    /**
     * Страница успешной аутентификации (OAuth2 + обычный login)
     */
    @GetMapping("/oauth2/success")
    public String success() {
        return "oauth2-success";
    }
}
