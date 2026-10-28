package ru.rfsnab.orderservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.rfsnab.orderservice.BaseServiceIntegrationTest;
import ru.rfsnab.orderservice.exception.BusinessException;
import ru.rfsnab.orderservice.models.dto.recipient.RecipientRequest;
import ru.rfsnab.orderservice.models.entity.Recipient;
import ru.rfsnab.orderservice.repository.RecipientRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ru.rfsnab.orderservice.models.entity.enums.CustomerType.B2B;
import static ru.rfsnab.orderservice.models.entity.enums.CustomerType.B2C;

/**
 * Интеграционные тесты RecipientService.
 */
@DisplayName("RecipientService — интеграционные тесты")
class RecipientServiceIntegrationTest extends BaseServiceIntegrationTest {

    @Autowired
    private RecipientService recipientService;

    @Autowired
    private RecipientRepository recipientRepository;

    private static final Long USER_ID = 200L;

    @BeforeEach
    void setUp() {
        recipientRepository.deleteAll();
    }

    @Nested
    @DisplayName("getByUserId")
    class GetByUserIdTests {

        @Test
        @DisplayName("возвращает пустой список если нет получателей")
        void shouldReturnEmptyList() {
            List<Recipient> result = recipientService.getByUserId(USER_ID, B2C);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("возвращает всех получателей пользователя")
        void shouldReturnAllRecipients() {
            recipientService.create(USER_ID, B2C, new RecipientRequest("Иванов Иван", "+79001234567", false));
            recipientService.create(USER_ID, B2C, new RecipientRequest("Петров Петр", "+79007654321", false));

            List<Recipient> result = recipientService.getByUserId(USER_ID, B2C);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("не возвращает получателей другого пользователя")
        void shouldNotReturnOtherUserRecipients() {
            recipientService.create(999L, B2C, new RecipientRequest("Чужой", "+79000000000", false));

            List<Recipient> result = recipientService.getByUserId(USER_ID, B2C);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("создаёт получателя с корректными данными")
        void shouldCreateRecipient() {
            Recipient recipient = recipientService.create(USER_ID, B2C,
                    new RecipientRequest("Иванов Иван", "+79001234567", false));

            assertThat(recipient.getId()).isNotNull();
            assertThat(recipient.getName()).isEqualTo("Иванов Иван");
            assertThat(recipient.getPhone()).isEqualTo("+79001234567");
            assertThat(recipient.getUserId()).isEqualTo(USER_ID);
            assertThat(recipient.isDefault()).isFalse();
        }

        @Test
        @DisplayName("при isDefault=true сбрасывает предыдущий дефолтный")
        void shouldResetPreviousDefault() {
            Recipient first = recipientService.create(USER_ID, B2C,
                    new RecipientRequest("Первый", "+79001111111", true));
            Recipient second = recipientService.create(USER_ID, B2C,
                    new RecipientRequest("Второй", "+79002222222", true));

            Recipient updatedFirst = recipientRepository.findById(first.getId()).orElseThrow();
            assertThat(updatedFirst.isDefault()).isFalse();
            assertThat(second.isDefault()).isTrue();
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("обновляет имя и телефон получателя")
        void shouldUpdateRecipient() {
            Recipient created = recipientService.create(USER_ID, B2C,
                    new RecipientRequest("Старое имя", "+79001234567", false));

            Recipient updated = recipientService.update(created.getId(), USER_ID, B2C,
                    new RecipientRequest("Новое имя", "+79009876543", false));

            assertThat(updated.getName()).isEqualTo("Новое имя");
            assertThat(updated.getPhone()).isEqualTo("+79009876543");
        }

        @Test
        @DisplayName("бросает исключение если получатель не найден")
        void shouldThrowIfNotFound() {
            assertThatThrownBy(() -> recipientService.update(999L, USER_ID, B2C,
                    new RecipientRequest("Имя", "+79001234567", false)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("бросает исключение если получатель принадлежит другому пользователю")
        void shouldThrowIfWrongUser() {
            Recipient created = recipientService.create(USER_ID, B2C,
                    new RecipientRequest("Иванов", "+79001234567", false));

            assertThatThrownBy(() -> recipientService.update(created.getId(), 999L, B2C,
                    new RecipientRequest("Имя", "+79001234567", false)))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("удаляет получателя")
        void shouldDeleteRecipient() {
            Recipient created = recipientService.create(USER_ID, B2C,
                    new RecipientRequest("Иванов", "+79001234567", false));

            recipientService.delete(created.getId(), USER_ID, B2C);

            assertThat(recipientRepository.findById(created.getId())).isEmpty();
        }

        @Test
        @DisplayName("бросает исключение если получатель не найден")
        void shouldThrowIfNotFound() {
            assertThatThrownBy(() -> recipientService.delete(999L, USER_ID, B2C))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("setDefault")
    class SetDefaultTests {

        @Test
        @DisplayName("устанавливает получателя по умолчанию")
        void shouldSetDefault() {
            Recipient first = recipientService.create(USER_ID, B2C,
                    new RecipientRequest("Первый", "+79001111111", false));
            Recipient second = recipientService.create(USER_ID, B2C,
                    new RecipientRequest("Второй", "+79002222222", false));

            recipientService.setDefault(first.getId(), USER_ID, B2C);

            Recipient updatedFirst = recipientRepository.findById(first.getId()).orElseThrow();
            Recipient updatedSecond = recipientRepository.findById(second.getId()).orElseThrow();

            assertThat(updatedFirst.isDefault()).isTrue();
            assertThat(updatedSecond.isDefault()).isFalse();
        }

        @Test
        @DisplayName("сбрасывает предыдущий дефолтный при установке нового")
        void shouldResetPreviousDefault() {
            Recipient first = recipientService.create(USER_ID, B2C,
                    new RecipientRequest("Первый", "+79001111111", true));
            Recipient second = recipientService.create(USER_ID, B2C,
                    new RecipientRequest("Второй", "+79002222222", false));

            recipientService.setDefault(second.getId(), USER_ID, B2C);

            Recipient updatedFirst = recipientRepository.findById(first.getId()).orElseThrow();
            assertThat(updatedFirst.isDefault()).isFalse();
            assertThat(recipientRepository.findById(second.getId()).orElseThrow().isDefault()).isTrue();
        }
    }

    @Nested
    @DisplayName("изоляция по customerType")
    class CustomerTypeIsolationTests {

        @Test
        @DisplayName("B2B с тем же id не видит и не меняет получателя B2C")
        void shouldIsolateB2bFromB2c() {
            Recipient b2c = recipientService.create(USER_ID, B2C,
                    new RecipientRequest("Иванов", "+79001234567", false));

            assertThat(recipientService.getByUserId(USER_ID, B2B)).isEmpty();
            assertThatThrownBy(() -> recipientService.getById(b2c.getId(), USER_ID, B2B))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> recipientService.update(b2c.getId(), USER_ID, B2B,
                    new RecipientRequest("Имя", "+79001234567", false)))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> recipientService.delete(b2c.getId(), USER_ID, B2B))
                    .isInstanceOf(BusinessException.class);
            assertThat(recipientService.getByUserId(USER_ID, B2C)).hasSize(1);
        }

        @Test
        @DisplayName("B2C с тем же id не видит получателя B2B")
        void shouldIsolateB2cFromB2b() {
            Recipient b2b = recipientService.create(USER_ID, B2B,
                    new RecipientRequest("ООО Ромашка", "+79001234567", false));

            assertThat(recipientService.getByUserId(USER_ID, B2C)).isEmpty();
            assertThatThrownBy(() -> recipientService.getById(b2b.getId(), USER_ID, B2C))
                    .isInstanceOf(BusinessException.class);
            assertThat(recipientService.getByUserId(USER_ID, B2B)).hasSize(1);
        }

        @Test
        @DisplayName("setDefault для получателя другого customerType бросает BusinessException")
        void shouldRejectSetDefaultOfOtherType() {
            Recipient b2c = recipientService.create(USER_ID, B2C,
                    new RecipientRequest("Иванов", "+79001234567", false));

            assertThatThrownBy(() -> recipientService.setDefault(b2c.getId(), USER_ID, B2B))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("isDefault у B2C и B2B с одним userId не сбрасывают друг друга")
        void shouldKeepDefaultsIndependentPerType() {
            Recipient b2c = recipientService.create(USER_ID, B2C,
                    new RecipientRequest("Иванов", "+79001111111", true));
            Recipient b2b = recipientService.create(USER_ID, B2B,
                    new RecipientRequest("ООО Ромашка", "+79002222222", true));
            Recipient b2cOther = recipientService.create(USER_ID, B2C,
                    new RecipientRequest("Петров", "+79003333333", false));
            Recipient b2bOther = recipientService.create(USER_ID, B2B,
                    new RecipientRequest("ООО Лютик", "+79004444444", false));

            // create с isDefault=true не должен сбросить default другого типа
            assertThat(recipientRepository.findById(b2c.getId()).orElseThrow().isDefault()).isTrue();
            assertThat(recipientRepository.findById(b2b.getId()).orElseThrow().isDefault()).isTrue();

            // setDefault в одном типе не должен сбросить default другого типа
            recipientService.setDefault(b2cOther.getId(), USER_ID, B2C);

            assertThat(recipientRepository.findById(b2cOther.getId()).orElseThrow().isDefault()).isTrue();
            assertThat(recipientRepository.findById(b2c.getId()).orElseThrow().isDefault()).isFalse();
            assertThat(recipientRepository.findById(b2b.getId()).orElseThrow().isDefault()).isTrue();
            assertThat(recipientRepository.findById(b2bOther.getId()).orElseThrow().isDefault()).isFalse();
        }
    }
}