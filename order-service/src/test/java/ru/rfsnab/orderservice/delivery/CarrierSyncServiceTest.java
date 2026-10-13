package ru.rfsnab.orderservice.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import ru.rfsnab.orderservice.BaseIntegrationTest;
import ru.rfsnab.orderservice.kafka.OrderKafkaProducer;
import ru.rfsnab.orderservice.repository.OrderRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Синхронизация терминалов на реальной БД: upsert, деактивация, защита 50 %, битые записи, ошибки ТК, события.
 * Клиенты ТК — моки: проверяется логика сервиса, а не HTTP.
 */
@DisplayName("CarrierSyncService — синхронизация терминалов")
class CarrierSyncServiceTest extends BaseIntegrationTest {

    @Autowired
    private CarrierRepository carrierRepository;
    @Autowired
    private CarrierTerminalRepository terminalRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    private final OrderKafkaProducer kafka = mock(OrderKafkaProducer.class);
    private CarrierClient dellin;
    private CarrierSyncService service;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        terminalRepository.deleteAll();
        for (Carrier carrier : carrierRepository.findAll()) {
            carrier.setTerminalDeliveryEnabled(false);
            carrier.setTerminalsSyncedAt(null);
            carrier.setLastSyncReport(null);
            carrier.setActiveTerminalsCount(0);
            carrierRepository.save(carrier);
        }
        dellin = clientMock(CarrierCode.DELLIN, true);
        service = serviceWith(dellin);
    }

    private CarrierClient clientMock(CarrierCode code, boolean configured) {
        CarrierClient client = mock(CarrierClient.class);
        when(client.code()).thenReturn(code);
        when(client.isConfigured()).thenReturn(configured);
        return client;
    }

    private CarrierSyncService serviceWith(CarrierClient... clients) {
        return new CarrierSyncService(List.of(clients), carrierRepository, terminalRepository,
                deliveryService, kafka, transactionTemplate, objectMapper);
    }

    private static TerminalData point(String id, String city, String address) {
        return new TerminalData(id, PointType.TERMINAL, city, "Терминал " + id, address, 55.0, 82.9, "Пн-Пт", "+7 000");
    }

    private void respondWith(TerminalData... points) {
        when(dellin.fetchTerminals()).thenReturn(Optional.of(List.of(points)));
    }

    private CarrierTerminal stored(String externalId) {
        return terminalRepository.findAllByCarrierCode(CarrierCode.DELLIN).stream()
                .filter(t -> t.getExternalId().equals(externalId)).findFirst().orElseThrow();
    }

    private Carrier carrier() {
        return carrierRepository.findById(CarrierCode.DELLIN).orElseThrow();
    }

    @Test
    @DisplayName("первая синхронизация добавляет точки и обновляет карточку ТК, terminal_delivery_enabled не включает")
    void firstSyncAddsTerminals() throws Exception {
        respondWith(point("1", "Новосибирск", "ул. А"), point("2", "Новосибирск", "ул. Б"), point("3", "Томск", "ул. В"));

        CarrierSyncReport report = service.syncManually(CarrierCode.DELLIN);

        assertThat(report.status()).isEqualTo(SyncStatus.OK);
        assertThat(report.received()).isEqualTo(3);
        assertThat(report.added()).isEqualTo(3);
        assertThat(report.updated()).isZero();
        assertThat(report.deactivated()).isZero();
        assertThat(report.skipped()).isZero();
        assertThat(terminalRepository.findAllByCarrierCode(CarrierCode.DELLIN)).hasSize(3).allMatch(CarrierTerminal::isActive);
        Carrier saved = carrier();
        assertThat(saved.getActiveTerminalsCount()).isEqualTo(3);
        assertThat(saved.getTerminalsSyncedAt()).isNotNull();
        assertThat(saved.isTerminalDeliveryEnabled()).isFalse();
        CarrierSyncReport persisted = objectMapper.readValue(saved.getLastSyncReport(), CarrierSyncReport.class);
        assertThat(persisted.status()).isEqualTo(SyncStatus.OK);
        assertThat(persisted.added()).isEqualTo(3);
        assertThat(persisted.finishedAt()).isNotNull();
    }

    @Test
    @DisplayName("повторная синхронизация: тот же ID со сменой адреса перезаписывается, без изменений — не считается")
    void secondSyncUpdatesChangedAddress() {
        respondWith(point("1", "Новосибирск", "ул. А"), point("2", "Новосибирск", "ул. Б"));
        service.syncManually(CarrierCode.DELLIN);
        long idBefore = stored("1").getId();

        respondWith(point("1", "Новосибирск", "ул. Новая"), point("2", "Новосибирск", "ул. Б"));
        CarrierSyncReport report = service.syncManually(CarrierCode.DELLIN);

        assertThat(report.added()).isZero();
        assertThat(report.updated()).isEqualTo(1);
        assertThat(report.status()).isEqualTo(SyncStatus.OK);
        assertThat(stored("1").getAddress()).isEqualTo("ул. Новая");
        assertThat(stored("1").getId()).isEqualTo(idBefore);
        assertThat(terminalRepository.findAllByCarrierCode(CarrierCode.DELLIN)).hasSize(2);
    }

    @Test
    @DisplayName("пропавшая у ТК точка получает active=false и не удаляется; вернувшаяся — снова активна")
    void vanishedPointDeactivatedAndRestored() {
        respondWith(point("1", "Новосибирск", "ул. А"), point("2", "Новосибирск", "ул. Б"));
        service.syncManually(CarrierCode.DELLIN);

        respondWith(point("1", "Новосибирск", "ул. А"));
        CarrierSyncReport report = service.syncManually(CarrierCode.DELLIN);

        assertThat(report.deactivated()).isEqualTo(1);
        assertThat(report.status()).isEqualTo(SyncStatus.OK);
        assertThat(stored("2").isActive()).isFalse();
        assertThat(carrier().getActiveTerminalsCount()).isEqualTo(1);

        respondWith(point("1", "Новосибирск", "ул. А"), point("2", "Новосибирск", "ул. Б"));
        CarrierSyncReport restored = service.syncManually(CarrierCode.DELLIN);

        assertThat(restored.updated()).isEqualTo(1);
        assertThat(stored("2").isActive()).isTrue();
        assertThat(carrier().getActiveTerminalsCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("защита 50 %: подозрительно малый ответ не деактивирует, но новое и изменённое сохраняет")
    void suspiciousResponseSkipsDeactivation() {
        TerminalData[] six = IntStream.rangeClosed(1, 6)
                .mapToObj(i -> point("T" + i, "Новосибирск", "ул. " + i)).toArray(TerminalData[]::new);
        respondWith(six);
        service.syncManually(CarrierCode.DELLIN);

        respondWith(point("T1", "Новосибирск", "ул. Изменённая"), point("NEW", "Омск", "ул. Н"));
        CarrierSyncReport report = service.syncManually(CarrierCode.DELLIN);

        assertThat(report.status()).isEqualTo(SyncStatus.WARNING);
        assertThat(report.deactivated()).isZero();
        assertThat(report.message()).isEqualTo("Подозрительный ответ ТК: получено 2 из 6, деактивация пропущена");
        assertThat(report.added()).isEqualTo(1);
        assertThat(report.updated()).isEqualTo(1);
        assertThat(stored("T2").isActive()).isTrue();
        assertThat(stored("T1").getAddress()).isEqualTo("ул. Изменённая");
        assertThat(stored("NEW")).isNotNull();
    }

    @Test
    @DisplayName("ровно 50 % валидных точек — не подозрительно, деактивация выполняется")
    void exactlyHalfIsNotSuspicious() {
        respondWith(point("1", "Омск", "ул. 1"), point("2", "Омск", "ул. 2"));
        service.syncManually(CarrierCode.DELLIN);

        respondWith(point("1", "Омск", "ул. 1"));
        CarrierSyncReport report = service.syncManually(CarrierCode.DELLIN);

        assertThat(report.status()).isEqualTo(SyncStatus.OK);
        assertThat(report.deactivated()).isEqualTo(1);
    }

    @Test
    @DisplayName("битые записи пропущены с причиной, остальные сохранены, статус WARNING")
    void brokenRecordsSkippedOthersSaved() {
        respondWith(
                point("OK1", "Новосибирск", "ул. А"),
                point(null, "Новосибирск", "ул. Без ID"),
                point("NOCITY", " ", "ул. Без города"),
                point("NOADDR", "Омск", null),
                new TerminalData("BADLAT", PointType.TERMINAL, "Омск", "n", "ул. Х", 95.0, 82.9, null, null),
                new TerminalData("BADLON", PointType.TERMINAL, "Омск", "n", "ул. Х", 55.0, -181.0, null, null),
                new TerminalData("NOCOORD", PointType.TERMINAL, "Омск", "n", "ул. Х", null, null, null, null),
                point("OK1", "Новосибирск", "ул. Повтор"),
                point("OK2", "Томск", "ул. Б"));

        CarrierSyncReport report = service.syncManually(CarrierCode.DELLIN);

        assertThat(report.status()).isEqualTo(SyncStatus.WARNING);
        assertThat(report.received()).isEqualTo(9);
        assertThat(report.added()).isEqualTo(2);
        assertThat(report.skipped()).isEqualTo(7);
        assertThat(report.moreSkipped()).isZero();
        assertThat(report.skippedItems()).extracting(CarrierSyncReport.SkippedItem::reason).containsExactly(
                "Нет ID точки", "Нет города", "Нет адреса", "Координаты вне диапазона",
                "Координаты вне диапазона", "Нет координат", "Повтор ID в ответе ТК");
        assertThat(terminalRepository.findAllByCarrierCode(CarrierCode.DELLIN)).extracting(CarrierTerminal::getExternalId)
                .containsExactlyInAnyOrder("OK1", "OK2");
        assertThat(stored("OK1").getAddress()).isEqualTo("ул. А");
    }

    @Test
    @DisplayName("в отчёт идут первые 50 пропущенных, остальные — счётчиком moreSkipped")
    void skippedItemsCappedAtFifty() {
        List<TerminalData> points = new ArrayList<>();
        points.add(point("OK", "Омск", "ул. 1"));
        IntStream.range(0, 60).forEach(i -> points.add(point("BAD" + i, "", "ул. 2")));
        when(dellin.fetchTerminals()).thenReturn(Optional.of(points));

        CarrierSyncReport report = service.syncManually(CarrierCode.DELLIN);

        assertThat(report.skipped()).isEqualTo(60);
        assertThat(report.skippedItems()).hasSize(CarrierSyncService.MAX_SKIPPED_ITEMS);
        assertThat(report.moreSkipped()).isEqualTo(10);
    }

    @Test
    @DisplayName("ошибка ТК: данные не тронуты, ERROR в отчёте и в карточке, кэш hash сброшен")
    void carrierErrorLeavesDataUntouched() throws Exception {
        respondWith(point("1", "Омск", "ул. 1"), point("2", "Омск", "ул. 2"));
        service.syncManually(CarrierCode.DELLIN);
        when(dellin.fetchTerminals()).thenThrow(new CarrierApiException("Деловые Линии не вернули hash"));

        CarrierSyncReport report = service.syncManually(CarrierCode.DELLIN);

        assertThat(report.status()).isEqualTo(SyncStatus.ERROR);
        assertThat(report.message()).isEqualTo("Деловые Линии не вернули hash");
        assertThat(terminalRepository.findAllByCarrierCode(CarrierCode.DELLIN)).hasSize(2).allMatch(CarrierTerminal::isActive);
        assertThat(carrier().getActiveTerminalsCount()).isEqualTo(2);
        assertThat(objectMapper.readValue(carrier().getLastSyncReport(), CarrierSyncReport.class).status())
                .isEqualTo(SyncStatus.ERROR);
        verify(dellin).forgetFetchState();
    }

    @Test
    @DisplayName("HTTP-ошибка ТК: в отчёте только код ответа, без URL и ключа из сообщения исключения")
    void httpErrorDoesNotLeakSecrets() {
        when(dellin.fetchTerminals()).thenThrow(new HttpClientErrorException(
                HttpStatus.UNAUTHORIZED, "401 on POST for \"https://vozovoz.ru/api/?token=SECRET\""));

        CarrierSyncReport report = service.syncManually(CarrierCode.DELLIN);

        assertThat(report.status()).isEqualTo(SyncStatus.ERROR);
        assertThat(report.message()).isEqualTo("ТК ответила HTTP 401").doesNotContain("SECRET");
    }

    @Test
    @DisplayName("пустой ответ ТК — ERROR, существующие точки не деактивируются")
    void emptyResponseIsErrorAndKeepsData() {
        respondWith(point("1", "Омск", "ул. 1"));
        service.syncManually(CarrierCode.DELLIN);
        respondWith();

        CarrierSyncReport report = service.syncManually(CarrierCode.DELLIN);

        assertThat(report.status()).isEqualTo(SyncStatus.ERROR);
        assertThat(stored("1").isActive()).isTrue();
    }

    @Test
    @DisplayName("ни одной валидной точки — ERROR, данные не тронуты")
    void noValidPointsIsError() {
        respondWith(point("1", "Омск", "ул. 1"));
        service.syncManually(CarrierCode.DELLIN);
        respondWith(point(null, "Омск", "ул. 2"), point("X", "", "ул. 3"));

        CarrierSyncReport report = service.syncManually(CarrierCode.DELLIN);

        assertThat(report.status()).isEqualTo(SyncStatus.ERROR);
        assertThat(report.message()).contains("нет ни одной валидной");
        assertThat(stored("1").isActive()).isTrue();
        assertThat(terminalRepository.findAllByCarrierCode(CarrierCode.DELLIN)).hasSize(1);
    }

    @Test
    @DisplayName("ключ не задан — NOT_CONFIGURED без обращения к ТК и без ошибки")
    void notConfiguredCarrier() {
        when(dellin.isConfigured()).thenReturn(false);

        CarrierSyncReport report = service.syncManually(CarrierCode.DELLIN);

        assertThat(report.status()).isEqualTo(SyncStatus.NOT_CONFIGURED);
        verify(dellin, never()).fetchTerminals();
        assertThat(carrier().getLastSyncReport()).contains("NOT_CONFIGURED");
        assertThat(carrier().getTerminalsSyncedAt()).isNull();
    }

    @Test
    @DisplayName("справочник не изменился (hash) — OK без изменения точек")
    void unchangedDirectory() {
        respondWith(point("1", "Омск", "ул. 1"));
        service.syncManually(CarrierCode.DELLIN);
        when(dellin.fetchTerminals()).thenReturn(Optional.empty());

        CarrierSyncReport report = service.syncManually(CarrierCode.DELLIN);

        assertThat(report.status()).isEqualTo(SyncStatus.OK);
        assertThat(report.message()).contains("не изменился");
        assertThat(stored("1").isActive()).isTrue();
        assertThat(carrier().getActiveTerminalsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("ручная синхронизация отправляет CARRIER_SYNC_COMPLETED с trigger=MANUAL и одним отчётом")
    void manualSyncPublishesEvent() {
        respondWith(point("1", "Омск", "ул. 1"));

        CarrierSyncReport report = service.syncManually(CarrierCode.DELLIN);

        CarrierSyncEvent event = capturedEvent();
        assertThat(event.eventType()).isEqualTo("CARRIER_SYNC_COMPLETED");
        assertThat(event.trigger()).isEqualTo(SyncTrigger.MANUAL);
        assertThat(event.reports()).containsExactly(report);
    }

    @Test
    @DisplayName("ночная синхронизация: одно событие SCHEDULED без ненастроенных ТК (NOT_CONFIGURED), но запись в БД о них остаётся")
    void nightlySyncSkipsNotConfiguredInEvent() {
        CarrierClient[] all = Arrays.stream(CarrierCode.values())
                .map(code -> code == CarrierCode.DELLIN ? dellin : clientMock(code, false))
                .toArray(CarrierClient[]::new);
        respondWith(point("1", "Омск", "ул. 1"));

        serviceWith(all).syncAllNightly();

        CarrierSyncEvent event = capturedEvent();
        assertThat(event.trigger()).isEqualTo(SyncTrigger.SCHEDULED);
        assertThat(event.reports()).singleElement().satisfies(r -> {
            assertThat(r.carrierCode()).isEqualTo(CarrierCode.DELLIN);
            assertThat(r.status()).isEqualTo(SyncStatus.OK);
        });
        assertThat(carrierRepository.findById(CarrierCode.CDEK).orElseThrow().getLastSyncReport()).contains("NOT_CONFIGURED");
    }

    @Test
    @DisplayName("ночная синхронизация: все ТК не настроены — событие не отправляется")
    void nightlySyncWithoutConfiguredCarriersSendsNothing() {
        CarrierClient[] all = Arrays.stream(CarrierCode.values())
                .map(code -> clientMock(code, false))
                .toArray(CarrierClient[]::new);

        serviceWith(all).syncAllNightly();

        verify(kafka, never()).sendCarrierSyncCompleted(any());
    }

    @Test
    @DisplayName("не удалось записать отчёт ERROR (БД недоступна): отчёт возвращается, исключение не вылетает")
    void storeReportFailureDoesNotBreakSync() {
        when(dellin.fetchTerminals()).thenThrow(new CarrierApiException("ТК недоступна"));
        TransactionTemplate brokenTx = mock(TransactionTemplate.class);
        doThrow(new DataAccessResourceFailureException("db down")).when(brokenTx).executeWithoutResult(any());
        CarrierSyncService broken = new CarrierSyncService(List.of(dellin), carrierRepository, terminalRepository,
                deliveryService, kafka, brokenTx, objectMapper);

        CarrierSyncReport report = broken.syncManually(CarrierCode.DELLIN);

        assertThat(report.status()).isEqualTo(SyncStatus.ERROR);
        assertThat(report.message()).isEqualTo("ТК недоступна");
    }

    @Test
    @DisplayName("ошибка одной ТК не мешает остальным в ночной синхронизации")
    void nightlyErrorDoesNotStopOthers() {
        CarrierClient cdek = clientMock(CarrierCode.CDEK, true);
        when(cdek.fetchTerminals()).thenThrow(new CarrierApiException("СДЭК недоступен"));
        CarrierClient[] all = Arrays.stream(CarrierCode.values())
                .map(code -> code == CarrierCode.DELLIN ? dellin : code == CarrierCode.CDEK ? cdek : clientMock(code, false))
                .toArray(CarrierClient[]::new);
        respondWith(point("1", "Омск", "ул. 1"));

        serviceWith(all).syncAllNightly();

        CarrierSyncEvent event = capturedEvent();
        assertThat(event.reports()).filteredOn(r -> r.carrierCode() == CarrierCode.CDEK)
                .singleElement().extracting(CarrierSyncReport::status).isEqualTo(SyncStatus.ERROR);
        assertThat(event.reports()).filteredOn(r -> r.carrierCode() == CarrierCode.DELLIN)
                .singleElement().extracting(CarrierSyncReport::status).isEqualTo(SyncStatus.OK);
        assertThat(stored("1").isActive()).isTrue();
    }

    @Test
    @DisplayName("старт: синхронизируются только настроенные ТК с пустым справочником, событие STARTUP")
    void startupSyncsOnlyConfiguredEmptyCarriers() {
        CarrierClient[] all = Arrays.stream(CarrierCode.values())
                .map(code -> code == CarrierCode.DELLIN ? dellin : clientMock(code, false))
                .toArray(CarrierClient[]::new);
        respondWith(point("1", "Омск", "ул. 1"));
        CarrierSyncService startupService = serviceWith(all);

        startupService.syncEmptyOnStartup();

        CarrierSyncEvent first = capturedEvent();
        assertThat(first.trigger()).isEqualTo(SyncTrigger.STARTUP);
        assertThat(first.reports()).hasSize(1);
        assertThat(first.reports().get(0).carrierCode()).isEqualTo(CarrierCode.DELLIN);

        // справочник уже заполнен — повторный старт ничего не синхронизирует и события не шлёт
        clearInvocations(kafka);
        startupService.syncEmptyOnStartup();
        verify(kafka, never()).sendCarrierSyncCompleted(any());
    }

    @Test
    @DisplayName("недоступная Kafka не ломает синхронизацию: отчёт возвращается, данные сохранены")
    void kafkaFailureDoesNotBreakSync() {
        doThrow(new IllegalStateException("kafka down")).when(kafka).sendCarrierSyncCompleted(any());
        respondWith(point("1", "Омск", "ул. 1"));

        CarrierSyncReport report = service.syncManually(CarrierCode.DELLIN);

        assertThat(report.status()).isEqualTo(SyncStatus.OK);
        assertThat(stored("1").isActive()).isTrue();
    }

    private CarrierSyncEvent capturedEvent() {
        ArgumentCaptor<CarrierSyncEvent> captor = ArgumentCaptor.forClass(CarrierSyncEvent.class);
        verify(kafka).sendCarrierSyncCompleted(captor.capture());
        return captor.getValue();
    }
}
