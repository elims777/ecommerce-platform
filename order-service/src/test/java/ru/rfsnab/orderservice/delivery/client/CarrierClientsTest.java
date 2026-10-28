package ru.rfsnab.orderservice.delivery.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import ru.rfsnab.orderservice.delivery.exception.CarrierApiException;
import ru.rfsnab.orderservice.delivery.models.entity.enums.PointType;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

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
 * Разбор ответов клиентов ТК на образцах JSON. Образцы СДЭК и Деловых Линий повторяют их документацию,
 * образцы ПЭК, Байкала и Возовоза — предполагаемый формат, его нужно сверить с реальным ответом.
 */
@DisplayName("Клиенты ТК: разбор справочника терминалов")
class CarrierClientsTest {

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
    }

    @Nested
    @DisplayName("СДЭК")
    class Cdek {

        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final CdekClient client = new CdekClient(builder, "https://api.cdek.ru/v2", "id", "secret");

        @Test
        @DisplayName("OAuth, две страницы, ПВЗ и постамат; запись без координат доходит до синхронизации с null")
        void readsAllPagesWithPvzAndPostamat() {
            server.expect(requestTo("https://api.cdek.ru/v2/oauth/token"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().string(containsString("client_id=id")))
                    .andExpect(content().string(containsString("grant_type=client_credentials")))
                    .andRespond(withSuccess("{\"access_token\":\"tok\",\"token_type\":\"bearer\"}", MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://api.cdek.ru/v2/deliverypoints?country_code=RU&type=ALL&size=1000&page=0"))
                    .andExpect(header("Authorization", "Bearer tok"))
                    .andRespond(withSuccess("""
                            [{"code":"NSK1","type":"PVZ","name":"ПВЗ Ленина","work_time":"Пн-Пт 10-19",
                              "phones":[{"number":"+7 383 000"},{"number":"+7 383 111"}],
                              "location":{"city":"Новосибирск","address":"Ленина, 1","address_full":"630000, Новосибирск, Ленина, 1",
                                          "latitude":55.03,"longitude":82.92,"extra":"ignored"}},
                             {"code":"NSK2","type":"POSTAMAT","name":"Постамат","location":{"city":"Новосибирск","address":"Мира, 2",
                                          "latitude":55.1,"longitude":82.9}}]
                            """, MediaType.APPLICATION_JSON).header("x-total-pages", "2"));
            server.expect(requestTo("https://api.cdek.ru/v2/deliverypoints?country_code=RU&type=ALL&size=1000&page=1"))
                    .andRespond(withSuccess("[{\"code\":\"TOM1\",\"type\":\"PVZ\",\"location\":{\"city\":\"Томск\",\"address\":\"Ленина, 5\"}}]",
                            MediaType.APPLICATION_JSON).header("x-total-pages", "2"));

            List<TerminalData> result = client.fetchTerminals().orElseThrow();

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isEqualTo(new TerminalData("NSK1", PointType.PVZ, "Новосибирск", "ПВЗ Ленина",
                    "630000, Новосибирск, Ленина, 1", 55.03, 82.92, "Пн-Пт 10-19", "+7 383 000, +7 383 111"));
            assertThat(result.get(1).pointType()).isEqualTo(PointType.POSTAMAT);
            assertThat(result.get(1).address()).isEqualTo("Мира, 2");
            assertThat(result.get(2).latitude()).isNull();
            server.verify();
        }

        @Test
        @DisplayName("без client-id/secret клиент отключён")
        void notConfiguredWithoutCredentials() {
            assertThat(new CdekClient(RestClient.builder(), "https://api.cdek.ru/v2", "", "").isConfigured()).isFalse();
            assertThat(new CdekClient(RestClient.builder(), "https://api.cdek.ru/v2", "id", "").isConfigured()).isFalse();
            assertThat(client.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("ошибка авторизации — исключение, а не пустой список")
        void authFailureThrows() {
            server.expect(requestTo("https://api.cdek.ru/v2/oauth/token")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

            assertThatThrownBy(client::fetchTerminals).isInstanceOf(HttpClientErrorException.class);
        }

        @Test
        @DisplayName("ответ не массив — CarrierApiException")
        void nonArrayThrows() {
            server.expect(requestTo("https://api.cdek.ru/v2/oauth/token"))
                    .andRespond(withSuccess("{\"access_token\":\"tok\"}", MediaType.APPLICATION_JSON));
            server.expect(requestTo(containsString("/deliverypoints")))
                    .andRespond(withSuccess("{\"errors\":[]}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(client::fetchTerminals).isInstanceOf(CarrierApiException.class);
        }

        @Test
        @DisplayName("токен не выдан — CarrierApiException без запроса точек")
        void missingTokenThrows() {
            server.expect(requestTo("https://api.cdek.ru/v2/oauth/token"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(client::fetchTerminals).isInstanceOf(CarrierApiException.class);
        }
    }

    @Nested
    @DisplayName("Деловые Линии")
    class Dellin {

        private static final String FILE_URL = "https://cdn.dellin.ru/terminals_v3.json";
        private static final String META = "{\"hash\":\"h1\",\"url\":\"" + FILE_URL + "\"}";
        private static final String FILE = """
                {"city":[
                  {"id":"1","name":"Новосибирск","terminals":[
                    {"id":"10","name":"Новосибирск Толмачёво","fullAddress":"ул. Ленина, 1","latitude":"55.03","longitude":"82.92",
                     "giveoutCargo":true,"mainPhone":"+7 383 000",
                     "worktables":[{"department":"cash","timetable":"Касса"},{"department":"general","timetable":"Пн-Пт 09:00-18:00"}]},
                    {"id":"11","name":"Только приём","fullAddress":"ул. Мира, 2","latitude":"55.0","longitude":"82.9","giveoutCargo":false}
                  ]},
                  {"id":"2","name":"Томск","terminals":{"terminal":[
                    {"id":"20","name":"Томск","address":"ул. Ленина, 5","latitude":56.5,"longitude":84.9,
                     "worktables":{"worktable":[{"department":"general","monday":"09:00-18:00","tuesday":"09:00-18:00"}]}}
                  ]}}
                ]}
                """;

        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final DellinClient client = new DellinClient(builder, "https://api.dellin.ru", "appkey", "login", "password");

        private void expectMeta() {
            server.expect(requestTo("https://api.dellin.ru/v3/public/terminals.json"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().string(containsString("\"appkey\":\"appkey\"")))
                    .andRespond(withSuccess(META, MediaType.APPLICATION_JSON));
        }

        private void expectFile() {
            server.expect(requestTo(FILE_URL)).andRespond(withSuccess(FILE, MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("файл по url: города, обе формы terminals, часы работы, терминалы без выдачи отсечены")
        void readsFile() {
            expectMeta();
            expectFile();

            List<TerminalData> result = client.fetchTerminals().orElseThrow();

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEqualTo(new TerminalData("10", PointType.TERMINAL, "Новосибирск",
                    "Новосибирск Толмачёво", "ул. Ленина, 1", 55.03, 82.92, "Пн-Пт 09:00-18:00", "+7 383 000"));
            assertThat(result.get(1).externalId()).isEqualTo("20");
            assertThat(result.get(1).city()).isEqualTo("Томск");
            assertThat(result.get(1).workingHours()).isEqualTo("Пн 09:00-18:00; Вт 09:00-18:00");
            server.verify();
        }

        @Test
        @DisplayName("hash не изменился — файл не качается, результат empty")
        void unchangedHashSkipsDownload() {
            expectMeta();
            expectFile();
            client.fetchTerminals();
            server.verify();
            server.reset();
            expectMeta();

            Optional<List<TerminalData>> second = client.fetchTerminals();

            assertThat(second).isEmpty();
            server.verify();
        }

        @Test
        @DisplayName("после forgetFetchState файл качается снова")
        void forgetFetchStateReloads() {
            expectMeta();
            expectFile();
            client.fetchTerminals();
            client.forgetFetchState();
            server.verify();
            server.reset();
            expectMeta();
            expectFile();

            assertThat(client.fetchTerminals()).isPresent();
            server.verify();
        }

        @Test
        @DisplayName("ответ без hash/url — CarrierApiException")
        void metaWithoutHashThrows() {
            server.expect(requestTo("https://api.dellin.ru/v3/public/terminals.json"))
                    .andRespond(withSuccess("{\"errors\":[\"bad appkey\"]}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(client::fetchTerminals).isInstanceOf(CarrierApiException.class);
        }

        @Test
        @DisplayName("файл без списка городов — CarrierApiException, hash не запоминается")
        void brokenFileThrowsAndKeepsNoHash() {
            expectMeta();
            server.expect(requestTo(FILE_URL)).andRespond(withSuccess("{\"foo\":1}", MediaType.APPLICATION_JSON));
            assertThatThrownBy(client::fetchTerminals).isInstanceOf(CarrierApiException.class);
            server.verify();
            server.reset();

            expectMeta();
            expectFile();
            assertThat(client.fetchTerminals()).isPresent();
        }

        @Test
        @DisplayName("без appkey клиент отключён")
        void notConfigured() {
            assertThat(new DellinClient(RestClient.builder(), "https://api.dellin.ru", "", "", "").isConfigured()).isFalse();
        }
    }

    @Nested
    @DisplayName("ПЭК")
    class Pec {

        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final PecClient client = new PecClient(builder, "https://kabinet.pecom.ru", "login", "key");

        @Test
        @DisplayName("POST с Basic-авторизацией; склады выдачи из branches.divisions.warehouses (формат реального ответа)")
        void readsBranches() {
            server.expect(requestTo("https://kabinet.pecom.ru/api/v1/branches/all/"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", basic("login", "key")))
                    .andRespond(withSuccess("""
                            {"branches":[{"id":"b1","title":"Абакан","address":"г. Абакан, ул. Хлебная, д. 30",
                              "coordinatesobj":{"latitude":53.71,"longitude":91.43},
                              "cities":[{"title":"Абакан","cityId":"c1"},{"title":"Черногорск","cityId":"c2"}],
                              "divisions":[
                                {"id":"d1","cityId":"c1","name":"Абакан","warehouses":[
                                  {"id":"w1","name":"Абакан 01","address":"Абакан,ул.Хлебная,30",
                                   "addressDivision":"655002, Абакан, Хлебная улица, 30",
                                   "isWarehouseGivesFreights":true,"departmentClosingDate":null,
                                   "coordinatesobj":{"latitude":53.711417,"longitude":91.431806},
                                   "telephone":"8(3902) 305-081","timeOfWork":[],
                                   "divisionTimeOfWork":[{"workFrom":"09:00","workTo":"18:00","dayOfWeek":"1"},
                                                         {"workFrom":"10:00","workTo":"15:00","dayOfWeek":"6"}]},
                                  {"id":"w2","name":"Только приём","isWarehouseGivesFreights":false,"departmentClosingDate":null},
                                  {"id":"w3","name":"Закрыт","isWarehouseGivesFreights":true,"departmentClosingDate":"2026-01-01"}]},
                                {"id":"d2","cityId":"c2","name":"Черногорск","warehouses":[
                                  {"id":"w4","name":"Черногорск 01","address":"Черногорск, Мира, 1",
                                   "isWarehouseGivesFreights":true,"departmentClosingDate":null,
                                   "coordinatesobj":{"latitude":53.8,"longitude":91.3}}]}]}]}
                            """, MediaType.APPLICATION_JSON));

            List<TerminalData> result = client.fetchTerminals().orElseThrow();

            assertThat(result).containsExactly(
                    new TerminalData("w1", PointType.TERMINAL, "Абакан", "Абакан 01", "655002, Абакан, Хлебная улица, 30",
                            53.711417, 91.431806, "Пн 09:00-18:00; Сб 10:00-15:00", "8(3902) 305-081"),
                    new TerminalData("w4", PointType.TERMINAL, "Черногорск", "Черногорск 01", "Черногорск, Мира, 1",
                            53.8, 91.3, null, null));
            server.verify();
        }

        @Test
        @DisplayName("ответ без списка отделений — CarrierApiException")
        void noListThrows() {
            server.expect(requestTo("https://kabinet.pecom.ru/api/v1/branches/all/"))
                    .andRespond(withSuccess("{\"success\":false}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(client::fetchTerminals).isInstanceOf(CarrierApiException.class);
        }

        @Test
        @DisplayName("HTTP 403 — исключение клиента")
        void forbiddenThrows() {
            server.expect(requestTo("https://kabinet.pecom.ru/api/v1/branches/all/")).andRespond(withStatus(HttpStatus.FORBIDDEN));

            assertThatThrownBy(client::fetchTerminals).isInstanceOf(HttpClientErrorException.class);
        }

        @Test
        @DisplayName("без login или ключа клиент отключён")
        void notConfigured() {
            assertThat(new PecClient(RestClient.builder(), "https://kabinet.pecom.ru", "login", "").isConfigured()).isFalse();
            assertThat(new PecClient(RestClient.builder(), "https://kabinet.pecom.ru", "", "key").isConfigured()).isFalse();
        }
    }

    @Nested
    @DisplayName("Байкал Сервис")
    class Baikal {

        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final BaikalClient client = new BaikalClient(builder, "https://api.baikalsr.ru", "apikey");

        @Test
        @DisplayName("ключ как Basic-логин, город — имя филиала, координаты из map, терминалы с finish=0 пропущены")
        void readsAffiliates() {
            server.expect(requestTo("https://api.baikalsr.ru/v2/affiliate"))
                    .andExpect(header("Authorization", basic("apikey", "")))
                    .andRespond(withSuccess("""
                            [{"name":"Челябинск","guid":"a376","phone":"+73512104015","terminals":[
                              {"id":430,"name":"Челябинск \\"Северный\\"","address":"454081, Северный Луч ул, 3",
                               "map":"55.215123,61.429817","phone":"+73512104015","start":1,"finish":1,"base":1},
                              {"id":402,"name":"Челябинск \\"Южный\\"","address":"Троицкий тракт, 13Д",
                               "map":"55.17,61.37","phone":"+73512104015","start":1,"finish":0,"base":0}]}]
                            """, MediaType.APPLICATION_JSON));

            List<TerminalData> result = client.fetchTerminals().orElseThrow();

            assertThat(result).containsExactly(new TerminalData("430", PointType.TERMINAL, "Челябинск",
                    "Челябинск \"Северный\"", "454081, Северный Луч ул, 3", 55.215123, 61.429817, null, "+73512104015"));
        }

        @Test
        @DisplayName("ответ не массив — CarrierApiException")
        void noListThrows() {
            server.expect(requestTo("https://api.baikalsr.ru/v2/affiliate"))
                    .andRespond(withSuccess("\"ok\"", MediaType.APPLICATION_JSON));

            assertThatThrownBy(client::fetchTerminals).isInstanceOf(CarrierApiException.class);
        }

        @Test
        @DisplayName("без ключа клиент отключён")
        void notConfigured() {
            assertThat(new BaikalClient(RestClient.builder(), "https://api.baikalsr.ru", " ").isConfigured()).isFalse();
        }
    }

    @Nested
    @DisplayName("Возовоз")
    class Vozovoz {

        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final VozovozClient client = new VozovozClient(builder, "https://vozovoz.ru", "tok");

        @Test
        @DisplayName("POST terminal.get с токеном и разбор response")
        void readsResponse() {
            server.expect(requestTo("https://vozovoz.ru/api/?token=tok"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().string(containsString("\"object\":\"terminal\"")))
                    .andExpect(content().string(containsString("\"action\":\"get\"")))
                    .andExpect(content().string(containsString("\"offset\":0")))
                    .andRespond(withSuccess("""
                            {"response":{"data":[{"guid":"v1","location_guid":"l1","name":"Возовоз Новосибирск",
                                                  "address":"ул. Мира, 3","note":"въезд платный","location_country":"RU",
                                                  "location_name":"Новосибирск",
                                                  "coordinates":{"latitude":"55.0","longitude":"82.9"}}],
                                         "meta":{"limit":1000,"offset":0,"total":1}}}
                            """, MediaType.APPLICATION_JSON));

            List<TerminalData> result = client.fetchTerminals().orElseThrow();

            assertThat(result).containsExactly(new TerminalData("v1", PointType.TERMINAL, "Новосибирск",
                    "Возовоз Новосибирск", "ул. Мира, 3", 55.0, 82.9, null, null));
            server.verify();
        }

        @Test
        @DisplayName("сервер урезает limit — листаем по meta.total, пока не соберём все точки")
        void pagesByMetaTotal() {
            server.expect(requestTo("https://vozovoz.ru/api/?token=tok"))
                    .andExpect(content().string(containsString("\"offset\":0")))
                    .andRespond(withSuccess("""
                            {"response":{"data":[{"guid":"v1","location_name":"Москва","name":"A","address":"a",
                                                  "coordinates":{"latitude":"55.9","longitude":"37.5"}}],
                                         "meta":{"limit":1,"offset":0,"total":2}}}
                            """, MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://vozovoz.ru/api/?token=tok"))
                    .andExpect(content().string(containsString("\"offset\":1")))
                    .andRespond(withSuccess("""
                            {"response":{"data":[{"guid":"v2","location_name":"Тверь","name":"B","address":"b",
                                                  "coordinates":{"latitude":"56.8","longitude":"35.9"}}],
                                         "meta":{"limit":1,"offset":1,"total":2}}}
                            """, MediaType.APPLICATION_JSON));

            assertThat(client.fetchTerminals().orElseThrow()).extracting(TerminalData::externalId).containsExactly("v1", "v2");
            server.verify();
        }

        @Test
        @DisplayName("нет meta.total — листаем до пустой страницы (сервер урезает limit до 100)")
        void pagesUntilEmptyWhenTotalMissing() {
            String item = "{\"guid\":\"v%d\",\"location_name\":\"Город\",\"name\":\"N\",\"address\":\"a\","
                    + "\"coordinates\":{\"latitude\":\"55.9\",\"longitude\":\"37.5\"}}";
            String cappedPage = java.util.stream.IntStream.range(0, 100).mapToObj(item::formatted)
                    .collect(java.util.stream.Collectors.joining(",", "{\"response\":{\"data\":[", "]}}"));
            server.expect(requestTo("https://vozovoz.ru/api/?token=tok"))
                    .andExpect(content().string(containsString("\"offset\":0")))
                    .andRespond(withSuccess(cappedPage, MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://vozovoz.ru/api/?token=tok"))
                    .andExpect(content().string(containsString("\"offset\":100")))
                    .andRespond(withSuccess("{\"response\":{\"data\":[" + item.formatted(100) + "]}}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://vozovoz.ru/api/?token=tok"))
                    .andExpect(content().string(containsString("\"offset\":101")))
                    .andRespond(withSuccess("{\"response\":{\"data\":[]}}", MediaType.APPLICATION_JSON));

            assertThat(client.fetchTerminals().orElseThrow()).hasSize(101);
            server.verify();
        }

        @Test
        @DisplayName("ошибка API (неверный токен) — CarrierApiException с текстом ТК")
        void apiErrorMessage() {
            server.expect(requestTo("https://vozovoz.ru/api/?token=tok"))
                    .andRespond(withSuccess("""
                            {"error":{"id":12004,"code":12004,"message":"Указанный токен не корректен"}}
                            """, MediaType.APPLICATION_JSON));

            assertThatThrownBy(client::fetchTerminals).isInstanceOf(CarrierApiException.class)
                    .hasMessageContaining("Указанный токен не корректен");
        }

        @Test
        @DisplayName("ответ с ошибкой вместо списка — CarrierApiException")
        void noListThrows() {
            server.expect(requestTo("https://vozovoz.ru/api/?token=tok"))
                    .andRespond(withSuccess("{\"error\":\"bad token\"}", MediaType.APPLICATION_JSON));

            assertThatThrownBy(client::fetchTerminals).isInstanceOf(CarrierApiException.class);
        }

        @Test
        @DisplayName("без токена клиент отключён")
        void notConfigured() {
            assertThat(new VozovozClient(RestClient.builder(), "https://vozovoz.ru", "").isConfigured()).isFalse();
        }
    }
}
