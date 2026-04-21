package ru.rfsnab.orderservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.rfsnab.orderservice.BaseServiceIntegrationTest;
import ru.rfsnab.orderservice.exception.BusinessException;
import ru.rfsnab.orderservice.models.dto.recipient.RecipientAddressRequest;
import ru.rfsnab.orderservice.models.dto.recipient.RecipientRequest;
import ru.rfsnab.orderservice.models.entity.Recipient;
import ru.rfsnab.orderservice.models.entity.RecipientAddress;
import ru.rfsnab.orderservice.repository.RecipientAddressRepository;
import ru.rfsnab.orderservice.repository.RecipientRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Интеграционные тесты RecipientAddressService.
 */
@DisplayName("RecipientAddressService — интеграционные тесты")
class RecipientAddressServiceIntegrationTest extends BaseServiceIntegrationTest {

    @Autowired
    private RecipientAddressService addressService;

    @Autowired
    private RecipientService recipientService;

    @Autowired
    private RecipientRepository recipientRepository;

    @Autowired
    private RecipientAddressRepository addressRepository;

    private static final Long USER_ID = 300L;

    private Recipient recipient;

    @BeforeEach
    void setUp() {
        addressRepository.deleteAll();
        recipientRepository.deleteAll();
        recipient = recipientService.create(USER_ID, new RecipientRequest("Иванов Иван", "+79001234567", false));
    }

    private RecipientAddressRequest buildAddressRequest(String label, boolean isDefault) {
        return new RecipientAddressRequest(label, "Москва", "Ленина", "1", "10", "101000", isDefault);
    }

    @Nested
    @DisplayName("getByRecipientId")
    class GetByRecipientIdTests {

        @Test
        @DisplayName("возвращает пустой список если нет адресов")
        void shouldReturnEmptyList() {
            List<RecipientAddress> result = addressService.getByRecipientId(recipient.getId(), USER_ID);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("возвращает все адреса получателя")
        void shouldReturnAllAddresses() {
            addressService.create(recipient.getId(), USER_ID, buildAddressRequest("Офис", false));
            addressService.create(recipient.getId(), USER_ID, buildAddressRequest("Склад", false));

            List<RecipientAddress> result = addressService.getByRecipientId(recipient.getId(), USER_ID);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("бросает исключение если получатель принадлежит другому пользователю")
        void shouldThrowIfWrongUser() {
            assertThatThrownBy(() -> addressService.getByRecipientId(recipient.getId(), 999L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("создаёт адрес с корректными данными")
        void shouldCreateAddress() {
            RecipientAddress address = addressService.create(recipient.getId(), USER_ID,
                    buildAddressRequest("Офис", false));

            assertThat(address.getId()).isNotNull();
            assertThat(address.getLabel()).isEqualTo("Офис");
            assertThat(address.getCity()).isEqualTo("Москва");
            assertThat(address.getRecipient().getId()).isEqualTo(recipient.getId());
            assertThat(address.isDefault()).isFalse();
        }

        @Test
        @DisplayName("при isDefault=true сбрасывает предыдущий дефолтный адрес")
        void shouldResetPreviousDefault() {
            RecipientAddress first = addressService.create(recipient.getId(), USER_ID,
                    buildAddressRequest("Первый", true));
            RecipientAddress second = addressService.create(recipient.getId(), USER_ID,
                    buildAddressRequest("Второй", true));

            RecipientAddress updatedFirst = addressRepository.findById(first.getId()).orElseThrow();
            assertThat(updatedFirst.isDefault()).isFalse();
            assertThat(second.isDefault()).isTrue();
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("обновляет данные адреса")
        void shouldUpdateAddress() {
            RecipientAddress created = addressService.create(recipient.getId(), USER_ID,
                    buildAddressRequest("Офис", false));

            RecipientAddressRequest updateRequest = new RecipientAddressRequest(
                    "Новый офис", "Питер", "Невский", "5", "20", "190000", false);
            RecipientAddress updated = addressService.update(created.getId(), recipient.getId(), USER_ID, updateRequest);

            assertThat(updated.getLabel()).isEqualTo("Новый офис");
            assertThat(updated.getCity()).isEqualTo("Питер");
            assertThat(updated.getStreet()).isEqualTo("Невский");
        }

        @Test
        @DisplayName("бросает исключение если адрес не найден")
        void shouldThrowIfNotFound() {
            RecipientAddressRequest request = buildAddressRequest("Офис", false);
            assertThatThrownBy(() -> addressService.update(999L, recipient.getId(), USER_ID, request))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("удаляет адрес получателя")
        void shouldDeleteAddress() {
            RecipientAddress created = addressService.create(recipient.getId(), USER_ID,
                    buildAddressRequest("Офис", false));

            addressService.delete(created.getId(), recipient.getId(), USER_ID);

            assertThat(addressRepository.findById(created.getId())).isEmpty();
        }

        @Test
        @DisplayName("бросает исключение если адрес не найден")
        void shouldThrowIfNotFound() {
            assertThatThrownBy(() -> addressService.delete(999L, recipient.getId(), USER_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("setDefault")
    class SetDefaultTests {

        @Test
        @DisplayName("устанавливает адрес по умолчанию")
        void shouldSetDefault() {
            RecipientAddress first = addressService.create(recipient.getId(), USER_ID,
                    buildAddressRequest("Первый", false));
            RecipientAddress second = addressService.create(recipient.getId(), USER_ID,
                    buildAddressRequest("Второй", false));

            addressService.setDefault(first.getId(), recipient.getId(), USER_ID);

            assertThat(addressRepository.findById(first.getId()).orElseThrow().isDefault()).isTrue();
            assertThat(addressRepository.findById(second.getId()).orElseThrow().isDefault()).isFalse();
        }

        @Test
        @DisplayName("сбрасывает предыдущий дефолтный при установке нового")
        void shouldResetPreviousDefault() {
            RecipientAddress first = addressService.create(recipient.getId(), USER_ID,
                    buildAddressRequest("Первый", true));
            RecipientAddress second = addressService.create(recipient.getId(), USER_ID,
                    buildAddressRequest("Второй", false));

            addressService.setDefault(second.getId(), recipient.getId(), USER_ID);

            assertThat(addressRepository.findById(first.getId()).orElseThrow().isDefault()).isFalse();
            assertThat(addressRepository.findById(second.getId()).orElseThrow().isDefault()).isTrue();
        }
    }
}