package ru.rfsnab.orderservice.exception;

import lombok.Getter;

import java.util.List;

/**
 * Исключение: профиль пользователя не заполнен полностью.
 * Блокирует добавление в корзину и создание заказа.
 */
@Getter
public class ProfileIncompleteException extends RuntimeException {
    private final List<String> missing;

    public ProfileIncompleteException(List<String> missing) {
        super("Заполните профиль для работы на портале");
        this.missing = missing;
    }
}