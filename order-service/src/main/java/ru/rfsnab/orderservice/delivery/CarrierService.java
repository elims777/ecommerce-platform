package ru.rfsnab.orderservice.delivery;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.orderservice.exception.InvalidOrderStateException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CarrierService {

    private final CarrierRepository carrierRepository;

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

    @Transactional(readOnly = true)
    public List<Carrier> getAll() {
        return carrierRepository.findAllByOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<Carrier> getEnabled() {
        return carrierRepository.findAllByEnabledTrueOrderBySortOrderAsc();
    }

    @Transactional
    public Carrier update(CarrierCode code, CarrierUpdateRequest request) {
        Carrier carrier = getCarrier(code);
        carrier.setEnabled(request.enabled());
        carrier.setAddressDeliveryEnabled(request.addressDeliveryEnabled());
        carrier.setSortOrder(request.sortOrder());
        return carrierRepository.save(carrier);
    }

    private Carrier getCarrier(CarrierCode code) {
        return carrierRepository.findById(code)
                .orElseThrow(() -> new IllegalStateException("carriers not initialized: " + code));
    }
}
