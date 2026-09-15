package org.syu_likelion.Festa_2026.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ResetPasswordTests {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() { FACTORY.close(); }

    @ParameterizedTest
    @ValueSource(strings = {"Abcdef1!", "Abcdefghijklmnopq1!Z", "Abcdef1~"})
    void acceptsAsciiPasswordsAtBothLengthBoundaries(String password) {
        assertThat(VALIDATOR.validate(request(password))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"        ", "Abcde1!", "Abcdefghijklmnopqr1!Z", "abcdef1!", "ABCDEF1!",
            "Abcdefg!", "Abcdef12", " Abcdef1!", "Abcdef1! ", "Abcd ef1!", "Abcdef1!\t",
            "Abcdef1!\n", "Abcdef1!한", "Abcdef1!😀", "Abcdef1！", "Abcdef1!\u007f"})
    void rejectsInvalidPasswordsWithoutTrimming(String password) {
        assertThat(VALIDATOR.validate(request(password))).singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("newPassword"));
    }

    private AuthDtos.ResetPasswordRequest request(String password) {
        return new AuthDtos.ResetPasswordRequest("festival01", "student@example.com", "123456", password);
    }
}
