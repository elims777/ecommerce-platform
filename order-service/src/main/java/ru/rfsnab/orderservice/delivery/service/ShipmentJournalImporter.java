package ru.rfsnab.orderservice.delivery.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import ru.rfsnab.orderservice.delivery.client.CarrierClient;
import ru.rfsnab.orderservice.delivery.client.JournalItem;
import ru.rfsnab.orderservice.delivery.exception.CarrierApiException;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentDirection;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Импорт журналов отправлений ТК (спек 6.7): раз в 2 часа, upsert по ТК и номеру накладной.
 * Направление определяется по ИНН компании, заказ привязывается по номеру заказа сайта.
 */
@Slf4j
@Component
public class ShipmentJournalImporter {

    /** Каждые 2 часа в 30 минут (сдвиг от опроса статусов; сервер в UTC). */
    static final String IMPORT_CRON = "0 30 */2 * * *";

    /** За какой период запрашивается журнал; upsert идемпотентен, поэтому окно перекрывается с прошлым запуском. */
    static final Duration LOOKBACK = Duration.ofDays(30);

    private final List<CarrierClient> clients;
    private final ShipmentService shipmentService;
    private final Set<String> companyInns;
    private final AtomicBoolean running = new AtomicBoolean();

    /** Создаёт импортёр; companyInn — ИНН наших компаний через запятую, без них направление всех отправлений UNKNOWN. */
    public ShipmentJournalImporter(List<CarrierClient> clients, ShipmentService shipmentService,
                                   @Value("${company.inn:}") String companyInn) {
        this.clients = clients;
        this.shipmentService = shipmentService;
        this.companyInns = Arrays.stream(companyInn.split(",")).map(String::trim).filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    /** После старта сервиса наполняет журнал, не дожидаясь расписания и не блокируя запуск. */
    @EventListener(ApplicationReadyEvent.class)
    public void importOnStartup() {
        CompletableFuture.runAsync(this::importAll).exceptionally(e -> {
            log.error("Импорт журналов отправлений упал", e);
            return null;
        });
    }

    /**
     * Запускает импорт в фоне (кнопка в админке).
     *
     * @return false, если импорт уже идёт
     */
    public boolean importAsync() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        CompletableFuture.runAsync(() -> {
            try {
                importJournals();
            } finally {
                running.set(false);
            }
        }).exceptionally(e -> {
            log.error("Импорт журналов отправлений упал", e);
            return null;
        });
        return true;
    }

    /** Идёт ли импорт сейчас. */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Импортирует журналы всех ТК, у которых он есть. Параллельный запуск пропускается.
     *
     * @return false, если импорт уже идёт и этот запуск пропущен
     */
    @Scheduled(cron = IMPORT_CRON, zone = "UTC")
    public boolean importAll() {
        if (!running.compareAndSet(false, true)) {
            log.info("Импорт журналов уже идёт, повторный запуск пропущен");
            return false;
        }
        try {
            importJournals();
            return true;
        } finally {
            running.set(false);
        }
    }

    private void importJournals() {
        for (CarrierClient client : clients) {
            if (!client.supportsJournal()) {
                continue;
            }
            List<JournalItem> items;
            try {
                items = client.fetchJournal(Instant.now().minus(LOOKBACK));
            } catch (CarrierApiException | RestClientException e) {
                log.warn("Журнал {} не загружен: {}", client.code(), e.getMessage());
                continue;
            } catch (RuntimeException e) {
                // ошибка разбора ответа одной ТК не должна останавливать остальные
                log.error("Журнал {} не загружен из-за неожиданной ошибки", client.code(), e);
                continue;
            }
            for (JournalItem item : items) {
                try {
                    shipmentService.upsertFromJournal(client.code(), item, directionOf(item));
                } catch (RuntimeException e) {
                    log.error("Не удалось сохранить отправление {} из журнала {}", item.waybillNumber(), client.code(), e);
                }
            }
        }
    }

    /** Направление по ИНН наших компаний: одна из них отправитель — OUTGOING, получатель — INCOMING, иначе UNKNOWN. */
    ShipmentDirection directionOf(JournalItem item) {
        if (item.senderInn() != null && companyInns.contains(item.senderInn())) {
            return ShipmentDirection.OUTGOING;
        }
        return item.receiverInn() != null && companyInns.contains(item.receiverInn())
                ? ShipmentDirection.INCOMING : ShipmentDirection.UNKNOWN;
    }
}
