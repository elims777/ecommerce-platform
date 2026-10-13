package ru.rfsnab.orderservice.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
    private final String companyInn;

    /** Создаёт импортёр; без ИНН компании направление всех отправлений UNKNOWN. */
    public ShipmentJournalImporter(List<CarrierClient> clients, ShipmentService shipmentService,
                                   @Value("${company.inn:}") String companyInn) {
        this.clients = clients;
        this.shipmentService = shipmentService;
        this.companyInn = companyInn;
    }

    /** Импортирует журналы всех ТК, у которых он есть. */
    @Scheduled(cron = IMPORT_CRON, zone = "UTC")
    public void importAll() {
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

    /** Направление по ИНН компании: она отправитель — OUTGOING, получатель — INCOMING, иначе UNKNOWN. */
    ShipmentDirection directionOf(JournalItem item) {
        if (!StringUtils.hasText(companyInn)) {
            return ShipmentDirection.UNKNOWN;
        }
        if (companyInn.equals(item.senderInn())) {
            return ShipmentDirection.OUTGOING;
        }
        return companyInn.equals(item.receiverInn()) ? ShipmentDirection.INCOMING : ShipmentDirection.UNKNOWN;
    }
}
