package ru.rfsnab.orderservice.delivery.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import ru.rfsnab.orderservice.delivery.client.CarrierClient;
import ru.rfsnab.orderservice.delivery.client.JournalItem;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentDirection;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ShipmentJournalImporter")
class ShipmentJournalImporterTest {

    private static final String INN = "7700000001";

    private final ShipmentService service = mock(ShipmentService.class);
    private final CarrierClient dellin = mock(CarrierClient.class);
    private final CarrierClient pec = mock(CarrierClient.class);

    private static JournalItem item(String waybill, String senderInn, String receiverInn) {
        return new JournalItem(waybill, null, senderInn, null, receiverInn, null, null, null, null, null, null, null);
    }

    private ShipmentJournalImporter importer(String inn) {
        when(dellin.code()).thenReturn(CarrierCode.DELLIN);
        when(dellin.supportsJournal()).thenReturn(true);
        when(pec.code()).thenReturn(CarrierCode.PEC);
        when(pec.supportsJournal()).thenReturn(false);
        return new ShipmentJournalImporter(List.of(dellin, pec), service, inn);
    }

    @Test
    @DisplayName("направление по ИНН компании: отправитель — OUTGOING, получатель — INCOMING, иначе UNKNOWN")
    void directionByInn() {
        ShipmentJournalImporter importer = importer(INN);

        assertThat(importer.directionOf(item("1", INN, "5400"))).isEqualTo(ShipmentDirection.OUTGOING);
        assertThat(importer.directionOf(item("2", "5400", INN))).isEqualTo(ShipmentDirection.INCOMING);
        assertThat(importer.directionOf(item("3", INN, INN))).isEqualTo(ShipmentDirection.OUTGOING);
        assertThat(importer.directionOf(item("4", "5400", "5401"))).isEqualTo(ShipmentDirection.UNKNOWN);
        assertThat(importer.directionOf(item("5", null, null))).isEqualTo(ShipmentDirection.UNKNOWN);
    }

    @Test
    @DisplayName("несколько компаний через запятую: любая из них даёт направление")
    void severalCompanyInns() {
        ShipmentJournalImporter importer = importer(INN + ", 1101164021");

        assertThat(importer.directionOf(item("1", "1101164021", "5400"))).isEqualTo(ShipmentDirection.OUTGOING);
        assertThat(importer.directionOf(item("2", "5400", "1101164021"))).isEqualTo(ShipmentDirection.INCOMING);
        assertThat(importer.directionOf(item("3", INN, "1101164021"))).isEqualTo(ShipmentDirection.OUTGOING);
    }

    @Test
    @DisplayName("ИНН компании не задан — направление всегда UNKNOWN")
    void blankInnMeansUnknown() {
        assertThat(importer("").directionOf(item("1", INN, null))).isEqualTo(ShipmentDirection.UNKNOWN);
    }

    @Test
    @DisplayName("импортируются только ТК с журналом; каждая запись — upsert с направлением")
    void importsOnlyJournalCarriers() {
        ShipmentJournalImporter importer = importer(INN);
        JournalItem outgoing = item("1", INN, null);
        JournalItem incoming = item("2", null, INN);
        when(dellin.fetchJournal(any(Instant.class))).thenReturn(List.of(outgoing, incoming));

        importer.importAll();

        verify(service).upsertFromJournal(CarrierCode.DELLIN, outgoing, ShipmentDirection.OUTGOING);
        verify(service).upsertFromJournal(CarrierCode.DELLIN, incoming, ShipmentDirection.INCOMING);
        verify(pec, never()).fetchJournal(any());
    }

    @Test
    @DisplayName("повторный запуск при идущем импорте пропускается; после завершения запуск снова возможен")
    void concurrentRunSkipped() {
        ShipmentJournalImporter importer = importer(INN);
        AtomicReference<Boolean> nested = new AtomicReference<>();
        when(dellin.fetchJournal(any(Instant.class))).thenAnswer(inv -> {
            nested.set(importer.importAll());
            return List.of();
        });

        assertThat(importer.importAll()).isTrue();

        assertThat(nested.get()).isFalse();
        verify(dellin, times(1)).fetchJournal(any(Instant.class));
        assertThat(importer.importAll()).isTrue();
    }

    @Test
    @DisplayName("importAsync: флаг выставлен сразу, повторный запуск отклонён, после завершения флаг снят")
    void importAsyncRunningFlag() throws Exception {
        ShipmentJournalImporter importer = importer(INN);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(dellin.fetchJournal(any(Instant.class))).thenAnswer(inv -> {
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return List.of();
        });

        assertThat(importer.importAsync()).isTrue();
        assertThat(importer.isRunning()).isTrue();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(importer.importAsync()).isFalse();

        release.countDown();
        await().atMost(5, TimeUnit.SECONDS).until(() -> !importer.isRunning());
    }

    @Test
    @DisplayName("неожиданная ошибка при разборе журнала одной ТК не мешает загрузке следующей")
    void unexpectedFailureInOneCarrierDoesNotStopNext() {
        when(dellin.code()).thenReturn(CarrierCode.DELLIN);
        when(dellin.supportsJournal()).thenReturn(true);
        when(pec.code()).thenReturn(CarrierCode.PEC);
        when(pec.supportsJournal()).thenReturn(true);
        ShipmentJournalImporter importer = new ShipmentJournalImporter(List.of(dellin, pec), service, INN);
        JournalItem pecItem = item("1", INN, null);
        when(dellin.fetchJournal(any(Instant.class))).thenThrow(new IllegalStateException("parse bug"));
        when(pec.fetchJournal(any(Instant.class))).thenReturn(List.of(pecItem));

        assertThat(importer.importAll()).isTrue();

        verify(service).upsertFromJournal(CarrierCode.PEC, pecItem, ShipmentDirection.OUTGOING);
        verify(service, never()).upsertFromJournal(eq(CarrierCode.DELLIN), any(), any());
    }

    @Test
    @DisplayName("ошибка одной записи не мешает остальным; недоступный журнал не бросает исключение")
    void failuresIsolated() {
        ShipmentJournalImporter importer = importer(INN);
        JournalItem bad = item("1", INN, null);
        JournalItem good = item("2", INN, null);
        when(dellin.fetchJournal(any(Instant.class))).thenReturn(List.of(bad, good));
        doThrow(new IllegalStateException("boom")).when(service).upsertFromJournal(eq(CarrierCode.DELLIN), eq(bad), any());

        importer.importAll();

        verify(service).upsertFromJournal(CarrierCode.DELLIN, good, ShipmentDirection.OUTGOING);

        when(dellin.fetchJournal(any(Instant.class))).thenThrow(new ResourceAccessException("timeout"));
        importer.importAll();
    }
}
