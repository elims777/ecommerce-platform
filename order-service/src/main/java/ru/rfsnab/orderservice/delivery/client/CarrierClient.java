package ru.rfsnab.orderservice.delivery.client;

import ru.rfsnab.orderservice.delivery.exception.CarrierApiException;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentDocument;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Клиент API транспортной компании. Одна реализация на ТК.
 * Ключи API берутся из env и никогда не попадают в логи и сообщения исключений.
 */
public interface CarrierClient {

    /** Код ТК, которую обслуживает клиент. */
    CarrierCode code();

    /** Задан ли ключ API в env. Если нет — клиент отключён и вызывать {@link #fetchTerminals()} нельзя. */
    boolean isConfigured();

    /**
     * Загружает все точки выдачи ТК.
     *
     * @return точки; {@link Optional#empty()}, если справочник не менялся с прошлой загрузки (hash у Деловых Линий)
     * @throws CarrierApiException если ТК недоступна или ответ не разобрался
     */
    Optional<List<TerminalData>> fetchTerminals();

    /** Забывает признак «справочник не менялся» — вызывается, если загруженные точки не удалось сохранить. */
    default void forgetFetchState() {
    }

    /** Умеем ли мы узнавать статусы накладных этой ТК (и хватает ли для этого ключей). По умолчанию нет. */
    default boolean supportsStatusTracking() {
        return false;
    }

    /**
     * Статусы накладных.
     *
     * @return статус по номеру накладной; накладной нет в карте, если ТК её не знает
     * @throws CarrierApiException если ТК не поддерживает опрос статусов, недоступна или ответ не разобрался
     */
    default Map<String, CarrierShipmentStatus> fetchStatuses(List<String> waybills) {
        throw new CarrierApiException("Опрос статусов для " + code() + " не поддерживается");
    }

    /** Есть ли у ТК журнал отправлений компании (и хватает ли ключей). По умолчанию нет. */
    default boolean supportsJournal() {
        return false;
    }

    /**
     * Отправления компании, созданные или изменённые начиная с since.
     *
     * @throws CarrierApiException если ТК не поддерживает журнал, недоступна или ответ не разобрался
     */
    default List<JournalItem> fetchJournal(Instant since) {
        throw new CarrierApiException("Журнал отправлений для " + code() + " не поддерживается");
    }

    /**
     * Скачивает документ отправления.
     *
     * @throws CarrierApiException если ТК не поддерживает скачивание, недоступна или ответ не разобрался
     */
    default byte[] downloadDocument(ShipmentDocument doc) {
        throw new CarrierApiException("Скачивание документов для " + code() + " не поддерживается");
    }
}
