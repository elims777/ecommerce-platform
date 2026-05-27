package ru.rfsnab.userservice.services;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import ru.rfsnab.userservice.exceptions.LegalEntityNotVerifiedException;
import ru.rfsnab.userservice.models.LegalEntity;
import ru.rfsnab.userservice.models.enums.VerificationStatus;
import ru.rfsnab.userservice.repository.LegalEntityRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class LegalEntityAuthTest {

    @Autowired LegalEntityService service;
    @Autowired LegalEntityRepository repo;
    @Autowired PasswordEncoder encoder;

    @Test
    void authenticateByEmail_verified_returnsDto() {
        LegalEntity entity = repo.save(LegalEntity.builder()
                .inn("1234567890").ogrn("1234567890123").fullName("ООО Тест").director("Иванов")
                .phone("+71234567890").email("legal@test.ru")
                .password(encoder.encode("secret"))
                .verificationStatus(VerificationStatus.VERIFIED)
                .emailVerified(true)
                .legalCity("Москва").legalStreet("Ленина").legalBuilding("1").legalPostalCode("100000")
                .build());

        var result = service.authenticate("legal@test.ru", "secret");

        assertThat(result.id()).isEqualTo(entity.getId());
        assertThat(result.email()).isEqualTo("legal@test.ru");
        assertThat(result.inn()).isEqualTo("1234567890");
    }

    @Test
    void authenticateByInn_verified_returnsDto() {
        repo.save(LegalEntity.builder()
                .inn("9876543210").ogrn("9876543210123").fullName("ООО Тест2").director("Петров")
                .phone("+71234567891").email("legal2@test.ru")
                .password(encoder.encode("secret"))
                .verificationStatus(VerificationStatus.VERIFIED)
                .emailVerified(true)
                .legalCity("СПб").legalStreet("Невский").legalBuilding("1").legalPostalCode("190000")
                .build());

        var result = service.authenticate("9876543210", "secret");
        assertThat(result.inn()).isEqualTo("9876543210");
    }

    @Test
    void authenticate_pendingEntity_throws() {
        repo.save(LegalEntity.builder()
                .inn("1111111111").ogrn("1111111111111").fullName("ООО Ожидание").director("Сидоров")
                .phone("+71234567892").email("pending@test.ru")
                .password(encoder.encode("secret"))
                .verificationStatus(VerificationStatus.PENDING)
                .emailVerified(false)
                .legalCity("Казань").legalStreet("Кремль").legalBuilding("1").legalPostalCode("420000")
                .build());

        assertThatThrownBy(() -> service.authenticate("pending@test.ru", "secret"))
                .isInstanceOf(LegalEntityNotVerifiedException.class);
    }

    @Test
    void authenticate_wrongPassword_throwsBadCredentials() {
        repo.save(LegalEntity.builder()
                .inn("2222222222").ogrn("2222222222222").fullName("ООО Неправильный").director("Козлов")
                .phone("+71234567893").email("wrong@test.ru")
                .password(encoder.encode("correct"))
                .verificationStatus(VerificationStatus.VERIFIED)
                .emailVerified(true)
                .legalCity("НН").legalStreet("Большая").legalBuilding("1").legalPostalCode("603000")
                .build());

        assertThatThrownBy(() -> service.authenticate("wrong@test.ru", "badpass"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
