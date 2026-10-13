package ru.rfsnab.orderservice.delivery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
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
    @DisplayName("ТК без реализации трекинга: не поддерживается, вызов — CarrierApiException")
    void unsupportedByDefault() {
        CarrierClient pec = new PecClient(RestClient.builder(), "login", "key");

        assertThat(pec.supportsStatusTracking()).isFalse();
        assertThat(pec.supportsJournal()).isFalse();
        assertThatThrownBy(() -> pec.fetchStatuses(List.of("1"))).isInstanceOf(CarrierApiException.class);
        assertThatThrownBy(() -> pec.fetchJournal(Instant.now())).isInstanceOf(CarrierApiException.class);
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
                                "derivalDate":"2026-10-01","arrivalDate":"2026-10-05T10:00:00",
                                "clientOrderNumber":"AAB32389-00007"}]}
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
            assertThat(item.estimatedDeliveryAt()).isEqualTo(Instant.parse("2026-10-05T10:00:00Z"));
            assertThat(item.siteOrderNumber()).isEqualTo("AAB32389-00007");
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
    }
}
