package ru.rfsnab.userservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.userservice.exceptions.AddressNotFoundException;
import ru.rfsnab.userservice.exceptions.DuplicateAddressLabelException;
import ru.rfsnab.userservice.mappers.UserAddressMapper;
import ru.rfsnab.userservice.models.UserAddress;
import ru.rfsnab.userservice.models.dto.SaveUserAddressRequest;
import ru.rfsnab.userservice.repository.UserAddressRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAddressService {
    private final UserAddressRepository userAddressRepository;

    /**
     * Все адреса пользователя. Default-адрес первым в списке.
     */
    @Transactional(readOnly = true)
    public List<UserAddress> getAddresses(Long userId) {
        return userAddressRepository.findAllByUserIdOrderByDefaultAddressDescCreatedAtAsc(userId);
    }

    /**
     * Получение адреса с проверкой принадлежности пользователю.
     */
    @Transactional(readOnly = true)
    public UserAddress getAddress(Long addressId, Long userId) {
        return userAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new AddressNotFoundException(
                        "Адрес с id=%d не найден у пользователя id=%d".formatted(addressId, userId)));
    }

    /**
     * Создание нового адреса.
     * Проверки: уникальность метки, логика default.
     */
    @Transactional
    public UserAddress createAddress(Long userId, SaveUserAddressRequest request) {
        validateLabelUniquenessForCreate(userId, request.label());

        if (request.defaultAddress()) {
            userAddressRepository.resetDefaultForUser(userId);
        }

        UserAddress address = UserAddressMapper.mapToEntity(request, userId);
        UserAddress saved = userAddressRepository.save(address);

        return saved;
    }

    /**
     * Обновление существующего адреса.
     * Проверки: owner validation, уникальность метки (исключая текущий), логика default.
     */
    @Transactional
    public UserAddress updateAddress(Long addressId, Long userId, SaveUserAddressRequest request) {
        UserAddress address = getAddress(addressId, userId);
        validateLabelUniquenessForUpdate(userId, request.label(), addressId);

        address.setLabel(request.label());
        address.setRecipientName(request.recipientName());
        address.setPhone(request.phone());
        address.setCity(request.city());
        address.setStreet(request.street());
        address.setBuilding(request.building());
        address.setApartment(request.apartment());
        address.setEntrance(request.entrance());
        address.setFloor(request.floor());
        address.setIntercomCode(request.intercomCode());
        address.setPostalCode(request.postalCode());
        address.setDeliveryInstructions(request.deliveryInstructions());

        if (request.defaultAddress() && !address.isDefaultAddress()) {
            userAddressRepository.resetDefaultForUser(userId);
        }
        address.setDefaultAddress(request.defaultAddress());

        return address;
    }

    /**
     * Удаление адреса с проверкой владельца.
     */
    @Transactional
    public void deleteAddress(Long addressId, Long userId) {
        UserAddress address = getAddress(addressId, userId);
        userAddressRepository.delete(address);
    }

    /**
     * Установить адрес по умолчанию.
     */
    @Transactional
    public UserAddress setDefault(Long addressId, Long userId) {
        UserAddress address = getAddress(addressId, userId);

        if (!address.isDefaultAddress()) {
            userAddressRepository.resetDefaultForUser(userId);
            address.setDefaultAddress(true);
        }

        return address;
    }

    private void validateLabelUniquenessForCreate(Long userId, String label) {
        if (userAddressRepository.existsByUserIdAndLabelIgnoreCase(userId, label)) {
            throw new DuplicateAddressLabelException(
                    "Адрес с названием '%s' уже существует".formatted(label));
        }
    }

    private void validateLabelUniquenessForUpdate(Long userId, String label, Long excludeId) {
        if (userAddressRepository.existsByUserIdAndLabelIgnoreCaseAndIdNot(userId, label, excludeId)) {
            throw new DuplicateAddressLabelException(
                    "Адрес с названием '%s' уже существует".formatted(label));
        }
    }
}
