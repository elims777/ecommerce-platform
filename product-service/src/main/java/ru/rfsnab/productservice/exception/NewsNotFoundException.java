package ru.rfsnab.productservice.exception;

public class NewsNotFoundException extends RuntimeException {

    public NewsNotFoundException(Long id) {
        super("Новость с id=" + id + " не найдена");
    }

    public NewsNotFoundException(String slug) {
        super("Новость с slug='" + slug + "' не найдена");
    }
}