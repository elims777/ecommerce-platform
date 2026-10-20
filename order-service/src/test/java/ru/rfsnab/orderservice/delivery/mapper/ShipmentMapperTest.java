package ru.rfsnab.orderservice.delivery.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import ru.rfsnab.orderservice.delivery.models.dto.ShipmentDocumentDto;
import ru.rfsnab.orderservice.delivery.models.dto.ShipmentDto;
import ru.rfsnab.orderservice.delivery.models.dto.ShipmentPackageDto;
import ru.rfsnab.orderservice.delivery.models.dto.ShipmentPayerDto;
import ru.rfsnab.orderservice.delivery.models.entity.Carrier;
import ru.rfsnab.orderservice.delivery.models.entity.Shipment;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentDocument;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentPackage;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentPayer;
import ru.rfsnab.orderservice.delivery.models.entity.enums.CarrierCode;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentDirection;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentPayerRole;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentStatusCode;
import ru.rfsnab.orderservice.models.entity.Order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ShipmentMapper")
class ShipmentMapperTest {

    private final ShipmentMapper mapper = Mappers.getMapper(ShipmentMapper.class);

    private static Carrier carrier(String template) {
        return Carrier.builder().code(CarrierCode.DELLIN).name("Деловые Линии").trackingUrlTemplate(template).build();
    }

    private static Shipment.ShipmentBuilder shipment() {
        return Shipment.builder().id(1L).carrierCode(CarrierCode.DELLIN).waybillNumber("12-34");
    }

    @Test
    @DisplayName("toDto: carrier null — название = код ТК, ссылки нет; заказа нет — orderId/orderNumber null")
    void nullCarrierAndOrder() {
        ShipmentDto dto = mapper.toDto(shipment().build(), null);

        assertThat(dto.carrierName()).isEqualTo("DELLIN");
        assertThat(dto.trackingUrl()).isNull();
        assertThat(dto.orderId()).isNull();
        assertThat(dto.orderNumber()).isNull();
    }

    @Test
    @DisplayName("toDto: данные заказа и ТК, notFound по статусу")
    void fullMapping() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).orderNumber("RF-1").build();
        Shipment s = shipment().order(order).carrierStatusCode(ShipmentStatusCode.NOT_FOUND).build();

        ShipmentDto dto = mapper.toDto(s, carrier("https://www.dellin.ru/tracker/orders/{number}/"));

        assertThat(dto.orderId()).isEqualTo(orderId);
        assertThat(dto.orderNumber()).isEqualTo("RF-1");
        assertThat(dto.carrierName()).isEqualTo("Деловые Линии");
        assertThat(dto.notFound()).isTrue();
        assertThat(dto.trackingUrl()).isEqualTo("https://www.dellin.ru/tracker/orders/12-34/");
    }

    @Test
    @DisplayName("toDto: документы отдаются без ref")
    void documentsWithoutRef() {
        Shipment s = shipment().documents(List.of(new ShipmentDocument("c1", "Счёт", "secret-ref"))).build();

        ShipmentDto dto = mapper.toDto(s, null);

        assertThat(dto.documents()).singleElement().isEqualTo(new ShipmentDocumentDto("c1", "Счёт"));
    }

    @Test
    @DisplayName("toDto: места и плательщики маппятся в DTO")
    void packagesAndPayersMapped() {
        Shipment s = shipment()
                .packages(List.of(new ShipmentPackage("P1", new BigDecimal("1.5"), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TWO)))
                .payers(List.of(new ShipmentPayer("ООО А", "7700", ShipmentPayerRole.THIRD))).build();

        ShipmentDto dto = mapper.toDto(s, null);

        assertThat(dto.packages()).containsExactly(
                new ShipmentPackageDto("P1", new BigDecimal("1.5"), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TWO));
        assertThat(dto.payers()).containsExactly(new ShipmentPayerDto("ООО А", "7700", ShipmentPayerRole.THIRD));
    }

    @Test
    @DisplayName("toDto: null-списки отдаются пустыми")
    void nullListsBecomeEmpty() {
        ShipmentDto dto = mapper.toDto(shipment().build(), null);

        assertThat(dto.documents()).isEmpty();
        assertThat(dto.packages()).isEmpty();
        assertThat(dto.payers()).isEmpty();
    }

    @Test
    @DisplayName("toClientDto: скрыты направление, контрагенты, стоимость, оплата, документы и плательщики")
    void clientDtoHidesFields() {
        Shipment s = shipment().direction(ShipmentDirection.OUTGOING).sender("ООО Мы").senderInn("7700")
                .receiver("ИП Клиент").receiverInn("5400").cost(BigDecimal.TEN).paid(true).routeFrom("Москва")
                .documents(List.of(new ShipmentDocument("c1", "Счёт", "ref"))).build();

        ShipmentDto dto = mapper.toClientDto(mapper.toDto(s, carrier(null)));

        assertThat(dto.direction()).isNull();
        assertThat(dto.sender()).isNull();
        assertThat(dto.senderInn()).isNull();
        assertThat(dto.receiver()).isNull();
        assertThat(dto.receiverInn()).isNull();
        assertThat(dto.cost()).isNull();
        assertThat(dto.paid()).isNull();
        assertThat(dto.documents()).isEmpty();
        assertThat(dto.payers()).isEmpty();
        assertThat(dto.routeFrom()).isEqualTo("Москва");
        assertThat(dto.waybillNumber()).isEqualTo("12-34");
    }

    @Test
    @DisplayName("toClientDto: места, маршрут и ссылка «Отследить» сохраняются")
    void clientDtoKeepsPackagesRouteAndTrackingUrl() {
        Shipment s = shipment().routeFrom("Москва").routeTo("Омск")
                .packages(List.of(new ShipmentPackage("P1", null, null, null, null))).build();

        ShipmentDto dto = mapper.toClientDto(mapper.toDto(s, carrier("https://t.example/{number}")));

        assertThat(dto.packages()).containsExactly(new ShipmentPackageDto("P1", null, null, null, null));
        assertThat(dto.routeFrom()).isEqualTo("Москва");
        assertThat(dto.routeTo()).isEqualTo("Омск");
        assertThat(dto.trackingUrl()).isEqualTo("https://t.example/12-34");
    }

    @Test
    @DisplayName("trackingUrl: шаблон не задан — null")
    void trackingUrlWithoutTemplate() {
        assertThat(mapper.trackingUrl(carrier(null), "123")).isNull();
    }

    @Test
    @DisplayName("trackingUrl подставляет номер накладной в шаблон")
    void trackingUrlSubstitutesNumber() {
        assertThat(mapper.trackingUrl(carrier("https://www.dellin.ru/tracker/orders/{number}/"), "12-34"))
                .isEqualTo("https://www.dellin.ru/tracker/orders/12-34/");
    }

    @Test
    @DisplayName("trackingUrl кодирует номер накладной (не даёт подменить путь/параметры ссылки)")
    void trackingUrlEncodesNumber() {
        assertThat(mapper.trackingUrl(carrier("https://t.example/{number}"), "a/b?c=d e"))
                .isEqualTo("https://t.example/a%2Fb%3Fc%3Dd+e");
    }
}
