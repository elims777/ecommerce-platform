package ru.rfsnab.integrationservice.service.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.integrationservice.model.PendingOrder;
import ru.rfsnab.integrationservice.model.commerceml.CmlContragent;
import ru.rfsnab.integrationservice.model.commerceml.CmlDocument;
import ru.rfsnab.integrationservice.model.commerceml.CmlOrderProduct;
import ru.rfsnab.integrationservice.model.commerceml.CommerceInfo;
import ru.rfsnab.integrationservice.repository.PendingOrderRepository;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Формирование XML заказов для выгрузки в 1С.
 * Вызывается из контроллера при type=sale, mode=query.
 * 1С забирает XML → при mode=success помечаем заказы как exported.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExportService {

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final PendingOrderRepository repository;
    private final JAXBContext commerceMlJaxbContext;
    private final ObjectMapper objectMapper;

    /** Результат выгрузки заказов: XML для 1С + количество заказов. */
    public record OrderExportResult(String xml, int count) {}

    /**
     * Формирует CommerceML XML из непереданных заказов.
     * Вызывается при type=sale, mode=query.
     * @return OrderExportResult с XML строкой и количеством заказов
     */
    @Transactional
    public OrderExportResult exportPendingOrders() {
        List<PendingOrder> orders = repository.findByExportedFalseOrderByCreatedAtAsc();

        if(orders.isEmpty()){
            log.info("Нет заказов для выгрузки в 1С");
            return new OrderExportResult(buildEmptyResponse(), 0);
        }

        log.info("Выгрузка {} заказов для 1С", orders.size());

        CommerceInfo commerceInfo = new CommerceInfo();
        commerceInfo.setSchemaVersion("2.10");
        commerceInfo.setFormationDate(LocalDateTime.now().format(DATETIME_FORMAT));

        List<CmlDocument> documents = new ArrayList<>();
        for(PendingOrder pendingOrder:orders){
            try{
                CmlDocument doc = buildDocument(pendingOrder);
                documents.add(doc);
            } catch (Exception e){
                log.error("Ошибка формирования XML для заказа {}: {}",
                        pendingOrder.getOrderId(), e.getMessage(), e);
            }
        }
        commerceInfo.setDocuments(documents);
        return new OrderExportResult(marshalToXml(commerceInfo), orders.size());
    }

    /**
     * Помечает все непереданные заказы как exported.
     * Вызывается при type=sale, mode=success (1С подтвердила получение).
     * @return количество помеченных заказов
     */
    @Transactional
    public int markOrdersAsExported() {
        int count = repository.markAllAsExported(LocalDateTime.now());
        log.info("Помечено {} заказов как exported", count);
        return count;
    }

    /**
     * Строит CmlDocument из JSON данных заказа (order_data из pending_orders).
     */
    private CmlDocument buildDocument(PendingOrder pendingOrder){
        try{
            JsonNode json = objectMapper.readTree(pendingOrder.getOrderData());

            CmlDocument doc = new CmlDocument();
            doc.setId(json.get("orderId").asText());
            doc.setNumber(json.get("orderNumber").asText());
            doc.setCurrency("RUB");
            doc.setTotalAmount(json.get("totalAmount").asText());
            doc.setRole("Покупатель");

            //Дата
            if(json.has("createdAt") && !json.get("createdAt").isNull()){
                String createdAt = json.get("createdAt").asText();
                doc.setDate(createdAt.substring(0,10)); //yyyy-MM-dd
            }

            doc.setOperation("Заказ товара");
            doc.setRate("1");
            // Время
            if (json.has("createdAt") && !json.get("createdAt").isNull()) {
                String createdAt = json.get("createdAt").asText();
                if (createdAt.length() > 10) {
                    doc.setTime(createdAt.substring(11, 19)); // HH:mm:ss
                }
            }

            //Комментарий
            if(json.has("comment") && !json.get("comment").isNull()){
                doc.setComment(json.get("comment").asText());
            }

            //Контрагент
            doc.setContragents(List.of(buidContragent(json)));

            //Товары
            doc.setProducts(buildProducts(json));

            //Способ оплаты, способ доставки
            doc.setRequisiteValues(buildRequisites(json));

            return doc;
        } catch (Exception e){
            throw  new RuntimeException("Ошибка парсинга order_data для заказа " +
                    pendingOrder.getOrderId(), e);
        }
    }

    private CmlContragent buidContragent(JsonNode json){
        CmlContragent contragent = new CmlContragent();
        contragent.setId(json.get("userId").asText());

        //ФИО или fallback на email
        String name = getTextOrNull(json, "recipientName");
        contragent.setName(name != null ? name : json.get("customerEmail").asText());

        contragent.setEmail(getTextOrNull(json, "customerEmail"));
        contragent.setPhone(getTextOrNull(json, "recipientPhone"));

        // Адрес
        String city = getTextOrNull(json, "city");
        String street = getTextOrNull(json, "street");
        String building = getTextOrNull(json, "building");
        if (city != null) {
            contragent.setPostalAddress(
                    String.join(", ", nonNull(city), nonNull(street), nonNull(building)).trim());
        }

        return contragent;
    }

    private List<CmlOrderProduct> buildProducts(JsonNode json) {
        List<CmlOrderProduct> products = new ArrayList<>();

        JsonNode items = json.get("items");
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                CmlOrderProduct product = new CmlOrderProduct();
                product.setId(getTextOrNull(item, "externalId"));
                product.setName(item.get("productName").asText());
                product.setQuantity(item.get("quantity").asText());
                product.setPricePerUnit(item.get("price").asText());
                product.setCatalogId(getTextOrNull(item, "externalId")); // тот же externalId
                product.setBaseUnit("шт");

                // Сумма = цена * количество
                BigDecimal price = new BigDecimal(item.get("price").asText());
                int qty = item.get("quantity").asInt();
                product.setSubtotal(price.multiply(BigDecimal.valueOf(qty)).toPlainString());

                products.add(product);
            }
        }

        return products;
    }

    private List<ru.rfsnab.integrationservice.model.commerceml.RequisiteValue> buildRequisites(JsonNode json) {
        List<ru.rfsnab.integrationservice.model.commerceml.RequisiteValue> requisites = new ArrayList<>();

        // Способ доставки
        String deliveryMethod = getTextOrNull(json, "deliveryMethod");
        if (deliveryMethod != null) {
            var req = new ru.rfsnab.integrationservice.model.commerceml.RequisiteValue();
            req.setName("Способ доставки");
            req.setValue(deliveryMethod);
            requisites.add(req);
        }

        // Способ оплаты
        String paymentMethod = getTextOrNull(json, "paymentMethod");
        if (paymentMethod != null) {
            var req = new ru.rfsnab.integrationservice.model.commerceml.RequisiteValue();
            req.setName("Способ оплаты");
            req.setValue(paymentMethod);
            requisites.add(req);
        }

        return requisites;
    }

    // ==================== Marshalling ====================

    private String marshalToXml(CommerceInfo commerceInfo) {
        try {
            Marshaller marshaller = commerceMlJaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");

            StringWriter writer = new StringWriter();
            marshaller.marshal(commerceInfo, writer);
            return writer.toString();

        } catch (JAXBException e) {
            log.error("Ошибка маршаллинга CommerceML XML", e);
            throw new RuntimeException("Ошибка формирования XML для 1С", e);
        }
    }

    private String buildEmptyResponse() {
        CommerceInfo empty = new CommerceInfo();
        empty.setSchemaVersion("2.10");
        empty.setFormationDate(LocalDateTime.now().format(DATETIME_FORMAT));
        empty.setDocuments(List.of());
        return marshalToXml(empty);
    }


    private String getTextOrNull(JsonNode json, String field) {
        if (json.has(field) && !json.get(field).isNull()) {
            return json.get(field).asText();
        }
        return null;
    }

    private String nonNull(String value) {
        return value != null ? value : "";
    }
}