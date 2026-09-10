package org.syu_likelion.Festa_2026.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SignupPasswordTests {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() { FACTORY.close(); }

    @ParameterizedTest
    @ValueSource(strings = {"Abcdef1!", "Abcdefghijklmnopq1!Z", "Abcdef1_", "Abcdef1~"})
    void acceptsValidPasswords(String password) {
        assertThat(VALIDATOR.validate(request(password))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "　"})
    void reportsRequiredForMissingOrBlankPasswords(String password) {
        assertThat(VALIDATOR.validate(request(password)))
                .singleElement().satisfies(v -> assertThat(v.getMessage()).isEqualTo("password is required"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Abcde1!", "Abcdefghijklmnopqr1!Z", "abcdef1!", "ABCDEF1!",
            "Abcdefg!", "Abcdef12", " Abcdef1!", "Abcdef1! ", "Abcd ef1!", "Abcdef1!\t",
            "Abcdef1!\n", "Abcdef1!한", "Abcdef1!😀", "Abcdef1！", "Abcdef1!\u007f"})
    void rejectsPolicyViolations(String password) {
        assertThat(VALIDATOR.validate(request(password)))
                .singleElement().satisfies(v -> assertThat(v.getMessage()).isEqualTo(
                        "password must be 8-20 characters and include uppercase, lowercase, digit, and special character (ASCII only, no spaces)"));
    }

    private AuthDtos.SignupRequest request(String password) {
        return new AuthDtos.SignupRequest("festival01", password, "student@example.com",
                "홍길동", null, "20260001", "컴퓨터공학과", null, null, null);
    }
}
