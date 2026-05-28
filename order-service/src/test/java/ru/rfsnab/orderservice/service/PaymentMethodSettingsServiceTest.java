package ru.rfsnab.orderservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rfsnab.orderservice.models.dto.payment.PaymentMethodSettingsDto;
import ru.rfsnab.orderservice.models.entity.PaymentMethodSettings;
import ru.rfsnab.orderservice.repository.PaymentMethodSettingsRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentMethodSettingsService")
class PaymentMethodSettingsServiceTest {

    @Mock
    private PaymentMethodSettingsRepository repository;

    @InjectMocks
    private PaymentMethodSettingsService service;

    private PaymentMethodSettings defaultSettings;

    @BeforeEach
    void setUp() {
        defaultSettings = new PaymentMethodSettings();
        defaultSettings.setId(1L);
        defaultSettings.setSbpEnabled(false);
        defaultSettings.setCardEnabled(false);
        defaultSettings.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("getSettings — возвращает текущие флаги")
    void getSettings_returnsCurrentFlags() {
        when(repository.findById(1L)).thenReturn(Optional.of(defaultSettings));

        PaymentMethodSettingsDto result = service.getSettings();

        assertThat(result.sbpEnabled()).isFalse();
        assertThat(result.cardEnabled()).isFalse();
    }

    @Test
    @DisplayName("getSettings — бросает IllegalStateException если строка не инициализирована")
    void getSettings_throwsWhenNotInitialized() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSettings())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not initialized");
    }

    @Test
    @DisplayName("updateSettings — обновляет оба флага и возвращает новые значения")
    void updateSettings_updatesBothFlags() {
        when(repository.findById(1L)).thenReturn(Optional.of(defaultSettings));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentMethodSettingsDto result = service.updateSettings(new PaymentMethodSettingsDto(true, true));

        assertThat(result.sbpEnabled()).isTrue();
        assertThat(result.cardEnabled()).isTrue();
        verify(repository).save(defaultSettings);
    }

    @Test
    @DisplayName("updateSettings — бросает IllegalStateException если строка не инициализирована")
    void updateSettings_throwsWhenNotInitialized() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSettings(new PaymentMethodSettingsDto(true, false)))
                .isInstanceOf(IllegalStateException.class);
    }
}
