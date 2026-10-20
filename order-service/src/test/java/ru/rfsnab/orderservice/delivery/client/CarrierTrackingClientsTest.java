package ru.rfsnab.orderservice.delivery.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import ru.rfsnab.orderservice.delivery.exception.CarrierApiException;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentDocument;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentPackage;
import ru.rfsnab.orderservice.delivery.models.entity.ShipmentPayer;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentPayerRole;
import ru.rfsnab.orderservice.delivery.models.entity.enums.ShipmentStatusCode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Статусы и журнал СДЭК и Деловых Линий на образцах JSON. Образцы собраны по документации и памяти об API:
 * реальных ответов нет, формат нужно сверить.
 */
@DisplayName("Клиенты ТК: статусы накладных и журнал")
class CarrierTrackingClientsTest {

    private static final String CDEK_TOKEN_OK = "{\"access_token\":\"tok\"}";

    @Test
    @DisplayName("СДЭК: журнала в API v2 нет — не поддерживается, вызов — CarrierApiException")
    void cdekJournalUnsupported() {
        CarrierClient cdek = new CdekClient(RestClient.builder(), "https://api.cdek.ru/v2", "id", "secret");

        assertThat(cdek.supportsJournal()).isFalse();
        assertThatThrownBy(() -> cdek.fetchJournal(Instant.now())).isInstanceOf(CarrierApiException.class);
    }

    @Nested
    @DisplayName("СДЭК")
    class Cdek {

        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final CdekClient client = new CdekClient(builder, "https://api.cdek.ru/v2", "id", "secret");

        private void expectToken() {
            server.expect(requestTo("https://api.cdek.ru/v2/oauth/token"))
                    .andRespond(withSuccess(CDEK_TOKEN_OK, MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("последний по date_time статус, откуда/куда; журнала нет")
        void readsLatestStatus() {
            expectToken();
            server.expect(requestTo("https://api.cdek.ru/v2/orders?cdek_number=1234567890"))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header("Authorization", "Bearer tok"))
                    .andRespond(withSuccess("""
                            {"entity":{"cdek_number":1234567890,
                              "from_location":{"city":"Новосибирск"},"to_location":{"city":"Томск"},
                              "statuses":[
                                {"code":"ACCEPTED_AT_PICK_UP_POINT","name":"Прибыл в пункт выдачи","date_time":"2026-10-12T10:00:00+0000"},
                                {"code":"CREATED","name":"Создан","date_time":"2026-10-10T08:00:00+0000"}]},
                             "requests":[]}
                            """, MediaType.APPLICATION_JSON));

            Map<String, CarrierShipmentStatus> result = client.fetchStatuses(List.of("1234567890"));

            assertThat(result.get("1234567890")).usingRecursiveComparison().ignoringFields("cargo").isEqualTo(new CarrierShipmentStatus(
                    ShipmentStatusCode.ARRIVED_AT_TERMINAL, "Прибыл в пункт выдачи", "Новосибирск", "Томск", null));
            assertThat(client.supportsStatusTracking()).isTrue();
            assertThat(client.supportsJournal()).isFalse();
            server.verify();
        }

        @Test
        @DisplayName("груз: вес г→кг, объём из габаритов, места, сумма, режим «склад-дверь», дата выдачи, накладная и штрихкоды")
        void cargoFromPackages() {
            expectToken();
            server.expect(requestTo("https://api.cdek.ru/v2/orders?cdek_number=1234567890"))
                    .andRespond(withSuccess("""
                            {"entity":{"cdek_number":1234567890,"delivery_mode":3,
                              "delivery_detail":{"total_sum":450.5,"date":"2026-10-12"},
                              "packages":[
                                {"number":"1","weight":1500,"length":30,"width":20,"height":10},
                                {"number":"2","weight":2500,"length":40,"width":30,"height":20}],
                              "statuses":[{"code":"DELIVERED","name":"Вручен","date_time":"2026-10-12T10:00:00+0000"}]},
                             "requests":[],
                             "related_entities":[
                               {"type":"waybill","url":"https://api.cdek.ru/v2/print/orders/11111111-2222-3333-4444-555555555555.pdf"},
                               {"type":"barcode","url":"https://api.cdek.ru/v2/print/barcodes/11111111-2222-3333-4444-555555555555.pdf"},
                               {"type":"direct_order","cdek_number":987}]}
                            """, MediaType.APPLICATION_JSON));

            ShipmentCargo cargo = client.fetchStatuses(List.of("1234567890")).get("1234567890").cargo();

            assertThat(cargo.weightKg()).isEqualByComparingTo(new BigDecimal("4.0"));
            assertThat(cargo.volumeM3()).isEqualByComparingTo(new BigDecimal("0.030"));
            assertThat(cargo.placesCount()).isEqualTo(2);
            assertThat(cargo.cost()).isEqualByComparingTo(new BigDecimal("450.5"));
            assertThat(cargo.paid()).isNull();
            assertThat(cargo.fromAddress()).isFalse();
            assertThat(cargo.toAddress()).isTrue();
            assertThat(cargo.deliveredAt()).isEqualTo(Instant.parse("2026-10-12T00:00:00Z"));
            assertThat(cargo.packages()).hasSize(2);
            assertThat(cargo.packages().get(0).weightKg()).isEqualByComparingTo(new BigDecimal("1.5"));
            assertThat(cargo.packages().get(0).lengthCm()).isEqualByComparingTo(new BigDecimal("30"));
            assertThat(cargo.documents()).extracting(ShipmentDocument::code, ShipmentDocument::name)
                    .containsExactly(tuple("waybill", "Накладная"), tuple("barcode", "Штрихкоды"));
            server.verify();
        }

        @Test
        @DisplayName("груз: без packages и режима — всё null, не падает")
        void cargoAbsent() {
            expectToken();
            server.expect(requestTo("https://api.cdek.ru/v2/orders?cdek_number=5"))
                    .andRespond(withSuccess("{\"entity\":{\"statuses\":[{\"code\":\"CREATED\"}]}}", MediaType.APPLICATION_JSON));

            ShipmentCargo cargo = client.fetchStatuses(List.of("5")).get("5").cargo();

            assertThat(cargo.weightKg()).isNull();
            assertThat(cargo.placesCount()).isNull();
            assertThat(cargo.fromAddress()).isNull();
            assertThat(cargo.documents()).isNull();
            assertThat(cargo.packages()).isNull();
        }

        @Test
        @DisplayName("скачивание: GET по url документа с Bearer-токеном")
        void downloadsDocument() {
            expectToken();
            byte[] pdf = "%PDF-cdek".getBytes(StandardCharsets.UTF_8);
            server.expect(requestTo("https://api.cdek.ru/v2/print/orders/abc.pdf"))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header("Authorization", "Bearer tok"))
                    .andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF));

            assertThat(client.downloadDocument(new ShipmentDocument("waybill", "Накладная", "https://api.cdek.ru/v2/print/orders/abc.pdf")))
                    .isEqualTo(pdf);
            server.verify();
        }

        @Test
        @DisplayName("скачивание: ссылка на чужой хост - токен не отправляется, CarrierApiException")
        void refusesForeignHost() {
            assertThatThrownBy(() -> client.downloadDocument(
                    new ShipmentDocument("waybill", "Накладная", "https://evil.example/print.pdf")))
                    .isInstanceOf(CarrierApiException.class);
            server.verify();
        }

        @Test
        @DisplayName("скачивание: ссылка по http - токен не отправляется, CarrierApiException")
        void refusesHttp() {
            assertThatThrownBy(() -> client.downloadDocument(
                    new ShipmentDocument("waybill", "Накладная", "http://api.cdek.ru/v2/print/orders/abc.pdf")))
                    .isInstanceOf(CarrierApiException.class);
            server.verify();
        }

        @Test
        @DisplayName("404 и другие 4xx кроме авторизации — накладной нет в карте, остальные накладные читаются")
        void unknownWaybillIsAbsent() {
            expectToken();
            server.expect(requestTo("https://api.cdek.ru/v2/orders?cdek_number=111"))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND));
            server.expect(requestTo("https://api.cdek.ru/v2/orders?cdek_number=222"))
                    .andRespond(withSuccess("{\"entity\":{\"statuses\":[{\"code\":\"DELIVERED\",\"name\":\"Вручен\"}]}}",
                            MediaType.APPLICATION_JSON));

            Map<String, CarrierShipmentStatus> result = client.fetchStatuses(List.of("111", "222"));

            assertThat(result).containsOnlyKeys("222");
            assertThat(result.get("222").code()).isEqualTo(ShipmentStatusCode.DELIVERED);
        }

        @Test
        @DisplayName("неизвестный код статуса: текст сохраняется, код null")
        void unknownStatusKeepsTextWithoutCode() {
            expectToken();
            server.expect(requestTo("https://api.cdek.ru/v2/orders?cdek_number=5"))
                    .andRespond(withSuccess("{\"entity\":{\"statuses\":[{\"code\":\"BRAND_NEW\",\"name\":\"Новый статус\"}]}}",
                            MediaType.APPLICATION_JSON));

            CarrierShipmentStatus status = client.fetchStatuses(List.of("5")).get("5");

            assertThat(status.code()).isNull();
            assertThat(status.rawText()).isEqualTo("Новый статус");
        }

        @Test
        @DisplayName("401 на запросе накладной — исключение, а не «не найдено»")
        void authErrorPropagates() {
            expectToken();
            server.expect(requestTo("https://api.cdek.ru/v2/orders?cdek_number=5"))
                    .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

            assertThatThrownBy(() -> client.fetchStatuses(List.of("5"))).isInstanceOf(HttpClientErrorException.class);
        }

        @Test
        @DisplayName("таблица сопоставления: ключевые коды СДЭК")
        void statusTable() {
            assertThat(CdekClient.normalizeStatus("CREATED")).isEqualTo(ShipmentStatusCode.CREATED);
            assertThat(CdekClient.normalizeStatus("ACCEPTED")).isEqualTo(ShipmentStatusCode.CREATED);
            assertThat(CdekClient.normalizeStatus("ACCEPTED_AT_RECIPIENT_CITY_WAREHOUSE")).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
            assertThat(CdekClient.normalizeStatus("ACCEPTED_AT_PICK_UP_POINT")).isEqualTo(ShipmentStatusCode.ARRIVED_AT_TERMINAL);
            assertThat(CdekClient.normalizeStatus("POSTOMAT_POSTED")).isEqualTo(ShipmentStatusCode.ARRIVED_AT_TERMINAL);
            assertThat(CdekClient.normalizeStatus("DELIVERED")).isEqualTo(ShipmentStatusCode.DELIVERED);
            assertThat(CdekClient.normalizeStatus("POSTOMAT_RECEIVED")).isEqualTo(ShipmentStatusCode.DELIVERED);
            assertThat(CdekClient.normalizeStatus("REMOVED")).isEqualTo(ShipmentStatusCode.CANCELLED);
            assertThat(CdekClient.normalizeStatus("NOT_DELIVERED")).isEqualTo(ShipmentStatusCode.CANCELLED);
            assertThat(CdekClient.normalizeStatus("POSTOMAT_SEIZED")).isEqualTo(ShipmentStatusCode.PROBLEM);
            assertThat(CdekClient.normalizeStatus("INVALID")).isNull();
            assertThat(CdekClient.normalizeStatus("???")).isNull();
            assertThat(CdekClient.normalizeStatus(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Деловые Линии")
    class Dellin {

        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final DellinClient client = new DellinClient(builder, "https://api.dellin.ru", "appkey", "login", "password");

        private void expectLogin() {
            server.expect(requestTo("https://api.dellin.ru/v3/auth/login.json"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().string(containsString("\"login\":\"login\"")))
                    .andRespond(withSuccess("{\"data\":{\"sessionID\":\"sess\"}}", MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("без логина/пароля ЛК статусы и журнал не поддерживаются")
        void notSupportedWithoutAccountCredentials() {
            DellinClient noAccount = new DellinClient(RestClient.builder(), "https://api.dellin.ru", "appkey", "", "");

            assertThat(noAccount.isConfigured()).isTrue();
            assertThat(noAccount.supportsStatusTracking()).isFalse();
            assertThat(noAccount.supportsJournal()).isFalse();
            assertThat(client.supportsStatusTracking()).isTrue();
            assertThat(client.supportsJournal()).isTrue();
        }

        @Test
        @DisplayName("статусы: логин, пакетный запрос; накладной нет в ответе — нет в карте")
        void readsStatuses() {
            expectLogin();
            server.expect(requestTo("https://api.dellin.ru/v3/orders.json"))
                    .andExpect(content().string(containsString("\"sessionID\":\"sess\"")))
                    .andExpect(content().string(containsString("\"docIds\"")))
                    .andRespond(withSuccess("""
                            {"orders":[
                              {"docId":"A1","state":"arrived","stateName":"Прибыл на терминал","derivalCity":"Москва","arrivalCity":"Новосибирск"},
                              {"orderId":"B2","state":"weird_state","stateName":"Что-то новое"}]}
                            """, MediaType.APPLICATION_JSON));

            Map<String, CarrierShipmentStatus> result = client.fetchStatuses(List.of("A1", "B2", "C3"));

            assertThat(result).containsOnlyKeys("A1", "B2");
            assertThat(result.get("A1")).usingRecursiveComparison().ignoringFields("cargo").isEqualTo(new CarrierShipmentStatus(
                    ShipmentStatusCode.ARRIVED_AT_TERMINAL, "Прибыл на терминал", "Москва", "Новосибирск", null));
            assertThat(result.get("B2").code()).isNull();
            assertThat(result.get("B2").rawText()).isEqualTo("Что-то новое");
            server.verify();
        }

        /** Урезанный и обезличенный реальный ответ /v3/orders.json: числа строками, накладная с режимами печати. */
        private static final String LIVE_ORDER = """
                {"orderNumber":"1001","orderId":"67000001","state":"inway","stateName":"Груз в пути",
                 "derival":{"city":"г. Москва"},"arrival":{"city":"Сыктывкар"},
                 "payer":{"name":"ООО Плательщик","inn":"7700000001"},
                 "freight":{"weight":"43.0","volume":"0.27","places":4},"cargoPlaces":[],
                 "totalSum":"1981.0","isPaid":true,
                 "orderDates":{"finish":null},
                 "orderedDeliveryFromAddress":false,"orderedDeliveryToAddress":false,
                 "documents":[
                   {"id":67000001,"uid":null,"type":"request","availableDocs":[]},
                   {"id":"26-00000000001","uid":"0x8126e315b6ddfd1649f2edb687fc522c","type":"shipping","availableDocs":["order","bill"]}]}
                """;

        @Test
        @DisplayName("статусы: груз из реального ответа — строковые числа, места, оплата, режим, документы с ref uid|режим")
        void statusCarriesCargo() {
            expectLogin();
            server.expect(requestTo("https://api.dellin.ru/v3/orders.json"))
                    .andExpect(content().string(containsString("\"docIds\":[\"67000001\"]")))
                    .andRespond(withSuccess("{\"orders\":[" + LIVE_ORDER + "]}", MediaType.APPLICATION_JSON));

            ShipmentCargo cargo = client.fetchStatuses(List.of("67000001")).get("67000001").cargo();

            assertThat(cargo.weightKg()).isEqualByComparingTo(new BigDecimal("43.0"));
            assertThat(cargo.volumeM3()).isEqualByComparingTo(new BigDecimal("0.27"));
            assertThat(cargo.placesCount()).isEqualTo(4);
            assertThat(cargo.cost()).isEqualByComparingTo(new BigDecimal("1981"));
            assertThat(cargo.paid()).isTrue();
            assertThat(cargo.fromAddress()).isFalse();
            assertThat(cargo.toAddress()).isFalse();
            assertThat(cargo.deliveredAt()).isNull();
            assertThat(cargo.packages()).isEmpty();
            assertThat(cargo.payers()).containsExactly(new ShipmentPayer("ООО Плательщик", "7700000001", null));
            assertThat(cargo.documents()).containsExactly(
                    new ShipmentDocument("order-26-00000000001", "Накладная 26-00000000001",
                            "0x8126e315b6ddfd1649f2edb687fc522c|order"),
                    new ShipmentDocument("bill-26-00000000001", "Счёт 26-00000000001",
                            "0x8126e315b6ddfd1649f2edb687fc522c|bill"));
            server.verify();
        }

        @Test
        @DisplayName("статусы: 7 накладных — два запроса docIds, не больше 5 номеров в каждом")
        void statusesAreBatchedByFive() {
            expectLogin();
            server.expect(requestTo("https://api.dellin.ru/v3/orders.json"))
                    .andExpect(content().string(containsString("\"D5\"")))
                    .andExpect(content().string(not(containsString("\"D6\""))))
                    .andRespond(withSuccess("{\"orders\":[{\"docId\":\"D1\",\"state\":\"inway\"}]}", MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://api.dellin.ru/v3/orders.json"))
                    .andExpect(content().string(containsString("\"D6\"")))
                    .andExpect(content().string(containsString("\"D7\"")))
                    .andRespond(withSuccess("{\"orders\":[{\"docId\":\"D7\",\"state\":\"arrived\"}]}", MediaType.APPLICATION_JSON));

            Map<String, CarrierShipmentStatus> result = client.fetchStatuses(List.of("D1", "D2", "D3", "D4", "D5", "D6", "D7"));

            assertThat(result).containsOnlyKeys("D1", "D7");
            server.verify();
        }

        @Test
        @DisplayName("пустой список накладных — без запроса к ТК")
        void emptyWaybillsNoRequest() {
            assertThat(client.fetchStatuses(List.of())).isEmpty();
            server.verify();
        }

        @Test
        @DisplayName("журнал: числа JSON-числами, дата выдачи у доставленного, места по cargoPlaces, две накладные — разные коды документов")
        void journalCargoNumericAndDelivered() {
            expectLogin();
            server.expect(requestTo("https://api.dellin.ru/v3/orders.json"))
                    .andRespond(withSuccess("""
                            {"orders":[{"orderId":"J9","state":"finished","freight":{"weight":2.5,"volume":0.01,"places":1},
                                        "totalSum":100.5,"isPaid":false,"orderedDeliveryFromAddress":true,"orderedDeliveryToAddress":true,
                                        "orderDates":{"finish":"2026-10-10 15:00:00"},
                                        "cargoPlaces":[{"number":"P-1","amount":1}],
                                        "documents":[
                                          {"id":"A-1","uid":"u1","type":"shipping","availableDocs":["order"]},
                                          {"id":"A-2","uid":"u2","type":"shipping","availableDocs":["order"]}]}]}
                            """, MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://api.dellin.ru/v3/orders.json"))
                    .andRespond(withSuccess("{\"orders\":[]}", MediaType.APPLICATION_JSON));

            ShipmentCargo cargo = client.fetchJournal(Instant.parse("2026-10-01T00:00:00Z")).get(0).cargo();

            assertThat(cargo.weightKg()).isEqualByComparingTo(new BigDecimal("2.5"));
            assertThat(cargo.cost()).isEqualByComparingTo(new BigDecimal("100.5"));
            assertThat(cargo.paid()).isFalse();
            assertThat(cargo.fromAddress()).isTrue();
            assertThat(cargo.toAddress()).isTrue();
            assertThat(cargo.deliveredAt()).isEqualTo(Instant.parse("2026-10-10T00:00:00Z"));
            assertThat(cargo.packages()).containsExactly(new ShipmentPackage("P-1", null, null, null, null));
            assertThat(cargo.documents()).extracting(ShipmentDocument::code).containsExactly("order-A-1", "order-A-2");
        }

        @Test
        @DisplayName("скачивание: логин, POST /v1/printable.json с docUID и режимом, PDF из data[0].base64")
        void downloadsDocument() {
            expectLogin();
            byte[] pdf = "%PDF-test".getBytes(StandardCharsets.UTF_8);
            server.expect(requestTo("https://api.dellin.ru/v1/printable.json"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().string(containsString("\"docUID\":\"0xabc\"")))
                    .andExpect(content().string(containsString("\"mode\":\"bill\"")))
                    .andExpect(content().string(containsString("\"sessionID\":\"sess\"")))
                    .andRespond(withSuccess("{\"data\":[{\"base64\":\"" + Base64.getEncoder().encodeToString(pdf) + "\"}]}",
                            MediaType.APPLICATION_JSON));

            byte[] result = client.downloadDocument(new ShipmentDocument("bill-1", "Счёт", "0xabc|bill"));

            assertThat(result).isEqualTo(pdf);
            server.verify();
        }

        @Test
        @DisplayName("скачивание: ответ без data[0].base64 или ref без режима — CarrierApiException")
        void downloadFailures() {
            expectLogin();
            server.expect(requestTo("https://api.dellin.ru/v1/printable.json"))
                    .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.downloadDocument(new ShipmentDocument("c", "n", "0xabc|bill")))
                    .isInstanceOf(CarrierApiException.class);
            assertThatThrownBy(() -> client.downloadDocument(new ShipmentDocument("c", "n", "0xabc")))
                    .isInstanceOf(CarrierApiException.class);
        }

        @Test
        @DisplayName("ответ без списка заказов — CarrierApiException, а не «все не найдены»")
        void statusesWithoutOrdersThrow() {
            expectLogin();
            server.expect(requestTo("https://api.dellin.ru/v3/orders.json"))
                    .andRespond(withSuccess("{\"errors\":[\"x\"]}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.fetchStatuses(List.of("A1"))).isInstanceOf(CarrierApiException.class);
        }

        @Test
        @DisplayName("логин без sessionID — CarrierApiException")
        void loginWithoutSessionThrows() {
            server.expect(requestTo("https://api.dellin.ru/v3/auth/login.json"))
                    .andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.fetchStatuses(List.of("A1"))).isInstanceOf(CarrierApiException.class);
        }

        @Test
        @DisplayName("журнал: стороны, ИНН, маршрут, даты, номер заказа клиента; повтор страницы останавливает загрузку")
        void readsJournal() {
            expectLogin();
            String page = """
                    {"orders":[{"orderId":"J1","state":"finished","stateName":"Доставлен",
                                "sender":{"name":"ООО Мы","inn":"7700000001"},"receiver":"ИП Клиент","receiverInn":"5400000002",
                                "derival":{"city":"Новосибирск"},"arrival":{"city":"Омск"},
                                "orderedAt":"2026-09-28 12:28:24","orderNumber":"3527",
                                "orderDates":{"derivalFromOspSender":"2026-10-01 09:00:00","arrivalToOspReceiver":"2026-10-05",
                                              "giveoutFromOspReceiver":"2026-10-06 00:00:00"}}]}
                    """;
            server.expect(requestTo("https://api.dellin.ru/v3/orders.json"))
                    .andExpect(content().string(containsString("\"page\":1")))
                    .andRespond(withSuccess(page, MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://api.dellin.ru/v3/orders.json"))
                    .andExpect(content().string(containsString("\"page\":2")))
                    .andRespond(withSuccess(page, MediaType.APPLICATION_JSON));

            List<JournalItem> result = client.fetchJournal(Instant.parse("2026-09-30T00:00:00Z"));

            assertThat(result).hasSize(1);
            JournalItem item = result.get(0);
            assertThat(item.waybillNumber()).isEqualTo("J1");
            assertThat(item.sender()).isEqualTo("ООО Мы");
            assertThat(item.senderInn()).isEqualTo("7700000001");
            assertThat(item.receiver()).isEqualTo("ИП Клиент");
            assertThat(item.receiverInn()).isEqualTo("5400000002");
            assertThat(item.routeFrom()).isEqualTo("Новосибирск");
            assertThat(item.routeTo()).isEqualTo("Омск");
            assertThat(item.shippedAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
            assertThat(item.estimatedDeliveryAt()).isEqualTo(Instant.parse("2026-10-05T00:00:00Z"));
            assertThat(item.siteOrderNumber()).isEqualTo("3527");
            assertThat(item.status().code()).isEqualTo(ShipmentStatusCode.DELIVERED);
            server.verify();
        }

        @Test
        @DisplayName("таблица сопоставления: ключевые состояния ДЛ, регистр и пробелы не важны")
        void statusTable() {
            assertThat(DellinClient.normalizeStatus("in_way")).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
            assertThat(DellinClient.normalizeStatus(" Arrived ")).isEqualTo(ShipmentStatusCode.ARRIVED_AT_TERMINAL);
            assertThat(DellinClient.normalizeStatus("finished")).isEqualTo(ShipmentStatusCode.DELIVERED);
            assertThat(DellinClient.normalizeStatus("draft")).isEqualTo(ShipmentStatusCode.CREATED);
            assertThat(DellinClient.normalizeStatus("waiting")).isEqualTo(ShipmentStatusCode.CREATED);
            assertThat(DellinClient.normalizeStatus("warehousing")).isEqualTo(ShipmentStatusCode.ARRIVED_AT_TERMINAL);
            assertThat(DellinClient.normalizeStatus("accompanying_documents_return")).isEqualTo(ShipmentStatusCode.DELIVERED);
            assertThat(DellinClient.normalizeStatus("declined")).isEqualTo(ShipmentStatusCode.CANCELLED);
            assertThat(DellinClient.normalizeStatus("problem")).isNull();
            assertThat(DellinClient.normalizeStatus("???")).isNull();
        }

        @Test
        @DisplayName("журнал: нет orderDates — отгрузка по orderedAt; нет arrivalToOspReceiver — giveoutFromOspReceiver; orderNumber null")
        void journalDateFallbacks() {
            expectLogin();
            server.expect(requestTo("https://api.dellin.ru/v3/orders.json"))
                    .andExpect(content().string(containsString("\"page\":1")))
                    .andRespond(withSuccess("""
                            {"orders":[{"orderId":"J2","state":"inway","orderedAt":"2026-09-28 12:28:24","orderNumber":null,
                                        "orderDates":{"giveoutFromOspReceiver":"2026-09-29 00:00:00"}}]}
                            """, MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://api.dellin.ru/v3/orders.json"))
                    .andRespond(withSuccess("{\"orders\":[]}", MediaType.APPLICATION_JSON));

            JournalItem item = client.fetchJournal(Instant.parse("2026-09-01T00:00:00Z")).get(0);

            assertThat(item.shippedAt()).isEqualTo(Instant.parse("2026-09-28T00:00:00Z"));
            assertThat(item.estimatedDeliveryAt()).isEqualTo(Instant.parse("2026-09-29T00:00:00Z"));
            assertThat(item.siteOrderNumber()).isNull();
            assertThat(item.status().code()).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
        }
    }

    @Nested
    @DisplayName("Возовоз")
    class Vozovoz {

        private static final String URL = "https://vozovoz.ru/api/?token=tok";

        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final VozovozClient client = new VozovozClient(builder, "https://vozovoz.ru", "tok");

        @Test
        @DisplayName("статусы: order.get с номерами; код по датам этапов и слову «отмен»; статус строкой или объектом")
        void readsStatuses() {
            server.expect(requestTo(URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().string(containsString("\"object\":\"order\"")))
                    .andExpect(content().string(containsString("\"number\":[\"700\",\"800\",\"900\",\"MY-1\"]")))
                    .andRespond(withSuccess("""
                            {"response":{"data":[
                              {"number":700,"id":"u-1","status":"Прибыл в город назначения",
                               "dates":{"created":"2026-10-01T10:00:00","taken":"2026-10-02T10:00:00","arrived":"2026-10-05T10:00:00","given":""},
                               "gateway":{"dispatch":{"point":{"location":{"name":"Москва"}}},"destination":{"point":{"location":{"name":"Омск"}}}}},
                              {"number":800,"status":"Заказ отменен","dates":{"created":"2026-10-01T10:00:00"}},
                              {"number":900,"customId":"MY-1","status":{"code":"given","message":"Выдан"},"dates":{"given":"2026-10-06T10:00:00"}}],
                             "meta":{"total":3}}}
                            """, MediaType.APPLICATION_JSON));

            Map<String, CarrierShipmentStatus> result = client.fetchStatuses(List.of("700", "800", "900", "MY-1"));

            assertThat(result).containsOnlyKeys("700", "800", "900", "MY-1");
            assertThat(result.get("700")).usingRecursiveComparison().ignoringFields("cargo").isEqualTo(new CarrierShipmentStatus(
                    ShipmentStatusCode.ARRIVED_AT_TERMINAL, "Прибыл в город назначения", "Москва", "Омск", null));
            assertThat(result.get("800").code()).isEqualTo(ShipmentStatusCode.CANCELLED);
            assertThat(result.get("900").code()).isEqualTo(ShipmentStatusCode.DELIVERED);
            assertThat(result.get("900").rawText()).isEqualTo("Выдан");
            assertThat(result.get("MY-1")).isEqualTo(result.get("900"));
            assertThat(client.supportsStatusTracking()).isTrue();
            assertThat(client.supportsJournal()).isTrue();
            server.verify();
        }

        /** Урезанный и обезличенный реальный ответ order.get. */
        private static final String LIVE_ORDER = """
                {"number":"261000001","id":"e82b3a6d-0000-0000-0000-000000000001","customId":"","status":"Заказ оформлен","isPaid":false,
                 "price":390,
                 "payer":{"company":"ООО Возовоз Плательщик","name":"Иван Контактов","inn":"5400000002"},
                 "dates":{"create":"2026-10-16T12:56:53","taken":"","shipped":"","arrived":"","given":"2026-10-20T10:00:00"},
                 "cargo":{"dimension":{"max":{"height":0.2,"length":0.2,"width":0.2,"weight":2.3},"quantity":1,"volume":0.02,"weight":2.3}},
                 "documentList":{
                   "markingList":"https://vozovoz.ru/api/document/?orderId=e82b3a6d&docType=markingList",
                   "lrc":"https://vozovoz.ru/api/document/?orderId=e82b3a6d&docType=lrc",
                   "etdmsKonturXml":"https://vozovoz.ru/api/document/?orderId=e82b3a6d&docType=etdmsKonturXml"},
                 "gateway":{"dispatch":{"point":{"location":{"name":"Москва"},"address":"г. Москва, ул. Тестовая, д. 1"}},
                            "destination":{"point":{"location":{"name":"Сыктывкар"},"terminal":{"id":"t-1","name":"Сыктывкар тест"}}}}}
                """;

        @Test
        @DisplayName("груз: размеры, цена, isPaid, адрес/терминал, дата выдачи, документы (XML для ЭДО пропущен)")
        void cargoFromLiveOrder() {
            server.expect(requestTo(URL))
                    .andRespond(withSuccess("{\"response\":{\"data\":[" + LIVE_ORDER + "],\"meta\":{\"total\":1}}}",
                            MediaType.APPLICATION_JSON));

            ShipmentCargo cargo = client.fetchStatuses(List.of("261000001")).get("261000001").cargo();

            assertThat(cargo.weightKg()).isEqualByComparingTo(new BigDecimal("2.3"));
            assertThat(cargo.volumeM3()).isEqualByComparingTo(new BigDecimal("0.02"));
            assertThat(cargo.placesCount()).isEqualTo(1);
            assertThat(cargo.cost()).isEqualByComparingTo(new BigDecimal("390"));
            assertThat(cargo.paid()).isFalse();
            assertThat(cargo.fromAddress()).isTrue();
            assertThat(cargo.toAddress()).isFalse();
            assertThat(cargo.deliveredAt()).isEqualTo(Instant.parse("2026-10-20T10:00:00Z"));
            assertThat(cargo.documents()).containsExactly(
                    new ShipmentDocument("markingList", "Маркировочный лист",
                            "https://vozovoz.ru/api/document/?orderId=e82b3a6d&docType=markingList"),
                    new ShipmentDocument("lrc", "Экспедиторская расписка",
                            "https://vozovoz.ru/api/document/?orderId=e82b3a6d&docType=lrc"));
            assertThat(cargo.packages()).isNull();
            assertThat(cargo.payers()).containsExactly(new ShipmentPayer("ООО Возовоз Плательщик", "5400000002", null));
        }

        @Test
        @DisplayName("скачивание: GET по ссылке из documentList")
        void downloadsDocument() {
            byte[] pdf = "%PDF-voz".getBytes(StandardCharsets.UTF_8);
            server.expect(requestTo("https://vozovoz.ru/api/document/?orderId=e82b3a6d&docType=lrc"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF));

            assertThat(client.downloadDocument(new ShipmentDocument("lrc", "Экспедиторская расписка",
                    "https://vozovoz.ru/api/document/?orderId=e82b3a6d&docType=lrc"))).isEqualTo(pdf);
            server.verify();
        }

        @Test
        @DisplayName("скачивание: поддомен vozovoz.ru разрешён")
        void downloadsFromSubdomain() {
            byte[] pdf = "%PDF-sub".getBytes(StandardCharsets.UTF_8);
            server.expect(requestTo("https://files.vozovoz.ru/doc.pdf")).andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF));

            assertThat(client.downloadDocument(new ShipmentDocument("lrc", "n", "https://files.vozovoz.ru/doc.pdf"))).isEqualTo(pdf);
            server.verify();
        }

        @Test
        @DisplayName("скачивание: чужой хост (в том числе похожий на vozovoz.ru) и http - CarrierApiException, запроса нет")
        void refusesForeignHostAndHttp() {
            for (String ref : List.of("https://evil.example/doc.pdf", "https://vozovoz.ru.evil.example/doc.pdf",
                    "https://notvozovoz.ru/doc.pdf", "http://vozovoz.ru/doc.pdf")) {
                assertThatThrownBy(() -> client.downloadDocument(new ShipmentDocument("lrc", "n", ref)))
                        .as(ref).isInstanceOf(CarrierApiException.class);
            }
            server.verify();
        }

        @Test
        @DisplayName("журнал: нет destination.point.date - estimatedDeliveryAt null, даже если есть dates.arrived")
        void journalWithoutPointDateHasNoEstimate() {
            server.expect(requestTo(URL))
                    .andRespond(withSuccess("""
                            {"response":{"data":[{"number":"700","dates":{"created":"2026-10-01T10:00:00","arrived":"2026-10-04T12:00:00"},
                              "gateway":{"destination":{"point":{"location":{"name":"Омск"}}}}}],"meta":{"total":1}}}
                            """, MediaType.APPLICATION_JSON));

            List<JournalItem> result = client.fetchJournal(Instant.parse("2026-10-01T00:00:00Z"));

            assertThat(result).singleElement().satisfies(item -> {
                assertThat(item.estimatedDeliveryAt()).isNull();
                assertThat(item.status().code()).isEqualTo(ShipmentStatusCode.ARRIVED_AT_TERMINAL);
            });
            server.verify();
        }

        @Test
        @DisplayName("заказ без дат этапов: код null; пустой массив — накладной нет в карте")
        void unknownStatusHasNoCode() {
            server.expect(requestTo(URL))
                    .andRespond(withSuccess("{\"response\":{\"data\":[{\"number\":5,\"status\":\"Нечто новое\"}],\"meta\":{\"total\":1}}}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(URL))
                    .andRespond(withSuccess("{\"response\":{\"data\":[],\"meta\":{\"total\":0}}}", MediaType.APPLICATION_JSON));

            assertThat(client.fetchStatuses(List.of("5")).get("5").code()).isNull();
            assertThat(client.fetchStatuses(List.of("6"))).isEmpty();
        }

        @Test
        @DisplayName("статусы: только дата создания — CREATED, дата забора или отгрузки — IN_TRANSIT")
        void createdAndTakenStatuses() {
            server.expect(requestTo(URL))
                    .andRespond(withSuccess("""
                            {"response":{"data":[
                              {"number":1,"status":"Заказ оформлен","dates":{"create":"2026-10-01T10:00:00","taken":"","shipped":""}},
                              {"number":2,"status":"Забран","dates":{"created":"2026-10-01T10:00:00","taken":"2026-10-02T10:00:00"}},
                              {"number":3,"status":"Отгружен","dates":{"create":"2026-10-01T10:00:00","shipped":"2026-10-03T10:00:00"}}],
                             "meta":{"total":3}}}
                            """, MediaType.APPLICATION_JSON));

            Map<String, CarrierShipmentStatus> result = client.fetchStatuses(List.of("1", "2", "3"));

            assertThat(result.get("1").code()).isEqualTo(ShipmentStatusCode.CREATED);
            assertThat(result.get("2").code()).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
            assertThat(result.get("3").code()).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
            server.verify();
        }

        @Test
        @DisplayName("журнал: фильтр по дате оформления, стороны (company важнее контактного name), ИНН, маршрут, даты, пользовательский номер; пагинация")
        void readsJournal() {
            server.expect(requestTo(URL))
                    .andExpect(content().string(containsString("\"from\":\"2026-10-01\"")))
                    .andExpect(content().string(containsString("\"offset\":0")))
                    .andRespond(withSuccess("""
                            {"response":{"data":[{"number":"700247224","customId":"AAB32389-00007","status":"Заказ оформлен",
                              "dates":{"created":"2026-10-01T16:43:18","shipped":"2026-10-02T09:00:00","arrived":"2026-10-04T12:00:00","given":""},
                              "gateway":{"dispatch":{"point":{"location":{"name":"Новосибирск"}},
                                                     "customer":{"companyName":"ООО Мы","inn":"7700000001"}},
                                         "destination":{"point":{"location":{"name":"Омск"},"date":"2026-10-06"},
                                                        "customer":{"company":"ООО Они","name":"Иван Иванов","inn":"5400000002"}}}}],
                             "meta":{"total":2}}}
                            """, MediaType.APPLICATION_JSON));
            server.expect(requestTo(URL))
                    .andExpect(content().string(containsString("\"offset\":1")))
                    .andRespond(withSuccess("{\"response\":{\"data\":[{\"number\":\"700247225\",\"dates\":{}}],\"meta\":{\"total\":2}}}",
                            MediaType.APPLICATION_JSON));

            List<JournalItem> result = client.fetchJournal(Instant.parse("2026-10-01T00:00:00Z"));

            assertThat(result).extracting(JournalItem::waybillNumber).containsExactly("700247224", "700247225");
            JournalItem item = result.get(0);
            assertThat(item.sender()).isEqualTo("ООО Мы");
            assertThat(item.senderInn()).isEqualTo("7700000001");
            assertThat(item.receiver()).isEqualTo("ООО Они");
            assertThat(item.receiverInn()).isEqualTo("5400000002");
            assertThat(item.routeFrom()).isEqualTo("Новосибирск");
            assertThat(item.routeTo()).isEqualTo("Омск");
            assertThat(item.shippedAt()).isEqualTo(Instant.parse("2026-10-02T09:00:00Z"));
            assertThat(item.estimatedDeliveryAt()).isEqualTo(Instant.parse("2026-10-06T00:00:00Z"));
            assertThat(item.siteOrderNumber()).isEqualTo("AAB32389-00007");
            assertThat(item.status().code()).isEqualTo(ShipmentStatusCode.ARRIVED_AT_TERMINAL);
            server.verify();
        }

        @Test
        @DisplayName("ответ без массива data или с ошибкой API — CarrierApiException")
        void badResponseThrows() {
            server.expect(requestTo(URL)).andRespond(withSuccess("{\"response\":{}}", MediaType.APPLICATION_JSON));
            server.expect(requestTo(URL)).andRespond(withSuccess("{\"error\":{\"message\":\"Токен не корректен\"}}",
                    MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.fetchStatuses(List.of("1"))).isInstanceOf(CarrierApiException.class);
            assertThatThrownBy(() -> client.fetchJournal(Instant.now())).isInstanceOf(CarrierApiException.class)
                    .hasMessageContaining("Токен не корректен");
        }

        @Test
        @DisplayName("пустой список накладных — без запроса к ТК")
        void emptyWaybillsNoRequest() {
            assertThat(client.fetchStatuses(List.of())).isEmpty();
            server.verify();
        }

        @Test
        @DisplayName("сетевая ошибка — CarrierApiException без URL и токена в сообщении")
        void httpErrorHidesToken() {
            server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

            assertThatThrownBy(() -> client.fetchStatuses(List.of("1"))).isInstanceOf(CarrierApiException.class)
                    .hasMessageNotContaining("token").hasMessageNotContaining("http");
        }

        @Test
        @DisplayName("бесконечная пагинация (нет total, страницы не кончаются) — CarrierApiException")
        void pageLimitThrows() {
            server.expect(ExpectedCount.manyTimes(), requestTo(URL))
                    .andRespond(withSuccess("{\"response\":{\"data\":[{\"number\":1}]}}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.fetchJournal(Instant.now())).isInstanceOf(CarrierApiException.class);
        }
    }

    @Nested
    @DisplayName("ПЭК")
    class Pec {

        private static final String STATUS_URL = "https://kabinet.pecom.ru/api/v1/cargos/status/";
        private static final String LIST_URL = "https://kabinet.pecom.ru/api/v1/cargos/listallorderbylogin/";

        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final PecClient client = new PecClient(builder, "https://kabinet.pecom.ru", "login", "key");

        @Test
        @DisplayName("статусы: POST пачками по 15 с Basic-авторизацией; «не найдены» (404 в теле) — накладных нет в карте")
        void readsStatusesInBatches() {
            List<String> waybills = IntStream.rangeClosed(1, 16).mapToObj(i -> "W" + i).toList();
            server.expect(requestTo(STATUS_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", "Basic bG9naW46a2V5"))
                    .andExpect(content().string(containsString("\"W15\"")))
                    .andExpect(content().string(not(containsString("\"W16\""))))
                    .andRespond(withSuccess("""
                            {"cargos":[
                              {"info":{"cargoStatus":"Прибыл"},"cargo":{"code":"W3"},
                               "sender":{"branchInfo":{"city":"Москва"}},"receiver":{"branch":{"city":"Омск"}}},
                              {"info":{"cargoStatus":"Выдан ( мест 1 из 2 )"},"cargo":{"code":"W5"}},
                              {"info":{"cargoStatus":"Нечто новое"},"cargo":{"code":"W7"}},
                              {"info":{"cargoStatus":"В пути"},"cargo":{"code":"OTHER"}}]}
                            """, MediaType.APPLICATION_JSON));
            server.expect(requestTo(STATUS_URL))
                    .andExpect(content().string(containsString("\"W16\"")))
                    .andRespond(withSuccess("{\"error\":{\"title\":\"Запрашиваемые вами грузы не найдены: W16\",\"status\":404}}",
                            MediaType.APPLICATION_JSON));

            Map<String, CarrierShipmentStatus> result = client.fetchStatuses(waybills);

            assertThat(result).containsOnlyKeys("W3", "W5", "W7");
            assertThat(result.get("W3")).usingRecursiveComparison().ignoringFields("cargo").isEqualTo(new CarrierShipmentStatus(
                    ShipmentStatusCode.ARRIVED_AT_TERMINAL, "Прибыл", "Москва", "Омск", null));
            assertThat(result.get("W5").code()).isEqualTo(ShipmentStatusCode.DELIVERED);
            assertThat(result.get("W7").code()).isNull();
            assertThat(client.supportsStatusTracking()).isTrue();
            assertThat(client.supportsJournal()).isTrue();
            server.verify();
        }

        @Test
        @DisplayName("пустой список накладных — без запроса к ТК")
        void emptyWaybillsNoRequest() {
            assertThat(client.fetchStatuses(List.of())).isEmpty();
            server.verify();
        }

        @Test
        @DisplayName("груз из /cargos/status/: вес, объём, места, сумма, долг=0 → оплачено, забор от адреса, штрихкоды мест, дата получения")
        void statusCarriesCargo() {
            server.expect(requestTo(STATUS_URL))
                    .andRespond(withSuccess("""
                            {"cargos":[{"info":{"cargoStatus":"Выдан получателю","deliveryPlanDate":"2026-10-19T00:00:00",
                                                "giveOutDateTime":"2026-10-18T09:00:00","receivedByClientDateTime":"2026-10-19T14:01:15"},
                                        "cargo":{"code":"W1","amount":3.0,"weight":26.0,"volume":0.25,
                                                 "positionBarCodes":["5558100025220001","5558100025220002","5558100025220003"]},
                                        "services":{"sum":450.0,"debt":0.0,"items":[
                                            {"payerType":1,"payerName":"ООО Отправитель"},{"payerType":1,"payerName":"ООО Отправитель"},
                                            {"payerType":3,"payerName":"ООО Третье"}]},
                                        "sender":{"intakeAddress":"Москва, Тестовая улица, 1","branchInfo":{"city":"Москва"}},
                                        "receiver":{"branch":{"city":"Омск"}}},
                                       {"info":{"cargoStatus":"В пути"},"cargo":{"code":"W2"},
                                        "services":{"sum":100.0,"debt":100.0},"sender":{"sender":"ООО Тест"}}]}
                            """, MediaType.APPLICATION_JSON));

            Map<String, CarrierShipmentStatus> result = client.fetchStatuses(List.of("W1", "W2"));

            ShipmentCargo cargo = result.get("W1").cargo();
            assertThat(cargo.weightKg()).isEqualByComparingTo(new BigDecimal("26"));
            assertThat(cargo.volumeM3()).isEqualByComparingTo(new BigDecimal("0.25"));
            assertThat(cargo.placesCount()).isEqualTo(3);
            assertThat(cargo.cost()).isEqualByComparingTo(new BigDecimal("450"));
            assertThat(cargo.paid()).isTrue();
            assertThat(cargo.fromAddress()).isTrue();
            assertThat(cargo.toAddress()).isTrue();
            assertThat(cargo.deliveredAt()).isEqualTo(Instant.parse("2026-10-19T14:01:15Z"));
            assertThat(cargo.packages()).extracting(ShipmentPackage::number)
                    .containsExactly("5558100025220001", "5558100025220002", "5558100025220003");
            assertThat(cargo.documents()).isNull();
            assertThat(cargo.payers()).containsExactly(
                    new ShipmentPayer("ООО Отправитель", null, ShipmentPayerRole.SENDER),
                    new ShipmentPayer("ООО Третье", null, ShipmentPayerRole.THIRD));
            ShipmentCargo second = result.get("W2").cargo();
            assertThat(second.payers()).isNull();
            assertThat(second.paid()).isFalse();
            assertThat(second.fromAddress()).isFalse();
            assertThat(second.toAddress()).isNull();
            assertThat(second.packages()).isNull();
        }

        @Test
        @DisplayName("ответ без массива cargos или другая ошибка в теле — CarrierApiException")
        void badResponseThrows() {
            server.expect(requestTo(STATUS_URL)).andRespond(withSuccess("{\"foo\":1}", MediaType.APPLICATION_JSON));
            server.expect(requestTo(STATUS_URL)).andRespond(withSuccess("{\"error\":{\"title\":\"Ошибка валидации\",\"status\":400}}",
                    MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.fetchStatuses(List.of("W1"))).isInstanceOf(CarrierApiException.class);
            assertThatThrownBy(() -> client.fetchStatuses(List.of("W1"))).isInstanceOf(CarrierApiException.class)
                    .hasMessageContaining("Ошибка валидации");
        }

        @Test
        @DisplayName("журнал: listallorderbylogin (selectBy 0, даты) → коды, ИНН, isIntake/isDelivery; остальное из /cargos/status/")
        void readsJournal() {
            server.expect(requestTo(LIST_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().string(containsString("\"selectBy\":0")))
                    .andExpect(content().string(containsString("\"dateBegin\":\"2026-10-01\"")))
                    .andRespond(withSuccess("""
                            {"cargos":[
                              {"code":"J1","orderNumber":"LIST-1",
                               "sender":{"isIntake":true,"counterpart":{"name":"ООО Мы (список)","legalEntity":{"inn":"7700000001"}}},
                               "receiver":{"isDelivery":false,"counterpart":{"name":"ИП Клиент (список)","legalEntity":{"inn":"5500000002"}}}},
                              {"code":"J2","sender":{"isIntake":false},"receiver":{"isDelivery":true}}]}
                            """, MediaType.APPLICATION_JSON));
            server.expect(requestTo(STATUS_URL))
                    .andExpect(content().string(containsString("\"J1\"")))
                    .andExpect(content().string(containsString("\"J2\"")))
                    .andRespond(withSuccess("""
                            {"cargos":[{"info":{"cargoStatus":"В пути","sendingDateTime":"2026-10-02T09:00:00",
                                                "arrivalPlanDateTime":"2026-10-05T08:00:00"},
                                        "cargo":{"code":"J1","orderNumber":"AAB32389-00007","weight":12.5,"amount":2.0},
                                        "services":{"sum":300.0,"debt":0.0},
                                        "sender":{"sender":"ООО Мы","branchInfo":{"city":"Москва"}},
                                        "receiver":{"receiver":"ИП Клиент","branch":{"city":"Омск"}}}]}
                            """, MediaType.APPLICATION_JSON));

            List<JournalItem> result = client.fetchJournal(Instant.parse("2026-10-01T00:00:00Z"));

            assertThat(result).extracting(JournalItem::waybillNumber).containsExactly("J1", "J2");
            JournalItem item = result.get(0);
            assertThat(item.sender()).isEqualTo("ООО Мы");
            assertThat(item.senderInn()).isEqualTo("7700000001");
            assertThat(item.receiver()).isEqualTo("ИП Клиент");
            assertThat(item.receiverInn()).isEqualTo("5500000002");
            assertThat(item.routeFrom()).isEqualTo("Москва");
            assertThat(item.routeTo()).isEqualTo("Омск");
            assertThat(item.shippedAt()).isEqualTo(Instant.parse("2026-10-02T09:00:00Z"));
            assertThat(item.estimatedDeliveryAt()).isEqualTo(Instant.parse("2026-10-05T08:00:00Z"));
            assertThat(item.siteOrderNumber()).isEqualTo("AAB32389-00007");
            assertThat(item.status().code()).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
            assertThat(item.cargo().weightKg()).isEqualByComparingTo(new BigDecimal("12.5"));
            assertThat(item.cargo().placesCount()).isEqualTo(2);
            assertThat(item.cargo().paid()).isTrue();
            assertThat(item.cargo().fromAddress()).isTrue();
            assertThat(item.cargo().toAddress()).isFalse();
            JournalItem notInStatus = result.get(1);
            assertThat(notInStatus.status()).isNull();
            assertThat(notInStatus.cargo().fromAddress()).isFalse();
            assertThat(notInStatus.cargo().toAddress()).isTrue();
            server.verify();
        }

        @Test
        @DisplayName("журнал: пустой {\"cargos\": []} — пустой список, /cargos/status/ не вызывается")
        void emptyJournalSkipsStatusCall() {
            server.expect(requestTo(LIST_URL)).andRespond(withSuccess("{\"cargos\": []}", MediaType.APPLICATION_JSON));

            assertThat(client.fetchJournal(Instant.parse("2026-10-01T00:00:00Z"))).isEmpty();
            server.verify();
        }

        @Test
        @DisplayName("журнал: 16 кодов — /cargos/status/ двумя пачками по 15; «не найдены» (404 в теле) не ломает журнал")
        void journalStatusesInBatches() {
            String cargos = IntStream.rangeClosed(1, 16).mapToObj(i -> "{\"code\":\"C" + i + "\"}")
                    .collect(java.util.stream.Collectors.joining(","));
            server.expect(requestTo(LIST_URL))
                    .andRespond(withSuccess("{\"cargos\":[" + cargos + "]}", MediaType.APPLICATION_JSON));
            server.expect(requestTo(STATUS_URL))
                    .andExpect(content().string(containsString("\"C15\"")))
                    .andExpect(content().string(not(containsString("\"C16\""))))
                    .andRespond(withSuccess("{\"cargos\":[]}", MediaType.APPLICATION_JSON));
            server.expect(requestTo(STATUS_URL))
                    .andExpect(content().string(containsString("\"C16\"")))
                    .andRespond(withSuccess("{\"error\":{\"title\":\"не найдены\",\"status\":404}}", MediaType.APPLICATION_JSON));

            assertThat(client.fetchJournal(Instant.parse("2026-10-01T00:00:00Z"))).hasSize(16);
            server.verify();
        }

        @Test
        @DisplayName("журнал без массива cargos — CarrierApiException")
        void journalWithoutCargosThrows() {
            server.expect(requestTo(LIST_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.fetchJournal(Instant.now())).isInstanceOf(CarrierApiException.class);
        }

        @Test
        @DisplayName("таблица сопоставления: ключевые статусы ПЭК, регистр и хвост «(мест N из M)» не важны")
        void statusTable() {
            assertThat(PecClient.normalizeStatus("В пути на терминал")).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
            assertThat(PecClient.normalizeStatus("Прибыл частично")).isEqualTo(ShipmentStatusCode.ARRIVED_AT_TERMINAL);
            assertThat(PecClient.normalizeStatus("Выдан получателю")).isEqualTo(ShipmentStatusCode.DELIVERED);
            assertThat(PecClient.normalizeStatus("Выдан ( мест 1 из 2 )")).isEqualTo(ShipmentStatusCode.DELIVERED);
            assertThat(PecClient.normalizeStatus("Аннулировано до приемки груза")).isEqualTo(ShipmentStatusCode.CANCELLED);
            assertThat(PecClient.normalizeStatus("Изъят на таможне")).isEqualTo(ShipmentStatusCode.PROBLEM);
            assertThat(PecClient.normalizeStatus("Отправлен на возврат")).isEqualTo(ShipmentStatusCode.PROBLEM);
            assertThat(PecClient.normalizeStatus("Заявка на забор зарегистрирована")).isEqualTo(ShipmentStatusCode.CREATED);
            assertThat(PecClient.normalizeStatus("Утилизирован")).isEqualTo(ShipmentStatusCode.CANCELLED);
            assertThat(PecClient.normalizeStatus("???")).isNull();
            assertThat(PecClient.normalizeStatus(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Байкал Сервис")
    class Baikal {

        private static final String BASE = "https://api.baikalsr.ru";

        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final BaikalClient client = new BaikalClient(builder, BASE, "key");

        private void expectTracking(String number, String json) {
            server.expect(requestTo(BASE + "/v1/tracking?number=" + number))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header("Authorization", "Basic a2V5Og=="))
                    .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        }

        private void expectNoRequests() {
            server.expect(requestTo(BASE + "/v2/order/request"))
                    .andRespond(withSuccess("{\"recordCount\":0,\"requestList\":[]}", MediaType.APPLICATION_JSON));
        }

        private void expectRequests(String json) {
            server.expect(requestTo(BASE + "/v2/order/request"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", "Basic a2V5Og=="))
                    .andExpect(content().string(containsString("\"size\":100")))
                    .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("статусы: код по тексту статуса (id не учитывается), пункты; {error} - накладной нет в карте")
        void readsStatuses() {
            expectTracking("B0", "{\"departure\":\"Москва\",\"destination\":\"Тверь\",\"status\":{\"id\":0,\"text\":\"Груз принят к перевозке.\"}}");
            expectTracking("B1", "{\"status\":{\"id\":1,\"text\":\"Груз отправлен.\"}}");
            expectTracking("B2", "{\"status\":{\"id\":2,\"text\":\"Груз прибыл.\"}}");
            expectTracking("B3", "{\"status\":{\"id\":3,\"text\":\"Груз выдан в пункте назначения 07.04.2015.\"}}");
            expectTracking("B4", "{\"error\":\"Уточните номер экспедиторской расписки, груза или заявки.\"}");

            Map<String, CarrierShipmentStatus> result = client.fetchStatuses(List.of("B0", "B1", "B2", "B3", "B4"));

            assertThat(result).containsOnlyKeys("B0", "B1", "B2", "B3");
            assertThat(result.get("B0")).isEqualTo(new CarrierShipmentStatus(
                    ShipmentStatusCode.IN_TRANSIT, "Груз принят к перевозке.", "Москва", "Тверь", null));
            assertThat(result.get("B1").code()).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
            assertThat(result.get("B2").code()).isEqualTo(ShipmentStatusCode.ARRIVED_AT_TERMINAL);
            assertThat(result.get("B3").code()).isEqualTo(ShipmentStatusCode.DELIVERED);
            assertThat(client.supportsStatusTracking()).isTrue();
            assertThat(client.supportsJournal()).isTrue();
            server.verify();
        }

        @Test
        @DisplayName("пустой список накладных - без запроса к ТК")
        void emptyWaybillsNoRequest() {
            assertThat(client.fetchStatuses(List.of())).isEmpty();
            server.verify();
        }

        @Test
        @DisplayName("HTTP 404 - CarrierApiException, а не «накладной нет»")
        void notFoundIsError() {
            server.expect(requestTo(BASE + "/v1/tracking?number=B2")).andRespond(withStatus(HttpStatus.NOT_FOUND));

            assertThatThrownBy(() -> client.fetchStatuses(List.of("B2"))).isInstanceOf(CarrierApiException.class);
        }

        @Test
        @DisplayName("200 без status.id или не объектный ответ - CarrierApiException")
        void missingStatusThrows() {
            expectTracking("B1", "{\"foo\":1}");
            expectTracking("B2", "[]");

            assertThatThrownBy(() -> client.fetchStatuses(List.of("B1"))).isInstanceOf(CarrierApiException.class);
            assertThatThrownBy(() -> client.fetchStatuses(List.of("B2"))).isInstanceOf(CarrierApiException.class);
        }

        @Test
        @DisplayName("журнал: 2 страницы order/list, waybill = trackingnumber, по каждой квитанции order/detail с ИНН сторон")
        void readsJournal() {
            expectNoRequests();
            server.expect(requestTo(BASE + "/v2/order/list"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", "Basic a2V5Og=="))
                    .andExpect(content().string(containsString("\"from\":\"2026-10-01T00:00:00\"")))
                    .andExpect(content().string(containsString("\"page\":0")))
                    .andExpect(content().string(containsString("\"size\":50")))
                    .andRespond(withSuccess("{\"recordCount\":2,\"orderList\":[{\"number\":\"МФ-1\",\"trackingnumber\":\"TN-1\"}]}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(BASE + "/v2/order/detail"))
                    .andExpect(content().string(containsString("\"number\":\"МФ-1\"")))
                    .andRespond(withSuccess("""
                            {"number":"МФ-1","date":"2026-10-02T10:00:00","cargoList":[
                              {"number":"МД-1","referenceNumber":"AAB32389-00007","status":{"id":5,"name":"Груз в пути"},
                               "departure":{"name":"Москва"},"destination":{"name":"Омск"},
                               "consignor":{"name":"ООО Мы","inn":"7700000001","kpp":"770001001"},
                               "consignee":{"name":"ИП Клиент","inn":"5500000002"},
                               "dateArrivalPlane":"2026-10-05T00:00:00"}]}
                            """, MediaType.APPLICATION_JSON));
            server.expect(requestTo(BASE + "/v2/order/list"))
                    .andExpect(content().string(containsString("\"page\":1")))
                    .andRespond(withSuccess("{\"recordCount\":2,\"orderList\":[{\"number\":\"МФ-2\",\"trackingnumber\":\"TN-2\"}]}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(BASE + "/v2/order/detail"))
                    .andExpect(content().string(containsString("\"number\":\"МФ-2\"")))
                    .andRespond(withSuccess("""
                            {"number":"МФ-2","date":"2026-10-03T10:00:00","cargoList":[
                              {"number":"МД-2","referenceNumber":"","status":{"id":7,"name":"Груз выдан"},
                               "departure":{"name":"Омск"},"destination":{"name":"Москва"},
                               "consignor":{"name":"ООО Другой","inn":"5500000003"},
                               "consignee":{"name":"ООО Мы","inn":"7700000001"}}]}
                            """, MediaType.APPLICATION_JSON));

            List<JournalItem> items = client.fetchJournal(Instant.parse("2026-10-01T00:00:00Z"));

            assertThat(items).hasSize(2);
            JournalItem first = items.get(0);
            assertThat(first.waybillNumber()).isEqualTo("TN-1");
            assertThat(first.sender()).isEqualTo("ООО Мы");
            assertThat(first.senderInn()).isEqualTo("7700000001");
            assertThat(first.receiver()).isEqualTo("ИП Клиент");
            assertThat(first.receiverInn()).isEqualTo("5500000002");
            assertThat(first.routeFrom()).isEqualTo("Москва");
            assertThat(first.routeTo()).isEqualTo("Омск");
            assertThat(first.shippedAt()).isEqualTo(Instant.parse("2026-10-02T10:00:00Z"));
            assertThat(first.estimatedDeliveryAt()).isEqualTo(Instant.parse("2026-10-05T00:00:00Z"));
            assertThat(first.siteOrderNumber()).isEqualTo("AAB32389-00007");
            assertThat(first.status().code()).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
            assertThat(items.get(1).waybillNumber()).isEqualTo("TN-2");
            assertThat(items.get(1).receiverInn()).isEqualTo("7700000001");
            assertThat(items.get(1).siteOrderNumber()).isNull();
            assertThat(items.get(1).status().code()).isEqualTo(ShipmentStatusCode.DELIVERED);
            server.verify();
        }

        @Test
        @DisplayName("журнал: квитанция без trackingnumber пропускается без запроса order/detail")
        void receiptWithoutTrackingNumberSkipped() {
            expectNoRequests();
            server.expect(requestTo(BASE + "/v2/order/list"))
                    .andRespond(withSuccess("{\"recordCount\":1,\"orderList\":[{\"number\":\"МФ-1\"}]}", MediaType.APPLICATION_JSON));

            assertThat(client.fetchJournal(Instant.parse("2026-10-01T00:00:00Z"))).isEmpty();
            server.verify();
        }

        @Test
        @DisplayName("журнал: груз квитанции — вес/объём/места суммой по грузам, номера грузов в packages, сумма/оплата, адреса по первому грузу, дата выдачи")
        void journalCargo() {
            expectNoRequests();
            server.expect(requestTo(BASE + "/v2/order/list"))
                    .andRespond(withSuccess("{\"recordCount\":1,\"orderList\":[{\"number\":\"МФ-1\",\"trackingnumber\":\"TN-1\"}]}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(BASE + "/v2/order/detail"))
                    .andRespond(withSuccess("""
                            {"number":"МФ-1","date":"2026-10-02T10:00:00","cargoList":[
                              {"number":"МД-1","status":{"name":"Груз выдан"},"total":{"sum":3075.63,"paid":3075.63},
                               "services":[{"payer":{"name":"ООО А","inn":"7700000003"}},{"payer":{"name":"ООО А","inn":"7700000003"}},
                                           {"payer":{"name":"ООО Б","inn":"5400000004"}}],
                               "cargo":{"length":1.2,"width":0.8,"height":1.5,"volume":1.584,"weight":101,"places":2},
                               "departure":{"name":"Москва","pickup":{"address":"Москва, Тестовая, 5"}},
                               "destination":{"name":"Омск","terminal":{"guid":"","name":"Омск терминал","address":"Омск, Тестовая, 1"}},
                               "dateIssued":"2026-10-08T12:00:00"},
                              {"number":"МД-2","status":{"name":"Груз выдан"},"total":{"sum":100,"paid":0},
                               "services":[{"payer":{"name":"ООО А","inn":"7700000003"}}],
                               "cargo":{"volume":1,"weight":50,"places":1},
                               "departure":{"name":"Москва"},"destination":{"name":"Омск"}}]}
                            """, MediaType.APPLICATION_JSON));

            List<JournalItem> items = client.fetchJournal(Instant.parse("2026-10-01T00:00:00Z"));

            assertThat(items).hasSize(1);
            ShipmentCargo cargo = items.get(0).cargo();
            assertThat(cargo.weightKg()).isEqualByComparingTo(new BigDecimal("151"));
            assertThat(cargo.volumeM3()).isEqualByComparingTo(new BigDecimal("2.584"));
            assertThat(cargo.placesCount()).isEqualTo(3);
            assertThat(cargo.cost()).isEqualByComparingTo(new BigDecimal("3075.63"));
            assertThat(cargo.paid()).isTrue();
            assertThat(cargo.fromAddress()).isTrue();
            assertThat(cargo.toAddress()).isFalse();
            assertThat(cargo.deliveredAt()).isEqualTo(Instant.parse("2026-10-08T12:00:00Z"));
            assertThat(cargo.documents()).isNull();
            assertThat(cargo.packages()).extracting(ShipmentPackage::number).containsExactly("МД-1", "МД-2");
            assertThat(cargo.payers()).containsExactly(new ShipmentPayer("ООО А", "7700000003", null),
                    new ShipmentPayer("ООО Б", "5400000004", null));
            assertThat(cargo.packages()).extracting(ShipmentPackage::weightKg)
                    .usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("101"), new BigDecimal("50"));
        }

        @Test
        @DisplayName("журнал: статус квитанции с несколькими грузами — самый ранний по порядку кодов, дата выдачи только при DELIVERED")
        void journalLowestCargoStatus() {
            expectNoRequests();
            server.expect(requestTo(BASE + "/v2/order/list"))
                    .andRespond(withSuccess("{\"recordCount\":1,\"orderList\":[{\"number\":\"МФ-1\",\"trackingnumber\":\"TN-1\"}]}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(BASE + "/v2/order/detail"))
                    .andRespond(withSuccess("""
                            {"number":"МФ-1","cargoList":[
                              {"number":"МД-1","status":{"name":"Груз выдан"},"dateIssued":"2026-10-08T12:00:00"},
                              {"number":"МД-2","status":{"name":"Груз в пути"}},
                              {"number":"МД-3","status":{"name":"Что-то новое"}}]}
                            """, MediaType.APPLICATION_JSON));

            JournalItem item = client.fetchJournal(Instant.parse("2026-10-01T00:00:00Z")).get(0);

            assertThat(item.status().code()).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
            assertThat(item.status().rawText()).isEqualTo("Груз в пути");
            assertThat(item.cargo().deliveredAt()).isNull();
        }

        @Test
        @DisplayName("статусы: сумма из трекинга попадает в cargo; без суммы cargo = null")
        void trackingCost() {
            expectTracking("B0", "{\"sum\":1500.5,\"status\":{\"id\":1,\"text\":\"Груз отправлен.\"}}");
            expectTracking("B1", "{\"status\":{\"id\":1,\"text\":\"Груз отправлен.\"}}");

            Map<String, CarrierShipmentStatus> result = client.fetchStatuses(List.of("B0", "B1"));

            assertThat(result.get("B0").cargo().cost()).isEqualByComparingTo(new BigDecimal("1500.5"));
            assertThat(result.get("B1").cargo()).isNull();
        }

        @Test
        @DisplayName("журнал: ответ с errors - CarrierApiException")
        void journalErrorThrows() {
            expectNoRequests();
            server.expect(requestTo(BASE + "/v2/order/list"))
                    .andRespond(withSuccess("{\"errors\":[{\"section\":\"[number]\"}]}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.fetchJournal(Instant.now())).isInstanceOf(CarrierApiException.class);
        }

        @Test
        @DisplayName("без BAIKAL_BASE_URL обращение к Байкалу - CarrierApiException, а не падение")
        void missingBaseUrlThrowsCarrierApiException() {
            BaikalClient noUrl = new BaikalClient(RestClient.builder(), "", "key");

            assertThatThrownBy(() -> noUrl.fetchJournal(Instant.now())).isInstanceOf(CarrierApiException.class);
            assertThatThrownBy(noUrl::fetchTerminals).isInstanceOf(CarrierApiException.class);
        }

        @Test
        @DisplayName("журнал: ошибка order/detail по одной квитанции пропускает её, остальные читаются")
        void journalSkipsFailedDetail() {
            expectNoRequests();
            server.expect(requestTo(BASE + "/v2/order/list"))
                    .andRespond(withSuccess("{\"recordCount\":2,\"orderList\":[{\"number\":\"МФ-1\",\"trackingnumber\":\"TN-1\"},"
                            + "{\"number\":\"МФ-2\",\"trackingnumber\":\"TN-2\"}]}", MediaType.APPLICATION_JSON));
            server.expect(requestTo(BASE + "/v2/order/detail"))
                    .andRespond(withSuccess("{\"error\":\"Квитанция не найдена\"}", MediaType.APPLICATION_JSON));
            server.expect(requestTo(BASE + "/v2/order/detail"))
                    .andRespond(withSuccess("{\"number\":\"МФ-2\",\"cargoList\":[{\"number\":\"МД-2\"}]}",
                            MediaType.APPLICATION_JSON));

            List<JournalItem> items = client.fetchJournal(Instant.now());

            assertThat(items).extracting(JournalItem::waybillNumber).containsExactly("TN-2");
            server.verify();
        }

        @Test
        @DisplayName("заявки: свежая «Не обработана» — CREATED, старше 14 дней — CANCELLED, «Отклонена» — CANCELLED; waybill = trackingnumber заявки")
        void journalRequests() {
            String fresh = Instant.now().minus(java.time.Duration.ofDays(1)).toString().substring(0, 19);
            String old = Instant.now().minus(java.time.Duration.ofDays(30)).toString().substring(0, 19);
            expectRequests("""
                    {"recordCount":4,"requestList":[
                      {"number":101,"trackingnumber":"RQ-1","date":"%s","from":"Томилино","to":"Омск","weight":100,"volume":0.2,"cargocount":1,
                       "status":{"id":0,"name":"Не обработана"},
                       "cargoList":[{"numberCargo":"","referenceNumber":"AAB-1",
                                     "consignor":{"name":"ООО МСВ","legalNaturalPerson":{"firstField":"1101059443"}},
                                     "consignee":{"name":"АО СТГ","legalNaturalPerson":{"firstField":"1121016110"}}}]},
                      {"number":102,"trackingnumber":"RQ-2","date":"%s","from":"Томилино","to":"Омск","status":{"id":0,"name":"Не обработана"},"cargoList":[]},
                      {"number":103,"trackingnumber":"RQ-3","date":"%s","from":"Томилино","to":"Омск","status":{"id":2,"name":"Отклонена"},"cargoList":[]},
                      {"number":104,"date":"%s","from":"Томилино","to":"Омск","status":{"id":0,"name":"Не обработана"},"cargoList":[]}]}
                    """.formatted(fresh, old, fresh, fresh));
            server.expect(requestTo(BASE + "/v2/order/list"))
                    .andRespond(withSuccess("{\"recordCount\":0,\"orderList\":[]}", MediaType.APPLICATION_JSON));

            List<JournalItem> items = client.fetchJournal(Instant.now().minus(java.time.Duration.ofDays(60)));

            assertThat(items).extracting(JournalItem::waybillNumber).containsExactly("RQ-1", "RQ-2", "RQ-3");
            JournalItem created = items.get(0);
            assertThat(created.status().code()).isEqualTo(ShipmentStatusCode.CREATED);
            assertThat(created.status().rawText()).isEqualTo("Не обработана");
            assertThat(created.sender()).isEqualTo("ООО МСВ");
            assertThat(created.senderInn()).isEqualTo("1101059443");
            assertThat(created.receiverInn()).isEqualTo("1121016110");
            assertThat(created.siteOrderNumber()).isEqualTo("AAB-1");
            assertThat(created.routeFrom()).isEqualTo("Томилино");
            assertThat(created.routeTo()).isEqualTo("Омск");
            assertThat(created.shippedAt()).isNull();
            assertThat(created.cargo().weightKg()).isEqualByComparingTo("100");
            assertThat(created.cargo().placesCount()).isEqualTo(1);
            assertThat(items.get(1).status().code()).isEqualTo(ShipmentStatusCode.CANCELLED);
            assertThat(items.get(1).status().rawText()).isEqualTo("Не обработана");
            assertThat(items.get(2).status().code()).isEqualTo(ShipmentStatusCode.CANCELLED);
            server.verify();
        }

        @Test
        @DisplayName("заявки: «Выполнена» пропускается; невыполненная заявка с тем же trackingnumber, что у квитанции, даёт запись с тем же waybill")
        void doneRequestSkippedAndSharedTrackingNumber() {
            expectRequests("""
                    {"recordCount":2,"requestList":[
                      {"number":1,"trackingnumber":"TN-DONE","date":"2026-10-13T15:37:34","status":{"id":4,"name":"Выполнена"},
                       "cargoList":[{"numberCargo":"ТО-1"}]},
                      {"number":2,"trackingnumber":"TN-1","date":"2099-01-01T00:00:00","status":{"id":0,"name":"Не обработана"},
                       "cargoList":[]}]}
                    """);
            server.expect(requestTo(BASE + "/v2/order/list"))
                    .andRespond(withSuccess("{\"recordCount\":2,\"orderList\":[{\"number\":\"МФ-1\",\"trackingnumber\":\"TN-DONE\"},"
                            + "{\"number\":\"МФ-2\",\"trackingnumber\":\"TN-1\"}]}", MediaType.APPLICATION_JSON));
            server.expect(requestTo(BASE + "/v2/order/detail"))
                    .andRespond(withSuccess("{\"number\":\"МФ-1\",\"cargoList\":[{\"number\":\"ТО-1\",\"status\":{\"name\":\"Груз в пути\"}}]}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(BASE + "/v2/order/detail"))
                    .andRespond(withSuccess("{\"number\":\"МФ-2\",\"cargoList\":[{\"number\":\"ТО-2\",\"status\":{\"name\":\"Груз в пути\"}}]}",
                            MediaType.APPLICATION_JSON));

            List<JournalItem> items = client.fetchJournal(Instant.parse("2026-10-01T00:00:00Z"));

            assertThat(items).extracting(JournalItem::waybillNumber).containsExactly("TN-1", "TN-DONE", "TN-1");
            assertThat(items.get(0).status().code()).isEqualTo(ShipmentStatusCode.CREATED);
            assertThat(items.get(2).status().code()).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
            server.verify();
        }

        @Test
        @DisplayName("заявки: сбой списка заявок не обрывает журнал квитанций")
        void requestListFailureKeepsReceipts() {
            server.expect(requestTo(BASE + "/v2/order/request")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
            server.expect(requestTo(BASE + "/v2/order/list"))
                    .andRespond(withSuccess("{\"recordCount\":1,\"orderList\":[{\"number\":\"МФ-1\",\"trackingnumber\":\"TN-1\"}]}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(BASE + "/v2/order/detail"))
                    .andRespond(withSuccess("{\"number\":\"МФ-1\",\"cargoList\":[{\"number\":\"МД-1\"}]}", MediaType.APPLICATION_JSON));

            List<JournalItem> items = client.fetchJournal(Instant.parse("2026-10-01T00:00:00Z"));

            assertThat(items).extracting(JournalItem::waybillNumber).containsExactly("TN-1");
            server.verify();
        }

        @Test
        @DisplayName("таблица ключевых слов: отмена/возврат, выдача, прибытие, в пути")
        void statusTable() {
            assertThat(BaikalClient.normalizeStatus("Отменён")).isEqualTo(ShipmentStatusCode.CANCELLED);
            assertThat(BaikalClient.normalizeStatus("Выдан получателю")).isEqualTo(ShipmentStatusCode.DELIVERED);
            assertThat(BaikalClient.normalizeStatus("Прибыл на терминал")).isEqualTo(ShipmentStatusCode.ARRIVED_AT_TERMINAL);
            assertThat(BaikalClient.normalizeStatus("В пути")).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
            assertThat(BaikalClient.normalizeStatus("Не выдан")).isNull();
            assertThat(BaikalClient.normalizeStatus("Готов к отправке")).isNull();
            assertThat(BaikalClient.normalizeStatus("Не обработана")).isEqualTo(ShipmentStatusCode.CREATED);
            assertThat(BaikalClient.normalizeStatus("Заявка подтверждена")).isEqualTo(ShipmentStatusCode.CREATED);
            assertThat(BaikalClient.normalizeStatus("Отклонена")).isEqualTo(ShipmentStatusCode.CANCELLED);
            assertThat(BaikalClient.normalizeStatus("Груз передан в службу доставки")).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
            assertThat(BaikalClient.normalizeStatus("Груз прибыл")).isEqualTo(ShipmentStatusCode.ARRIVED_AT_TERMINAL);
            assertThat(BaikalClient.normalizeStatus("Груз доставлен")).isEqualTo(ShipmentStatusCode.DELIVERED);
            assertThat(BaikalClient.normalizeStatus("Груз не доставлен")).isNull();
            assertThat(BaikalClient.normalizeStatus("???")).isNull();
            assertThat(BaikalClient.normalizeStatus(null)).isNull();
        }
    }
}
