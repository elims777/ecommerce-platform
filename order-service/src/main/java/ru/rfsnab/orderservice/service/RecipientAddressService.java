package ru.rfsnab.orderservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.orderservice.exception.BusinessException;
import ru.rfsnab.orderservice.mapper.RecipientAddressMapper;
import ru.rfsnab.orderservice.models.dto.recipient.RecipientAddressRequest;
import ru.rfsnab.orderservice.models.entity.Recipient;
import ru.rfsnab.orderservice.models.entity.RecipientAddress;
import ru.rfsnab.orderservice.repository.RecipientAddressRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipientAddressService {

    private final RecipientAddressRepository addressRepository;
    private final RecipientAddressMapper addressMapper;
    private final RecipientService recipientService;

    /** Возвращает все адреса получателя. */
    public List<RecipientAddress> getByRecipientId(Long recipientId, Long userId) {
        recipientService.getById(recipientId, userId);
        return addressRepository.findByRecipientId(recipientId);
    }

    /** Возвращает адрес по id с проверкой владельца. */
    public RecipientAddress getById(Long id, Long recipientId, Long userId) {
        recipientService.getById(recipientId, userId);
        return addressRepository.findById(id)
                .filter(a -> a.getRecipient().getId().equals(recipientId))
                .orElseThrow(() -> new BusinessException("Адрес не найден"));
    }

    /** Создаёт новый адрес для получателя. */
    @Transactional
    public RecipientAddress create(Long recipientId, Long userId, RecipientAddressRequest request) {
        Recipient recipient = recipientService.getById(recipientId, userId);
        if (request.isDefault()) {
            resetDefault(recipientId);
        }
        RecipientAddress address = addressMapper.toEntity(request);
        address.setRecipient(recipient);
        return addressRepository.save(address);
    }

    /** Обновляет данные адреса. */
    @Transactional
    public RecipientAddress update(Long id, Long recipientId, Long userId, RecipientAddressRequest request) {
        RecipientAddress address = getById(id, recipientId, userId);
        address.setLabel(request.label());
        address.setCity(request.city());
        address.setStreet(request.street());
        address.setBuilding(request.building());
        address.setApartment(request.apartment());
        address.setPostalCode(request.postalCode());
        return addressRepository.save(address);
    }

    /** Удаляет адрес получателя. */
    @Transactional
    public void delete(Long id, Long recipientId, Long userId) {
        RecipientAddress address = getById(id, recipientId, userId);
        addressRepository.delete(address);
    }

    /** Устанавливает адрес по умолчанию, сбрасывая предыдущий. */
    @Transactional
    public RecipientAddress setDefault(Long id, Long recipientId, Long userId) {
        resetDefault(recipientId);
        RecipientAddress address = getById(id, recipientId, userId);
        address.setDefault(true);
        return addressRepository.save(address);
    }

    /** Сбрасывает флаг isDefault у текущего адреса по умолчанию. */
    private void resetDefault(Long recipientId) {
        addressRepository.findByRecipientIdAndIsDefaultTrue(recipientId)
                .ifPresent(a -> {
                    a.setDefault(false);
                    addressRepository.save(a);
                });
    }
}