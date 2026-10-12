package ru.rfsnab.orderservice.exception;

/** Точка самовывоза с таким названием уже есть (warehouse_points.name — UNIQUE). */
public class DuplicateWarehousePointException extends RuntimeException {
    public DuplicateWarehousePointException(String message) {
        super(message);
    }
}
