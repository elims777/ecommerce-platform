package ru.rfsnab.orderservice.delivery.client;

import ru.rfsnab.orderservice.delivery.models.entity.ShipmentDocument;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentPackage;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentPayer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Груз и детали отправления, как их вернула ТК. Все поля nullable: null — ТК не сообщает, существующее значение не затирается.
 *
 * @param paid        оплачено ли отправление
 * @param fromAddress true — забор от адреса, false — от терминала/ПВЗ
 * @param toAddress   true — доставка до адреса, false — до терминала/ПВЗ
 * @param deliveredAt фактическая выдача/доставка
 * @param documents   пустой список тоже записывается
 * @param packages    пустой список тоже записывается
 * @param payers      плательщики; null — ТК не сообщает, существующий список не затирается
 */
public record ShipmentCargo(BigDecimal weightKg, BigDecimal volumeM3, Integer placesCount, BigDecimal cost,
                            Boolean paid, Boolean fromAddress, Boolean toAddress, Instant deliveredAt,
                            List<ShipmentDocument> documents, List<ShipmentPackage> packages, List<ShipmentPayer> payers) {
}
