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
            List<Recipient> result = recipientService.getByUserId(USER_ID);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("возвращает всех получателей пользователя")
        void shouldReturnAllRecipients() {
            recipientService.create(USER_ID, new RecipientRequest("Иванов Иван", "+79001234567", false));
            recipientService.create(USER_ID, new RecipientRequest("Петров Петр", "+79007654321", false));

            List<Recipient> result = recipientService.getByUserId(USER_ID);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("не возвращает получателей другого пользователя")
        void shouldNotReturnOtherUserRecipients() {
            recipientService.create(999L, new RecipientRequest("Чужой", "+79000000000", false));

            List<Recipient> result = recipientService.getByUserId(USER_ID);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("создаёт получателя с корректными данными")
        void shouldCreateRecipient() {
            Recipient recipient = recipientService.create(USER_ID,
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
            Recipient first = recipientService.create(USER_ID,
                    new RecipientRequest("Первый", "+79001111111", true));
            Recipient second = recipientService.create(USER_ID,
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
            Recipient created = recipientService.create(USER_ID,
                    new RecipientRequest("Старое имя", "+79001234567", false));

            Recipient updated = recipientService.update(created.getId(), USER_ID,
                    new RecipientRequest("Новое имя", "+79009876543", false));

            assertThat(updated.getName()).isEqualTo("Новое имя");
            assertThat(updated.getPhone()).isEqualTo("+79009876543");
        }

        @Test
        @DisplayName("бросает исключение если получатель не найден")
        void shouldThrowIfNotFound() {
            assertThatThrownBy(() -> recipientService.update(999L, USER_ID,
                    new RecipientRequest("Имя", "+79001234567", false)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("бросает исключение если получатель принадлежит другому пользователю")
        void shouldThrowIfWrongUser() {
            Recipient created = recipientService.create(USER_ID,
                    new RecipientRequest("Иванов", "+79001234567", false));

            assertThatThrownBy(() -> recipientService.update(created.getId(), 999L,
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
            Recipient created = recipientService.create(USER_ID,
                    new RecipientRequest("Иванов", "+79001234567", false));

            recipientService.delete(created.getId(), USER_ID);

            assertThat(recipientRepository.findById(created.getId())).isEmpty();
        }

        @Test
        @DisplayName("бросает исключение если получатель не найден")
        void shouldThrowIfNotFound() {
            assertThatThrownBy(() -> recipientService.delete(999L, USER_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("setDefault")
    class SetDefaultTests {

        @Test
        @DisplayName("устанавливает получателя по умолчанию")
        void shouldSetDefault() {
            Recipient first = recipientService.create(USER_ID,
                    new RecipientRequest("Первый", "+79001111111", false));
            Recipient second = recipientService.create(USER_ID,
                    new RecipientRequest("Второй", "+79002222222", false));

            recipientService.setDefault(first.getId(), USER_ID);

            Recipient updatedFirst = recipientRepository.findById(first.getId()).orElseThrow();
            Recipient updatedSecond = recipientRepository.findById(second.getId()).orElseThrow();

            assertThat(updatedFirst.isDefault()).isTrue();
            assertThat(updatedSecond.isDefault()).isFalse();
        }

        @Test
        @DisplayName("сбрасывает предыдущий дефолтный при установке нового")
        void shouldResetPreviousDefault() {
            Recipient first = recipientService.create(USER_ID,
                    new RecipientRequest("Первый", "+79001111111", true));
            Recipient second = recipientService.create(USER_ID,
                    new RecipientRequest("Второй", "+79002222222", false));

            recipientService.setDefault(second.getId(), USER_ID);

            Recipient updatedFirst = recipientRepository.findById(first.getId()).orElseThrow();
            assertThat(updatedFirst.isDefault()).isFalse();
            assertThat(recipientRepository.findById(second.getId()).orElseThrow().isDefault()).isTrue();
        }
    }
}