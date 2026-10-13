package ru.rfsnab.orderservice.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.orderservice.exception.InvalidOrderStateException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarrierService {

    private final CarrierRepository carrierRepository;
    private final List<CarrierClient> clients;
    private final ObjectMapper objectMapper;

    /**
     * Проверяет, что через ТК сейчас можно оформить доставку до адреса.
     *
     * @return ТК — для снимка названия в заказе
     * @throws InvalidOrderStateException если ТК выключена или доставка до адреса у неё выключена
     */
    @Transactional(readOnly = true)
    public Carrier requireAddressDelivery(CarrierCode code) {
        Carrier carrier = getCarrier(code);
        if (!carrier.isEnabled() || !carrier.isAddressDeliveryEnabled()) {
            throw new InvalidOrderStateException(
                    "Доставка до адреса через " + carrier.getName() + " сейчас недоступна");
        }
        return carrier;
    }

    /**
     * Проверяет, что через ТК сейчас можно оформить доставку до терминала.
     *
     * @return ТК — для снимка названия в заказе
     * @throws InvalidOrderStateException если ТК выключена или доставка до терминала у неё выключена
     */
    @Transactional(readOnly = true)
    public Carrier requireTerminalDelivery(CarrierCode code) {
        Carrier carrier = getCarrier(code);
        if (!carrier.isEnabled() || !carrier.isTerminalDeliveryEnabled()) {
            throw new InvalidOrderStateException(
                    "Доставка до терминала через " + carrier.getName() + " сейчас недоступна");
        }
        return carrier;
    }

    /** Все ТК для админки, по порядку вывода. */
    @Transactional(readOnly = true)
    public List<CarrierDto> getAll() {
        return carrierRepository.findAllByOrderBySortOrderAsc().stream().map(this::toDto).toList();
    }

    /** Включённые ТК для клиента, по порядку вывода; служебные поля синхронизации скрыты. */
    @Transactional(readOnly = true)
    public List<CarrierDto> getEnabled() {
        return carrierRepository.findAllByEnabledTrueOrderBySortOrderAsc().stream().map(this::toDto).map(CarrierDto::forClient).toList();
    }

    /**
     * Обновляет настройки ТК. Флаг «до терминала» включается только при наличии активных терминалов
     * и только админом: синхронизация сама его не включает.
     *
     * @throws InvalidOrderStateException если терминальную доставку включают без терминалов
     */
    @Transactional
    public CarrierDto update(CarrierCode code, CarrierUpdateRequest request) {
        Carrier carrier = getCarrier(code);
        if (Boolean.TRUE.equals(request.terminalDeliveryEnabled())
                && !carrier.isTerminalDeliveryEnabled() && carrier.getActiveTerminalsCount() == 0) {
            throw new InvalidOrderStateException(
                    "Нет активных терминалов " + carrier.getName() + " — сначала выполните синхронизацию");
        }
        carrier.setEnabled(request.enabled());
        carrier.setAddressDeliveryEnabled(request.addressDeliveryEnabled());
        carrier.setSortOrder(request.sortOrder());
        if (request.terminalDeliveryEnabled() != null) {
            carrier.setTerminalDeliveryEnabled(request.terminalDeliveryEnabled());
        }
        if (request.trackingUrlTemplate() != null) {
            carrier.setTrackingUrlTemplate(request.trackingUrlTemplate().isBlank() ? null : request.trackingUrlTemplate().trim());
        }
        return toDto(carrierRepository.save(carrier));
    }

    private CarrierDto toDto(Carrier carrier) {
        boolean configured = clients.stream()
                .anyMatch(c -> c.code() == carrier.getCode() && c.isConfigured());
        boolean tracking = clients.stream()
                .anyMatch(c -> c.code() == carrier.getCode() && c.supportsStatusTracking());
        return CarrierDto.from(carrier, configured, parseReport(carrier), tracking);
    }

    private CarrierSyncReport parseReport(Carrier carrier) {
        if (carrier.getLastSyncReport() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(carrier.getLastSyncReport(), CarrierSyncReport.class);
        } catch (JsonProcessingException e) {
            log.warn("Не удалось прочитать отчёт синхронизации {}: {}", carrier.getCode(), e.getOriginalMessage());
            return null;
        }
    }

    private Carrier getCarrier(CarrierCode code) {
        return carrierRepository.findById(code)
                .orElseThrow(() -> new IllegalStateException("carriers not initialized: " + code));
    }
}
