package ru.rfsnab.userservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rfsnab.userservice.exceptions.LegalEntityAlreadyExistsException;
import ru.rfsnab.userservice.exceptions.LegalEntityNotFoundException;
import ru.rfsnab.userservice.exceptions.LegalEntityNotVerifiedException;
import ru.rfsnab.userservice.models.*;
import ru.rfsnab.userservice.models.dto.legal.LegalEntityAuthResponse;
import ru.rfsnab.userservice.models.dto.legal.RegisterLegalEntityRequest;
import ru.rfsnab.userservice.models.dto.legal.SaveBankAccountRequest;
import ru.rfsnab.userservice.models.dto.legal.SaveLegalEntityAddressRequest;
import ru.rfsnab.userservice.models.dto.legal.UpdateLegalEntityRequest;
import ru.rfsnab.userservice.models.enums.LinkStatus;
import ru.rfsnab.userservice.models.enums.VerificationStatus;
import ru.rfsnab.userservice.models.kafka.LegalEntityEvent;
import ru.rfsnab.userservice.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Сервис управления юридическими лицами: регистрация, верификация, привязка к физлицам,
 * управление банковскими счетами и адресами доставки.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LegalEntityService {

    private final LegalEntityRepository legalEntityRepository;
    private final LegalEntityBankAccountRepository bankAccountRepository;
    private final LegalEntityAddressRepository addressRepository;
    private final UserLegalEntityRepository userLegalEntityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LegalEntityKafkaProducerService kafkaProducerService;

    @Transactional
    public LegalEntity register(RegisterLegalEntityRequest request) {
        if (legalEntityRepository.existsByInn(request.inn())) {
            throw new LegalEntityAlreadyExistsException("Юрлицо с таким ИНН уже зарегистрировано");
        }
        if (legalEntityRepository.existsByEmail(request.email())) {
            throw new LegalEntityAlreadyExistsException("Юрлицо с таким email уже зарегистрировано");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new LegalEntityAlreadyExistsException("Email уже используется физическим лицом");
        }

        String confirmToken = UUID.randomUUID().toString();

        LegalEntity entity = LegalEntity.builder()
                .inn(request.inn())
                .ogrn(request.ogrn())
                .fullName(request.fullName())
                .director(request.director())
                .phone(request.phone())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .legalCity(request.legalCity())
                .legalStreet(request.legalStreet())
                .legalBuilding(request.legalBuilding())
                .legalPostalCode(request.legalPostalCode())
                .verificationStatus(VerificationStatus.PENDING)
                .emailConfirmToken(confirmToken)
                .emailVerified(false)
                .build();

        entity = legalEntityRepository.save(entity);

        kafkaProducerService.send(new LegalEntityEvent(
                "LEGAL_ENTITY_REGISTERED",
                entity.getId(), entity.getInn(), entity.getFullName(),
                entity.getEmail(), entity.getEmail(),
                null, LocalDateTime.now(), confirmToken
        ));

        log.info("Legal entity registered: inn={}, id={}", entity.getInn(), entity.getId());
        return entity;
    }

    @Transactional
    public void confirmEmail(String token) {
        LegalEntity entity = legalEntityRepository.findByEmailConfirmToken(token)
                .orElseThrow(() -> new LegalEntityNotFoundException("Недействительная ссылка подтверждения"));

        entity.setEmailVerified(true);
        entity.setEmailConfirmToken(null);
        legalEntityRepository.save(entity);

        kafkaProducerService.send(new LegalEntityEvent(
                "LEGAL_ENTITY_EMAIL_CONFIRMED",
                entity.getId(), entity.getInn(), entity.getFullName(),
                entity.getEmail(), "manager@rfsnab.ru",
                null, LocalDateTime.now(), null
        ));

        log.info("Legal entity email confirmed: id={}", entity.getId());
    }

    @Transactional
    public LegalEntity verify(Long id, String managerEmail) {
        LegalEntity entity = getById(id);
        entity.setVerificationStatus(VerificationStatus.VERIFIED);
        entity.setVerifiedBy(managerEmail);
        entity.setVerifiedAt(LocalDateTime.now());
        entity = legalEntityRepository.save(entity);

        kafkaProducerService.send(new LegalEntityEvent(
                "LEGAL_ENTITY_VERIFIED",
                entity.getId(), entity.getInn(), entity.getFullName(),
                entity.getEmail(), entity.getEmail(),
                null, LocalDateTime.now(), null
        ));

        log.info("Legal entity verified: id={} by {}", id, managerEmail);
        return entity;
    }

    @Transactional
    public LegalEntity reject(Long id, String managerEmail, String reason) {
        LegalEntity entity = getById(id);
        entity.setVerificationStatus(VerificationStatus.REJECTED);
        entity.setVerifiedBy(managerEmail);
        entity.setVerifiedAt(LocalDateTime.now());
        entity = legalEntityRepository.save(entity);

        kafkaProducerService.send(new LegalEntityEvent(
                "LEGAL_ENTITY_REJECTED",
                entity.getId(), entity.getInn(), entity.getFullName(),
                entity.getEmail(), entity.getEmail(),
                reason, LocalDateTime.now(), null
        ));

        log.info("Legal entity rejected: id={} by {}, reason: {}", id, managerEmail, reason);
        return entity;
    }

    @Transactional
    public LegalEntity update(Long id, UpdateLegalEntityRequest request) {
        LegalEntity entity = getById(id);
        entity.setFullName(request.fullName());
        entity.setDirector(request.director());
        entity.setDirectorTitle(request.directorTitle());
        entity.setBasisOfAuthority(request.basisOfAuthority());
        entity.setOffice(request.office());
        entity.setPhone(request.phone());
        entity.setLegalCity(request.legalCity());
        entity.setLegalStreet(request.legalStreet());
        entity.setLegalBuilding(request.legalBuilding());
        entity.setLegalPostalCode(request.legalPostalCode());
        entity = legalEntityRepository.save(entity);
        log.info("Legal entity updated: id={}", id);
        return entity;
    }

    @Transactional
    public void linkToUser(Long userId, String inn) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new LegalEntityNotFoundException("Пользователь не найден"));
        LegalEntity entity = legalEntityRepository.findByInn(inn)
                .orElseThrow(() -> new LegalEntityNotFoundException("Юрлицо с ИНН " + inn + " не найдено"));

        if (entity.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new LegalEntityNotVerifiedException("Юрлицо не прошло верификацию");
        }
        if (userLegalEntityRepository.existsByUserIdAndLegalEntityId(userId, entity.getId())) {
            throw new LegalEntityAlreadyExistsException("Юрлицо уже привязано к этому аккаунту");
        }

        String linkToken = UUID.randomUUID().toString();
        UserLegalEntity link = UserLegalEntity.builder()
                .user(user)
                .legalEntity(entity)
                .linkStatus(LinkStatus.PENDING)
                .linkToken(linkToken)
                .build();
        userLegalEntityRepository.save(link);

        kafkaProducerService.send(new LegalEntityEvent(
                "LEGAL_ENTITY_LINK_REQUESTED",
                entity.getId(), entity.getInn(), entity.getFullName(),
                entity.getEmail(), entity.getEmail(),
                user.getFirstname() + " " + user.getLastname(), LocalDateTime.now(), linkToken
        ));

        log.info("Link requested: userId={} → legalEntityId={}", userId, entity.getId());
    }

    @Transactional
    public void confirmLink(String token) {
        UserLegalEntity link = userLegalEntityRepository.findByLinkToken(token)
                .orElseThrow(() -> new LegalEntityNotFoundException("Недействительная ссылка привязки"));

        link.setLinkStatus(LinkStatus.CONFIRMED);
        link.setLinkToken(null);
        link.setLinkedAt(LocalDateTime.now());
        userLegalEntityRepository.save(link);

        LegalEntity entity = link.getLegalEntity();
        UserEntity user = link.getUser();

        kafkaProducerService.send(new LegalEntityEvent(
                "LEGAL_ENTITY_LINK_CONFIRMED",
                entity.getId(), entity.getInn(), entity.getFullName(),
                entity.getEmail(), user.getEmail(),
                null, LocalDateTime.now(), null
        ));

        log.info("Link confirmed: userId={} → legalEntityId={}", user.getId(), entity.getId());
    }

    @Transactional(readOnly = true)
    public LegalEntity getById(Long id) {
        return legalEntityRepository.findById(id)
                .orElseThrow(() -> new LegalEntityNotFoundException("Юрлицо не найдено: " + id));
    }

    @Transactional(readOnly = true)
    public List<LegalEntity> getByVerificationStatus(VerificationStatus status) {
        return legalEntityRepository.findAllByVerificationStatus(status);
    }

    @Transactional(readOnly = true)
    public List<UserLegalEntity> getConfirmedLinksForUser(Long userId) {
        return userLegalEntityRepository.findAllByUserIdAndLinkStatus(userId, LinkStatus.CONFIRMED);
    }

    @Transactional
    public LegalEntityBankAccount addBankAccount(Long legalEntityId, SaveBankAccountRequest request) {
        LegalEntity entity = getById(legalEntityId);
        LegalEntityBankAccount account = LegalEntityBankAccount.builder()
                .legalEntity(entity)
                .bankName(request.bankName())
                .bik(request.bik())
                .correspondentAccount(request.correspondentAccount())
                .settlementAccount(request.settlementAccount())
                .primary(request.primary())
                .build();
        return bankAccountRepository.save(account);
    }

    @Transactional
    public void deleteBankAccount(Long legalEntityId, Long accountId) {
        LegalEntityBankAccount account = bankAccountRepository
                .findByIdAndLegalEntityId(accountId, legalEntityId)
                .orElseThrow(() -> new LegalEntityNotFoundException("Банковский счёт не найден"));
        bankAccountRepository.delete(account);
    }

    @Transactional(readOnly = true)
    public List<LegalEntityBankAccount> getBankAccounts(Long legalEntityId) {
        return bankAccountRepository.findAllByLegalEntityId(legalEntityId);
    }

    @Transactional
    public LegalEntityAddress addAddress(Long legalEntityId, SaveLegalEntityAddressRequest request) {
        LegalEntity entity = getById(legalEntityId);
        LegalEntityAddress address = LegalEntityAddress.builder()
                .legalEntity(entity)
                .city(request.city())
                .street(request.street())
                .building(request.building())
                .apartment(request.apartment())
                .postalCode(request.postalCode())
                .primary(request.primary())
                .build();
        return addressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(Long legalEntityId, Long addressId) {
        LegalEntityAddress address = addressRepository
                .findByIdAndLegalEntityId(addressId, legalEntityId)
                .orElseThrow(() -> new LegalEntityNotFoundException("Адрес не найден"));
        addressRepository.delete(address);
    }

    @Transactional(readOnly = true)
    public List<LegalEntityAddress> getAddresses(Long legalEntityId) {
        return addressRepository.findAllByLegalEntityId(legalEntityId);
    }

    @Transactional(readOnly = true)
    public LegalEntityAuthResponse authenticate(String login, String password) {
        LegalEntity entity = login.matches("\\d{10}|\\d{12}")
                ? legalEntityRepository.findByInn(login)
                        .orElseThrow(() -> new LegalEntityNotFoundException("Юрлицо не найдено"))
                : legalEntityRepository.findByEmail(login)
                        .orElseThrow(() -> new LegalEntityNotFoundException("Юрлицо не найдено"));

        if (!passwordEncoder.matches(password, entity.getPassword())) {
            throw new BadCredentialsException("Неверный логин или пароль");
        }
        if (entity.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new LegalEntityNotVerifiedException("Юрлицо не прошло верификацию");
        }

        return new LegalEntityAuthResponse(entity.getId(), entity.getEmail(),
                entity.getInn(), entity.getFullName());
    }

    @Transactional(readOnly = true)
    public boolean isLinkConfirmed(Long userId, Long legalEntityId) {
        return userLegalEntityRepository
                .findByUserIdAndLegalEntityId(userId, legalEntityId)
                .map(link -> link.getLinkStatus() == LinkStatus.CONFIRMED)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<UserLegalEntity> getAllLinksForUser(Long userId) {
        return userLegalEntityRepository.findAllByUserId(userId);
    }

    @Transactional
    public void detachFromUser(Long legalEntityId, Long userId) {
        UserLegalEntity link = userLegalEntityRepository
                .findByUserIdAndLegalEntityId(userId, legalEntityId)
                .orElseThrow(() -> new LegalEntityNotFoundException(
                        "Связь пользователя " + userId + " с юрлицом " + legalEntityId + " не найдена"));
        userLegalEntityRepository.delete(link);
        log.info("Legal entity {} detached from user {} by admin", legalEntityId, userId);
    }
}
