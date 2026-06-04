package ru.rfsnab.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rfsnab.orderservice.models.entity.PaymentMethodSettings;

public interface PaymentMethodSettingsRepository extends JpaRepository<PaymentMethodSettings, Long> {
}
