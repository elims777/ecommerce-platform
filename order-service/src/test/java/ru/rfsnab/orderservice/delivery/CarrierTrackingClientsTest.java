package ru.rfsnab.orderservice.delivery;

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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
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
        CarrierClient cdek = new CdekClient(RestClient.builder(), "id", "secret");

        assertThat(cdek.supportsJournal()).isFalse();
        assertThatThrownBy(() -> cdek.fetchJournal(Instant.now())).isInstanceOf(CarrierApiException.class);
    }

    @Nested
    @DisplayName("СДЭК")
    class Cdek {

        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final CdekClient client = new CdekClient(builder, "id", "secret");

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

            assertThat(result.get("1234567890")).isEqualTo(new CarrierShipmentStatus(
                    ShipmentStatusCode.ARRIVED_AT_TERMINAL, "Прибыл в пункт выдачи", "Новосибирск", "Томск"));
            assertThat(client.supportsStatusTracking()).isTrue();
            assertThat(client.supportsJournal()).isFalse();
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
            assertThat(CdekClient.normalizeStatus("CREATED")).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
            assertThat(CdekClient.normalizeStatus("ACCEPTED_AT_RECIPIENT_CITY_WAREHOUSE")).isEqualTo(ShipmentStatusCode.ARRIVED_AT_TERMINAL);
            assertThat(CdekClient.normalizeStatus("DELIVERED")).isEqualTo(ShipmentStatusCode.DELIVERED);
            assertThat(CdekClient.normalizeStatus("REMOVED")).isEqualTo(ShipmentStatusCode.CANCELLED);
            assertThat(CdekClient.normalizeStatus("NOT_DELIVERED")).isEqualTo(ShipmentStatusCode.PROBLEM);
            assertThat(CdekClient.normalizeStatus("???")).isNull();
            assertThat(CdekClient.normalizeStatus(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Деловые Линии")
    class Dellin {

        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final DellinClient client = new DellinClient(builder, "appkey", "login", "password");

        private void expectLogin() {
            server.expect(requestTo("https://api.dellin.ru/v3/auth/login.json"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().string(containsString("\"login\":\"login\"")))
                    .andRespond(withSuccess("{\"data\":{\"sessionID\":\"sess\"}}", MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("без логина/пароля ЛК статусы и журнал не поддерживаются")
        void notSupportedWithoutAccountCredentials() {
            DellinClient noAccount = new DellinClient(RestClient.builder(), "appkey", "", "");

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
            server.expect(requestTo("https://api.dellin.ru/v3/orders/statuses.json"))
                    .andExpect(content().string(containsString("\"sessionID\":\"sess\"")))
                    .andExpect(content().string(containsString("\"docIds\"")))
                    .andRespond(withSuccess("""
                            {"orders":[
                              {"docId":"A1","state":"arrived","stateName":"Прибыл на терминал","derivalCity":"Москва","arrivalCity":"Новосибирск"},
                              {"orderId":"B2","state":"weird_state","stateName":"Что-то новое"}]}
                            """, MediaType.APPLICATION_JSON));

            Map<String, CarrierShipmentStatus> result = client.fetchStatuses(List.of("A1", "B2", "C3"));

            assertThat(result).containsOnlyKeys("A1", "B2");
            assertThat(result.get("A1")).isEqualTo(new CarrierShipmentStatus(
                    ShipmentStatusCode.ARRIVED_AT_TERMINAL, "Прибыл на терминал", "Москва", "Новосибирск"));
            assertThat(result.get("B2").code()).isNull();
            assertThat(result.get("B2").rawText()).isEqualTo("Что-то новое");
            server.verify();
        }

        @Test
        @DisplayName("ответ без списка заказов — CarrierApiException, а не «все не найдены»")
        void statusesWithoutOrdersThrow() {
            expectLogin();
            server.expect(requestTo("https://api.dellin.ru/v3/orders/statuses.json"))
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
                    {"orders":[{"orderId":"J1","state":"delivered","stateName":"Доставлен",
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
            assertThat(DellinClient.normalizeStatus("cancelled")).isEqualTo(ShipmentStatusCode.CANCELLED);
            assertThat(DellinClient.normalizeStatus("problem")).isEqualTo(ShipmentStatusCode.PROBLEM);
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
        private final VozovozClient client = new VozovozClient(builder, "tok");

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
            assertThat(result.get("700")).isEqualTo(new CarrierShipmentStatus(
                    ShipmentStatusCode.ARRIVED_AT_TERMINAL, "Прибыл в город назначения", "Москва", "Омск"));
            assertThat(result.get("800").code()).isEqualTo(ShipmentStatusCode.CANCELLED);
            assertThat(result.get("900").code()).isEqualTo(ShipmentStatusCode.DELIVERED);
            assertThat(result.get("900").rawText()).isEqualTo("Выдан");
            assertThat(result.get("MY-1")).isEqualTo(result.get("900"));
            assertThat(client.supportsStatusTracking()).isTrue();
            assertThat(client.supportsJournal()).isTrue();
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
        @DisplayName("журнал: фильтр по дате оформления, стороны, ИНН, маршрут, даты, пользовательский номер; пагинация")
        void readsJournal() {
            server.expect(requestTo(URL))
                    .andExpect(content().string(containsString("\"from\":\"2026-10-01\"")))
                    .andExpect(content().string(containsString("\"offset\":0")))
                    .andRespond(withSuccess("""
                            {"response":{"data":[{"number":"700247224","customId":"AAB32389-00007","status":"Заказ оформлен",
                              "dates":{"created":"2026-10-01T16:43:18","shipped":"2026-10-02T09:00:00","arrived":"2026-10-04T12:00:00","given":""},
                              "gateway":{"dispatch":{"point":{"location":{"name":"Новосибирск"}},
                                                     "customer":{"companyName":"ООО Мы","inn":"7700000001"}},
                                         "destination":{"point":{"location":{"name":"Омск"}},
                                                        "customer":{"name":"Иван Иванов","inn":"5400000002"}}}}],
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
            assertThat(item.receiver()).isEqualTo("Иван Иванов");
            assertThat(item.receiverInn()).isEqualTo("5400000002");
            assertThat(item.routeFrom()).isEqualTo("Новосибирск");
            assertThat(item.routeTo()).isEqualTo("Омск");
            assertThat(item.shippedAt()).isEqualTo(Instant.parse("2026-10-02T09:00:00Z"));
            assertThat(item.estimatedDeliveryAt()).isEqualTo(Instant.parse("2026-10-04T12:00:00Z"));
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
        private static final String LIST_URL = "https://kabinet.pecom.ru/api/v1/cargos/list/";

        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final PecClient client = new PecClient(builder, "login", "key");

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
            assertThat(result.get("W3")).isEqualTo(new CarrierShipmentStatus(
                    ShipmentStatusCode.ARRIVED_AT_TERMINAL, "Прибыл", "Москва", "Омск"));
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
        @DisplayName("журнал: dateBegin/dateEnd, стороны, маршрут, даты, номер заказа клиента, статус")
        void readsJournal() {
            server.expect(requestTo(LIST_URL))
                    .andExpect(content().string(containsString("\"dateBegin\":\"2026-10-01\"")))
                    .andRespond(withSuccess("""
                            {"cargos":[{"info":{"cargoStatus":"В пути","sendingDateTime":"2026-10-02T09:00:00",
                                                "arrivalPlanDateTime":"2026-10-05T08:00:00"},
                                        "cargo":{"code":"J1","orderNumber":"AAB32389-00007"},
                                        "sender":{"sender":"ООО Мы","branchInfo":{"city":"Москва"}},
                                        "receiver":{"receiver":"ИП Клиент","branch":{"city":"Омск"}}},
                                       {"info":{},"cargo":{"code":"J2"}}]}
                            """, MediaType.APPLICATION_JSON));

            List<JournalItem> result = client.fetchJournal(Instant.parse("2026-10-01T00:00:00Z"));

            assertThat(result).extracting(JournalItem::waybillNumber).containsExactly("J1", "J2");
            JournalItem item = result.get(0);
            assertThat(item.sender()).isEqualTo("ООО Мы");
            assertThat(item.receiver()).isEqualTo("ИП Клиент");
            assertThat(item.routeFrom()).isEqualTo("Москва");
            assertThat(item.routeTo()).isEqualTo("Омск");
            assertThat(item.shippedAt()).isEqualTo(Instant.parse("2026-10-02T09:00:00Z"));
            assertThat(item.estimatedDeliveryAt()).isEqualTo(Instant.parse("2026-10-05T08:00:00Z"));
            assertThat(item.siteOrderNumber()).isEqualTo("AAB32389-00007");
            assertThat(item.status().code()).isEqualTo(ShipmentStatusCode.IN_TRANSIT);
            assertThat(result.get(1).status()).isNull();
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
            assertThat(PecClient.normalizeStatus("???")).isNull();
            assertThat(PecClient.normalizeStatus(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Байкал Сервис (пути предположительные)")
    class Baikal {

        private static final String BASE = "https://api.baikalsr.ru/restapi";

        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final BaikalClient client = new BaikalClient(builder, "key");

        @Test
        @DisplayName("статусы: запрос на накладную с Basic-ключом")
        void readsStatuses() {
            server.expect(requestTo(BASE + "/orders/status?number=B1"))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header("Authorization", "Basic a2V5Og=="))
                    .andRespond(withSuccess("{\"status\":\"Принят к перевозке\",\"from\":\"Москва\",\"to\":\"Омск\"}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(BASE + "/orders/status?number=B3"))
                    .andRespond(withSuccess("{\"status\":\"Нечто новое\"}", MediaType.APPLICATION_JSON));

            Map<String, CarrierShipmentStatus> result = client.fetchStatuses(List.of("B1", "B3"));

            assertThat(result).containsOnlyKeys("B1", "B3");
            assertThat(result.get("B1")).isEqualTo(new CarrierShipmentStatus(
                    ShipmentStatusCode.IN_TRANSIT, "Принят к перевозке", "Москва", "Омск"));
            assertThat(result.get("B3").code()).isNull();
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
        @DisplayName("404 (путь не сверен) — CarrierApiException, а не «накладной нет»")
        void notFoundIsError() {
            server.expect(requestTo(BASE + "/orders/status?number=B2")).andRespond(withStatus(HttpStatus.NOT_FOUND));

            assertThatThrownBy(() -> client.fetchStatuses(List.of("B2"))).isInstanceOf(CarrierApiException.class);
        }

        @Test
        @DisplayName("200 без поля статуса — CarrierApiException")
        void missingStatusThrows() {
            server.expect(requestTo(BASE + "/orders/status?number=B1"))
                    .andRespond(withSuccess("{\"foo\":1}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.fetchStatuses(List.of("B1"))).isInstanceOf(CarrierApiException.class);
        }

        @Test
        @DisplayName("не объектный ответ статуса — CarrierApiException")
        void emptyStatusThrows() {
            server.expect(requestTo(BASE + "/orders/status?number=B1"))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.fetchStatuses(List.of("B1"))).isInstanceOf(CarrierApiException.class);
        }

        @Test
        @DisplayName("журнал: массив orders, поля, статус; ответ без массива — CarrierApiException")
        void readsJournal() {
            server.expect(requestTo(startsWith(BASE + "/orders?date_from=2026-10-01&date_to=")))
                    .andRespond(withSuccess("""
                            {"orders":[{"number":"J1","sender":"ООО Мы","senderInn":"7700000001","receiver":"ИП Клиент",
                                        "from":"Москва","to":"Омск","shippedAt":"2026-10-02","estimatedDeliveryAt":"2026-10-05",
                                        "clientNumber":"AAB32389-00007","status":"Выдан"}]}
                            """, MediaType.APPLICATION_JSON));
            server.expect(requestTo(startsWith(BASE + "/orders?")))
                    .andRespond(withSuccess("{\"foo\":1}", MediaType.APPLICATION_JSON));

            JournalItem item = client.fetchJournal(Instant.parse("2026-10-01T00:00:00Z")).get(0);

            assertThat(item.waybillNumber()).isEqualTo("J1");
            assertThat(item.sender()).isEqualTo("ООО Мы");
            assertThat(item.senderInn()).isEqualTo("7700000001");
            assertThat(item.routeFrom()).isEqualTo("Москва");
            assertThat(item.shippedAt()).isEqualTo(Instant.parse("2026-10-02T00:00:00Z"));
            assertThat(item.siteOrderNumber()).isEqualTo("AAB32389-00007");
            assertThat(item.status().code()).isEqualTo(ShipmentStatusCode.DELIVERED);
            assertThatThrownBy(() -> client.fetchJournal(Instant.now())).isInstanceOf(CarrierApiException.class);
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
            assertThat(BaikalClient.normalizeStatus("???")).isNull();
            assertThat(BaikalClient.normalizeStatus(null)).isNull();
        }
    }
}
