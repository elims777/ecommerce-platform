package ru.rfsnab.orderservice.delivery.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;
import ru.rfsnab.orderservice.delivery.models.dto.ShipmentDocumentDto;
import ru.rfsnab.orderservice.delivery.models.dto.ShipmentDto;
import ru.rfsnab.orderservice.delivery.models.dto.ShipmentPackageDto;
import ru.rfsnab.orderservice.delivery.models.dto.ShipmentPayerDto;
import ru.rfsnab.orderservice.delivery.models.entity.Carrier;
import ru.rfsnab.orderservice.delivery.models.entity.Shipment;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentDocument;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentPackage;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentPayer;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

// documents идут через iterable-метод, defaultExpression на нём не срабатывает — null-список отдаём пустым стратегией
@Mapper(componentModel = "spring", imports = List.class,
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface ShipmentMapper {

    /** Собирает DTO; carrier нужен для названия ТК и ссылки «Отследить» (null — название = код, ссылки нет). */
    @Mapping(target = "orderId", source = "shipment.order.id")
    @Mapping(target = "orderNumber", source = "shipment.order.orderNumber")
    @Mapping(target = "carrierName",
            expression = "java(carrier == null ? shipment.getCarrierCode().name() : carrier.getName())")
    @Mapping(target = "notFound",
            expression = "java(shipment.getCarrierStatusCode() == ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentStatusCode.NOT_FOUND)")
    @Mapping(target = "trackingUrl",
            expression = "java(carrier == null ? null : trackingUrl(carrier, shipment.getWaybillNumber()))")
    // фронт опирается на .length: у старых записей списков нет — отдаём пустые
    @Mapping(target = "documents", source = "shipment.documents")
    @Mapping(target = "packages", source = "shipment.packages", defaultExpression = "java(List.of())")
    @Mapping(target = "payers", source = "shipment.payers", defaultExpression = "java(List.of())")
    ShipmentDto toDto(Shipment shipment, Carrier carrier);

    ShipmentDocumentDto toDocumentDto(ShipmentDocument document);

    ShipmentPackageDto toPackageDto(ShipmentPackage shipmentPackage);

    ShipmentPayerDto toPayerDto(ShipmentPayer payer);

    /** Копия для клиента: без контрагентов и направления накладной (они только для менеджера). */
    @Mapping(target = "direction", ignore = true)
    @Mapping(target = "sender", ignore = true)
    @Mapping(target = "senderInn", ignore = true)
    @Mapping(target = "receiver", ignore = true)
    @Mapping(target = "receiverInn", ignore = true)
    // стоимость перевозки, оплата и наши документы ТК (счета) клиенту не показываем
    @Mapping(target = "cost", ignore = true)
    @Mapping(target = "paid", ignore = true)
    @Mapping(target = "documents", expression = "java(List.of())")
    @Mapping(target = "payers", expression = "java(List.of())")
    ShipmentDto toClientDto(ShipmentDto dto);

    /** Ссылка «Отследить» для накладной; null, если шаблон не задан. */
    default String trackingUrl(Carrier carrier, String waybillNumber) {
        String template = carrier.getTrackingUrlTemplate();
        return template == null ? null : template.replace("{number}", URLEncoder.encode(waybillNumber, StandardCharsets.UTF_8));
    }
}
