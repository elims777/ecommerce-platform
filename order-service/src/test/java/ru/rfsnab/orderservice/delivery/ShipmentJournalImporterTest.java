package ru.rfsnab.orderservice.delivery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ShipmentJournalImporter")
class ShipmentJournalImporterTest {

    private static final String INN = "7700000001";

    private final ShipmentService service = mock(ShipmentService.class);
    private final CarrierClient dellin = mock(CarrierClient.class);
    private final CarrierClient pec = mock(CarrierClient.class);

    private static JournalItem item(String waybill, String senderInn, String receiverInn) {
        return new JournalItem(waybill, null, senderInn, null, receiverInn, null, null, null, null, null, null);
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
