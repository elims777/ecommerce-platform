package ru.rfsnab.orderservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.orderservice.exception.BusinessException;
import ru.rfsnab.orderservice.mapper.RecipientMapper;
import ru.rfsnab.orderservice.models.dto.recipient.RecipientRequest;
import ru.rfsnab.orderservice.models.entity.Recipient;
import ru.rfsnab.orderservice.models.entity.enums.CustomerType;
import ru.rfsnab.orderservice.repository.RecipientRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipientService {

    private final RecipientRepository recipientRepository;
    private final RecipientMapper recipientMapper;

    /** Возвращает всех получателей пользователя. */
    public List<Recipient> getByUserId(Long userId, CustomerType customerType) {
        return recipientRepository.findByUserIdAndCustomerType(userId, customerType);
    }

    /** Возвращает получателя по id с проверкой владельца. */
    public Recipient getById(Long id, Long userId, CustomerType customerType) {
        return recipientRepository.findById(id)
                .filter(r -> r.getUserId().equals(userId) && r.getCustomerType() == customerType)
                .orElseThrow(() -> new BusinessException("Получатель не найден"));
    }

    /** Создаёт нового получателя для пользователя. */
    @Transactional
    public Recipient create(Long userId, CustomerType customerType, RecipientRequest request) {
        if (request.isDefault()) {
            resetDefault(userId, customerType);
        }
        Recipient recipient = recipientMapper.toEntity(request);
        recipient.setUserId(userId);
        recipient.setCustomerType(customerType);
        return recipientRepository.save(recipient);
    }

    /** Обновляет имя и телефон получателя. */
    @Transactional
    public Recipient update(Long id, Long userId, CustomerType customerType, RecipientRequest request) {
        Recipient recipient = getById(id, userId, customerType);
        recipient.setName(request.name());
        recipient.setPhone(request.phone());
        return recipientRepository.save(recipient);
    }

    /** Удаляет получателя. */
    @Transactional
    public void delete(Long id, Long userId, CustomerType customerType) {
        Recipient recipient = getById(id, userId, customerType);
        recipientRepository.delete(recipient);
    }

    /** Устанавливает получателя по умолчанию, сбрасывая предыдущий. */
    @Transactional
    public Recipient setDefault(Long id, Long userId, CustomerType customerType) {
        resetDefault(userId, customerType);
        Recipient recipient = getById(id, userId, customerType);
        recipient.setDefault(true);
        return recipientRepository.save(recipient);
    }

    /** Сбрасывает флаг isDefault у текущего получателя по умолчанию. */
    private void resetDefault(Long userId, CustomerType customerType) {
        recipientRepository.findByUserIdAndCustomerTypeAndIsDefaultTrue(userId, customerType)
                .ifPresent(r -> {
                    r.setDefault(false);
                    recipientRepository.save(r);
                });
    }
}