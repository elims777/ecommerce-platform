package ru.rfsnab.orderservice.delivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ShipmentPoller")
class ShipmentPollerTest {

    private final ShipmentRepository repository = mock(ShipmentRepository.class);
    private final ShipmentService service = mock(ShipmentService.class);
    private final CarrierClient dellin = mock(CarrierClient.class);
    private final CarrierClient pec = mock(CarrierClient.class);
    private ShipmentPoller poller;

    @BeforeEach
    void setUp() {
        when(dellin.code()).thenReturn(CarrierCode.DELLIN);
        when(dellin.supportsStatusTracking()).thenReturn(true);
        when(pec.code()).thenReturn(CarrierCode.PEC);
        when(pec.supportsStatusTracking()).thenReturn(false);
        poller = new ShipmentPoller(List.of(dellin, pec), repository, service);
    }

    private static Shipment shipment(long id) {
        return Shipment.builder().id(id).waybillNumber("W" + id).build();
    }

    private void due(CarrierCode code, Shipment... shipments) {
        when(repository.findPollable(eq(code), any(), any())).thenReturn(List.of(shipments));
    }

    private static CarrierShipmentStatus inTransit() {
        return new CarrierShipmentStatus(ShipmentStatusCode.IN_TRANSIT, "В пути", null, null);
    }

    @Test
    @DisplayName("ТК без поддержки статусов пропускается целиком: ни запроса отправлений, ни обращения к ТК")
    void unsupportedCarrierSkipped() {
        due(CarrierCode.DELLIN);

        poller.pollAll();

        verify(repository, never()).findPollable(eq(CarrierCode.PEC), any(), any());
        verify(pec, never()).fetchStatuses(any());
    }

    @Test
    @DisplayName("статусы применяются по номерам; накладной нет в ответе — применяется empty (не найдена)")
    void appliesStatusesPerShipment() {
        due(CarrierCode.DELLIN, shipment(1), shipment(2));
        when(dellin.fetchStatuses(List.of("W1", "W2"))).thenReturn(Map.of("W1", inTransit()));

        poller.pollAll();

        verify(service).applyPolled(1L, Optional.of(inTransit()), false);
        verify(service).applyPolled(2L, Optional.empty(), false);
    }

    @Test
    @DisplayName("пачки по размеру BATCH_SIZE: один запрос к ТК на пачку")
    void batches() {
        Shipment[] shipments = IntStream.rangeClosed(1, ShipmentPoller.BATCH_SIZE + 1)
                .mapToObj(ShipmentPollerTest::shipment).toArray(Shipment[]::new);
        due(CarrierCode.DELLIN, shipments);
        when(dellin.fetchStatuses(any())).thenReturn(Map.of());

        poller.pollAll();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(dellin, times(2)).fetchStatuses(captor.capture());
        assertThat(captor.getAllValues().get(0)).hasSize(ShipmentPoller.BATCH_SIZE);
        assertThat(captor.getAllValues().get(1)).hasSize(1);
    }

    @Test
    @DisplayName("падение одного отправления не мешает остальным (своя транзакция на каждое)")
    void oneFailureDoesNotStopOthers() {
        due(CarrierCode.DELLIN, shipment(1), shipment(2), shipment(3));
        when(dellin.fetchStatuses(any())).thenReturn(Map.of("W1", inTransit(), "W2", inTransit(), "W3", inTransit()));
        doThrow(new IllegalStateException("boom")).when(service).applyPolled(eq(2L), any(), anyBoolean());

        poller.pollAll();

        verify(service).applyPolled(1L, Optional.of(inTransit()), false);
        verify(service).applyPolled(2L, Optional.of(inTransit()), false);
        verify(service).applyPolled(3L, Optional.of(inTransit()), false);
    }

    @Test
    @DisplayName("ТК не ответила на пачку: статусы не применяются (иначе все накладные стали бы «не найдены»), следующая пачка опрашивается")
    void failedBatchAppliesNothing() {
        Shipment[] shipments = IntStream.rangeClosed(1, ShipmentPoller.BATCH_SIZE + 1)
                .mapToObj(ShipmentPollerTest::shipment).toArray(Shipment[]::new);
        due(CarrierCode.DELLIN, shipments);
        when(dellin.fetchStatuses(any()))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(Map.of("W" + (ShipmentPoller.BATCH_SIZE + 1), inTransit()));

        poller.pollAll();

        verify(service, times(1)).applyPolled(any(), any(), anyBoolean());
        verify(service).applyPolled(ShipmentPoller.BATCH_SIZE + 1L, Optional.of(inTransit()), false);
    }

    @Test
    @DisplayName("ошибка одной ТК не мешает опросу другой")
    void carrierFailureIsolated() {
        CarrierClient cdek = mock(CarrierClient.class);
        when(cdek.code()).thenReturn(CarrierCode.CDEK);
        when(cdek.supportsStatusTracking()).thenReturn(true);
        poller = new ShipmentPoller(List.of(dellin, cdek), repository, service);
        when(repository.findPollable(eq(CarrierCode.DELLIN), any(), any())).thenThrow(new IllegalStateException("db"));
        due(CarrierCode.CDEK, shipment(5));
        when(cdek.fetchStatuses(any())).thenReturn(Map.of("W5", inTransit()));

        poller.pollAll();

        verify(service).applyPolled(5L, Optional.of(inTransit()), false);
    }
}
