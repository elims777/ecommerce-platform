package ru.rfsnab.userservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.rfsnab.userservice.exceptions.LegalEntityAlreadyExistsException;
import ru.rfsnab.userservice.exceptions.LegalEntityNotFoundException;
import ru.rfsnab.userservice.models.LegalEntity;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.models.UserLegalEntity;
import ru.rfsnab.userservice.models.dto.legal.RegisterLegalEntityRequest;
import ru.rfsnab.userservice.models.enums.LinkStatus;
import ru.rfsnab.userservice.models.enums.VerificationStatus;
import ru.rfsnab.userservice.models.kafka.LegalEntityEvent;
import ru.rfsnab.userservice.repository.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LegalEntityService — unit tests")
class LegalEntityServiceTest {

    @Mock LegalEntityRepository legalEntityRepository;
    @Mock LegalEntityBankAccountRepository bankAccountRepository;
    @Mock LegalEntityAddressRepository addressRepository;
    @Mock UserLegalEntityRepository userLegalEntityRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock LegalEntityKafkaProducerService kafkaProducerService;

    @InjectMocks LegalEntityService legalEntityService;

    private RegisterLegalEntityRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new RegisterLegalEntityRequest(
                "1234567890", "1234567890123",
                "ООО Ромашка", "Иванов Иван Иванович",
                "+79001112233", "company@example.com", "password123",
                "Сыктывкар", "Октябрьский проспект", "1", "167000"
        );
    }

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("регистрирует юрлицо с PENDING статусом")
        void shouldRegisterLegalEntity() {
            when(legalEntityRepository.existsByInn("1234567890")).thenReturn(false);
            when(legalEntityRepository.existsByEmail("company@example.com")).thenReturn(false);
            when(userRepository.existsByEmail("company@example.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("encoded");
            when(legalEntityRepository.save(any())).thenAnswer(inv -> {
                LegalEntity e = inv.getArgument(0);
                return LegalEntity.builder()
                        .id(1L).inn(e.getInn()).ogrn(e.getOgrn())
                        .fullName(e.getFullName()).director(e.getDirector())
                        .phone(e.getPhone()).email(e.getEmail()).password(e.getPassword())
                        .legalCity(e.getLegalCity()).legalStreet(e.getLegalStreet())
                        .legalBuilding(e.getLegalBuilding())
                        .verificationStatus(VerificationStatus.PENDING)
                        .emailVerified(false)
                        .build();
            });
            doNothing().when(kafkaProducerService).send(any(LegalEntityEvent.class));

            LegalEntity result = legalEntityService.register(validRequest);

            assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
            assertThat(result.isEmailVerified()).isFalse();
            assertThat(result.getPassword()).isEqualTo("encoded");
            verify(kafkaProducerService).send(argThat(e -> "LEGAL_ENTITY_REGISTERED".equals(e.eventType())));
        }

        @Test
        @DisplayName("выбрасывает исключение при дублирующем ИНН")
        void shouldThrowWhenInnExists() {
            when(legalEntityRepository.existsByInn("1234567890")).thenReturn(true);

            assertThatThrownBy(() -> legalEntityService.register(validRequest))
                    .isInstanceOf(LegalEntityAlreadyExistsException.class)
                    .hasMessageContaining("ИНН");
        }

        @Test
        @DisplayName("выбрасывает исключение при дублирующем email")
        void shouldThrowWhenEmailExists() {
            when(legalEntityRepository.existsByInn("1234567890")).thenReturn(false);
            when(legalEntityRepository.existsByEmail("company@example.com")).thenReturn(true);

            assertThatThrownBy(() -> legalEntityService.register(validRequest))
                    .isInstanceOf(LegalEntityAlreadyExistsException.class)
                    .hasMessageContaining("email");
        }
    }

    @Nested
    @DisplayName("confirmEmail")
    class ConfirmEmailTests {

        @Test
        @DisplayName("подтверждает email и отправляет уведомление менеджеру")
        void shouldConfirmEmail() {
            LegalEntity entity = LegalEntity.builder()
                    .id(1L).email("company@example.com").fullName("ООО Ромашка").inn("1234567890")
                    .emailConfirmToken("test-token").emailVerified(false)
                    .verificationStatus(VerificationStatus.PENDING)
                    .build();
            when(legalEntityRepository.findByEmailConfirmToken("test-token")).thenReturn(Optional.of(entity));
            when(legalEntityRepository.save(any())).thenReturn(entity);
            doNothing().when(kafkaProducerService).send(any());

            legalEntityService.confirmEmail("test-token");

            assertThat(entity.isEmailVerified()).isTrue();
            assertThat(entity.getEmailConfirmToken()).isNull();
            verify(kafkaProducerService).send(argThat(e -> "LEGAL_ENTITY_EMAIL_CONFIRMED".equals(e.eventType())));
        }

        @Test
        @DisplayName("выбрасывает исключение при невалидном токене")
        void shouldThrowWhenTokenInvalid() {
            when(legalEntityRepository.findByEmailConfirmToken("bad-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> legalEntityService.confirmEmail("bad-token"))
                    .isInstanceOf(LegalEntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("verify / reject")
    class VerificationTests {

        @Test
        @DisplayName("менеджер верифицирует юрлицо → VERIFIED")
        void shouldVerifyLegalEntity() {
            LegalEntity entity = LegalEntity.builder()
                    .id(1L).email("company@example.com").fullName("ООО Ромашка").inn("1234567890")
                    .verificationStatus(VerificationStatus.PENDING).emailVerified(true)
                    .build();
            when(legalEntityRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(legalEntityRepository.save(any())).thenReturn(entity);
            doNothing().when(kafkaProducerService).send(any());

            legalEntityService.verify(1L, "manager@rfsnab.ru");

            assertThat(entity.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
            assertThat(entity.getVerifiedBy()).isEqualTo("manager@rfsnab.ru");
            assertThat(entity.getVerifiedAt()).isNotNull();
            verify(kafkaProducerService).send(argThat(e -> "LEGAL_ENTITY_VERIFIED".equals(e.eventType())));
        }

        @Test
        @DisplayName("менеджер отклоняет юрлицо → REJECTED")
        void shouldRejectLegalEntity() {
            LegalEntity entity = LegalEntity.builder()
                    .id(1L).email("company@example.com").fullName("ООО Ромашка").inn("1234567890")
                    .verificationStatus(VerificationStatus.PENDING).emailVerified(true)
                    .build();
            when(legalEntityRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(legalEntityRepository.save(any())).thenReturn(entity);
            doNothing().when(kafkaProducerService).send(any());

            legalEntityService.reject(1L, "manager@rfsnab.ru", "Недействительный ИНН");

            assertThat(entity.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
            verify(kafkaProducerService).send(argThat(e ->
                    "LEGAL_ENTITY_REJECTED".equals(e.eventType()) &&
                    "Недействительный ИНН".equals(e.rejectionReason())));
        }
    }

    @Nested
    @DisplayName("getAllLinksForUser / detachFromUser")
    class AdminLinkTests {

        @Test
        @DisplayName("getAllLinksForUser — возвращает все связи пользователя")
        void shouldReturnAllLinksForUser() {
            UserEntity user = UserEntity.builder().id(1L).email("user@example.com")
                    .firstname("Иван").lastname("Иванов").build();
            LegalEntity le1 = LegalEntity.builder().id(10L).inn("1111111111").build();
            LegalEntity le2 = LegalEntity.builder().id(11L).inn("2222222222").build();
            UserLegalEntity link1 = UserLegalEntity.builder()
                    .user(user).legalEntity(le1).linkStatus(LinkStatus.CONFIRMED).build();
            UserLegalEntity link2 = UserLegalEntity.builder()
                    .user(user).legalEntity(le2).linkStatus(LinkStatus.PENDING).build();

            when(userLegalEntityRepository.findAllByUserId(1L)).thenReturn(List.of(link1, link2));

            List<UserLegalEntity> result = legalEntityService.getAllLinksForUser(1L);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(l -> l.getLegalEntity().getInn())
                    .containsExactlyInAnyOrder("1111111111", "2222222222");
            verify(userLegalEntityRepository).findAllByUserId(1L);
        }

        @Test
        @DisplayName("detachFromUser — удаляет связь юрлицо-пользователь")
        void shouldDetachLegalEntityFromUser() {
            UserEntity user = UserEntity.builder().id(1L).build();
            LegalEntity le = LegalEntity.builder().id(10L).build();
            UserLegalEntity link = UserLegalEntity.builder()
                    .user(user).legalEntity(le).linkStatus(LinkStatus.CONFIRMED).build();

            when(userLegalEntityRepository.findByUserIdAndLegalEntityId(1L, 10L))
                    .thenReturn(Optional.of(link));
            doNothing().when(userLegalEntityRepository).delete(link);

            legalEntityService.detachFromUser(10L, 1L);

            verify(userLegalEntityRepository).delete(link);
        }

        @Test
        @DisplayName("detachFromUser — выбрасывает исключение если связь не найдена")
        void shouldThrowWhenLinkNotFound() {
            when(userLegalEntityRepository.findByUserIdAndLegalEntityId(1L, 99L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> legalEntityService.detachFromUser(99L, 1L))
                    .isInstanceOf(LegalEntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("linkToUser")
    class LinkTests {

        @Test
        @DisplayName("физлицо привязывает юрлицо по ИНН → PENDING")
        void shouldCreatePendingLink() {
            UserEntity user = UserEntity.builder().id(1L).email("user@example.com")
                    .firstname("Иван").lastname("Иванов").build();
            LegalEntity entity = LegalEntity.builder()
                    .id(10L).inn("1234567890").email("company@example.com")
                    .fullName("ООО Ромашка").verificationStatus(VerificationStatus.VERIFIED)
                    .build();
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(legalEntityRepository.findByInn("1234567890")).thenReturn(Optional.of(entity));
            when(userLegalEntityRepository.existsByUserIdAndLegalEntityId(1L, 10L)).thenReturn(false);
            when(userLegalEntityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(kafkaProducerService).send(any());

            legalEntityService.linkToUser(1L, "1234567890");

            verify(userLegalEntityRepository).save(argThat(link ->
                    link.getLinkStatus() == LinkStatus.PENDING));
            verify(kafkaProducerService).send(argThat(e -> "LEGAL_ENTITY_LINK_REQUESTED".equals(e.eventType())));
        }

        @Test
        @DisplayName("подтверждение привязки по токену → CONFIRMED")
        void shouldConfirmLink() {
            UserEntity user = UserEntity.builder().id(1L).email("user@example.com")
                    .firstname("Иван").lastname("Иванов").build();
            LegalEntity entity = LegalEntity.builder()
                    .id(10L).email("company@example.com").fullName("ООО Ромашка").build();
            UserLegalEntity link = UserLegalEntity.builder()
                    .user(user).legalEntity(entity)
                    .linkStatus(LinkStatus.PENDING).linkToken("link-token")
                    .build();
            when(userLegalEntityRepository.findByLinkToken("link-token")).thenReturn(Optional.of(link));
            when(userLegalEntityRepository.save(any())).thenReturn(link);
            doNothing().when(kafkaProducerService).send(any());

            legalEntityService.confirmLink("link-token");

            assertThat(link.getLinkStatus()).isEqualTo(LinkStatus.CONFIRMED);
            assertThat(link.getLinkedAt()).isNotNull();
            verify(kafkaProducerService).send(argThat(e -> "LEGAL_ENTITY_LINK_CONFIRMED".equals(e.eventType())));
        }
    }
}
