package ru.rfsnab.authservice.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailMasker unit tests")
class EmailMaskerTest {

    @Test
    @DisplayName("mask() - оставляет первую букву и домен")
    void mask_ValidEmail_KeepsFirstCharAndDomain() {
        assertThat(EmailMasker.mask("ivan@mail.ru")).isEqualTo("i***@mail.ru");
    }

    @Test
    @DisplayName("mask() - null и строка без локальной части маскируются полностью")
    void mask_NullOrMalformed_ReturnsMask() {
        assertThat(EmailMasker.mask(null)).isEqualTo("***");
        assertThat(EmailMasker.mask("no-at-sign")).isEqualTo("***");
        assertThat(EmailMasker.mask("@mail.ru")).isEqualTo("***");
    }
}
