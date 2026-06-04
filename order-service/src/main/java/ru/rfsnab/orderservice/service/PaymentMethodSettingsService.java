package ru.rfsnab.orderservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.orderservice.models.dto.payment.PaymentMethodSettingsDto;
import ru.rfsnab.orderservice.models.entity.PaymentMethodSettings;
import ru.rfsnab.orderservice.repository.PaymentMethodSettingsRepository;

@Service
@RequiredArgsConstructor
public class PaymentMethodSettingsService {

    private final PaymentMethodSettingsRepository repository;

    @Transactional(readOnly = true)
    public PaymentMethodSettingsDto getSettings() {
        PaymentMethodSettings s = repository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("payment_method_settings not initialized"));
        return new PaymentMethodSettingsDto(s.isSbpEnabled(), s.isCardEnabled());
    }

    @Transactional
    public PaymentMethodSettingsDto updateSettings(PaymentMethodSettingsDto dto) {
        PaymentMethodSettings s = repository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("payment_method_settings not initialized"));
        s.setSbpEnabled(dto.sbpEnabled());
        s.setCardEnabled(dto.cardEnabled());
        repository.save(s);
        return new PaymentMethodSettingsDto(s.isSbpEnabled(), s.isCardEnabled());
    }
}
