package ru.rfsnab.integrationservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.integrationservice.BaseIntegrationTest;
import ru.rfsnab.integrationservice.model.PendingOrder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PendingOrderRepository")
class PendingOrderRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private PendingOrderRepository pendingOrderRepository;

    @BeforeEach
    void cleanup() {
        pendingOrderRepository.deleteAll();
    }

    private PendingOrder saveOrder(String orderId, boolean exported) {
        PendingOrder order = PendingOrder.builder()
                .orderId(orderId)
                .externalId("ORD-" + orderId)
                .orderData("{\"orderId\": \"" + orderId + "\"}")
                .exported(exported)
                .build();
        if (exported) {
            order.setExportedAt(LocalDateTime.now());
        }
        return pendingOrderRepository.save(order);
    }

    @Test
    @DisplayName("findByExportedFalse — только неэкспортированные")
    void shouldFindOnlyNonExported() {
        saveOrder("uuid-r1", false);
        saveOrder("uuid-r2", false);
        saveOrder("uuid-r3", true);

        List<PendingOrder> result = pendingOrderRepository.findByExportedFalseOrderByCreatedAtAsc();

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(o -> !o.isExported());
    }

    @Test
    @DisplayName("existsByOrderId — проверка дубликата")
    void shouldCheckExistence() {
        saveOrder("uuid-exists", false);

        assertThat(pendingOrderRepository.existsByOrderId("uuid-exists")).isTrue();
        assertThat(pendingOrderRepository.existsByOrderId("uuid-missing")).isFalse();
    }

    @Test
    @DisplayName("markAllAsExported — обновляет все неэкспортированные")
    @Transactional
    void shouldMarkAllAsExported() {
        saveOrder("uuid-m1", false);
        saveOrder("uuid-m2", false);
        saveOrder("uuid-m3", true); // уже exported

        int count = pendingOrderRepository.markAllAsExported(LocalDateTime.now());

        assertThat(count).isEqualTo(2);
    }
}
