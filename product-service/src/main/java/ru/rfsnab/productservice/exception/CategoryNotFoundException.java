package ru.rfsnab.productservice.exception;

public class CategoryNotFoundException extends RuntimeException{
    public CategoryNotFoundException(Long id) {
        super("Категория с id=" + id + " не найдена");
    }

    public CategoryNotFoundException(String slug) {
        super("Категория с slug='" + slug + "' не найдена");
    }

    public CategoryNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
