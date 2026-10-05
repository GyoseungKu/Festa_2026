package org.syu_likelion.Festa_2026.mail;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

class MailAccountConfigurationTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(MailAccountConfiguration.class)
            .withPropertyValues("spring.mail.host=smtp.gmail.com", "spring.mail.port=587",
                    "spring.mail.username=first@gmail.com", "spring.mail.password=test-password",
                    "spring.mail.properties.mail.smtp.starttls.required=true",
                    "spring.mail.properties.mail.smtp.timeout=15000",
                    "spring.mail.properties.mail.smtp.from=primary-alias@example.com");

    @Test void singleWrapperCopiesSettingsAndSkipsMissingSecondSlot() {
        context.withPropertyValues("mail.accounts.3.username=third@gmail.com", "mail.accounts.3.password=third-pass")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(JavaMailSender.class);
                    var wrapper = ctx.getBean(QuotaMailSender.class);
                    @SuppressWarnings("unchecked")
                    var accounts = (List<QuotaMailSender.Account>) ReflectionTestUtils.getField(wrapper, "accounts");
                    assertThat(accounts).extracting(QuotaMailSender.Account::slot).containsExactly(1, 3);
                    var primary = (JavaMailSenderImpl) accounts.get(0).sender();
                    var third = (JavaMailSenderImpl) accounts.get(1).sender();
                    assertThat(third).isNotSameAs(primary);
                    assertThat(third.getHost()).isEqualTo("smtp.gmail.com");
                    assertThat(third.getPort()).isEqualTo(587);
                    assertThat(third.getPassword()).isEqualTo("third-pass");
                    assertThat(third.getJavaMailProperties()).containsEntry("mail.smtp.timeout", "15000")
                            .containsEntry("mail.smtp.starttls.required", "true")
                            .containsEntry("mail.smtp.from", "third@gmail.com");
                    assertThat(primary.getJavaMailProperties()).containsEntry("mail.smtp.from", "primary-alias@example.com");
                    assertThat(accounts.get(1).from()).isEqualTo("third@gmail.com");
                });
    }

    @Test void registersAllFiveAccountsInOrder() {
        context.withPropertyValues(
                "mail.accounts.2.username=second@gmail.com", "mail.accounts.2.password=second-pass",
                "mail.accounts.3.username=third@gmail.com", "mail.accounts.3.password=third-pass",
                "mail.accounts.4.username=fourth@gmail.com", "mail.accounts.4.password=fourth-pass",
                "mail.accounts.5.username=fifth@gmail.com", "mail.accounts.5.password=fifth-pass",
                "mail.accounts.5.from=alias@example.com")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(JavaMailSender.class);
                    @SuppressWarnings("unchecked")
                    var accounts = (List<QuotaMailSender.Account>) ReflectionTestUtils.getField(ctx.getBean(QuotaMailSender.class), "accounts");
                    assertThat(accounts).extracting(QuotaMailSender.Account::slot).containsExactly(1, 2, 3, 4, 5);
                    var fifth = (JavaMailSenderImpl) accounts.get(4).sender();
                    assertThat(fifth.getPassword()).isEqualTo("fifth-pass");
                    assertThat(fifth.getJavaMailProperties()).containsEntry("mail.smtp.from", "alias@example.com");
                });
    }

    @Test void fifthAccountWorksWithIntermediateSlotsOmitted() {
        context.withPropertyValues("mail.accounts.5.username=fifth@gmail.com", "mail.accounts.5.password=fifth-pass")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    @SuppressWarnings("unchecked")
                    var accounts = (List<QuotaMailSender.Account>) ReflectionTestUtils.getField(ctx.getBean(QuotaMailSender.class), "accounts");
                    assertThat(accounts).extracting(QuotaMailSender.Account::slot).containsExactly(1, 5);
                });
    }

    @Test void registersAllTenAccountsAndCopiesSettingsToLastSlot() {
        var properties = new java.util.ArrayList<String>();
        for (int slot = 2; slot <= 10; slot++) {
            properties.add("mail.accounts." + slot + ".username=account" + slot + "@gmail.com");
            properties.add("mail.accounts." + slot + ".password=password" + slot);
        }
        properties.add("mail.accounts.10.from=tenth@example.com");
        context.withPropertyValues(properties.toArray(String[]::new)).run(ctx -> {
            assertThat(ctx).hasSingleBean(JavaMailSender.class);
            @SuppressWarnings("unchecked")
            var accounts = (List<QuotaMailSender.Account>) ReflectionTestUtils.getField(ctx.getBean(QuotaMailSender.class), "accounts");
            assertThat(accounts).extracting(QuotaMailSender.Account::slot).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            var tenth = (JavaMailSenderImpl) accounts.get(9).sender();
            assertThat(tenth.getPassword()).isEqualTo("password10");
            assertThat(tenth.getJavaMailProperties()).containsEntry("mail.smtp.from", "tenth@example.com")
                    .containsEntry("mail.smtp.starttls.required", "true");
        });
    }

    @Test void tenthAccountWorksWithIntermediateSlotsOmitted() {
        context.withPropertyValues("mail.accounts.10.username=tenth@gmail.com", "mail.accounts.10.password=test")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    @SuppressWarnings("unchecked")
                    var accounts = (List<QuotaMailSender.Account>) ReflectionTestUtils.getField(ctx.getBean(QuotaMailSender.class), "accounts");
                    assertThat(accounts).extracting(QuotaMailSender.Account::slot).containsExactly(1, 10);
                    assertThat(accounts.get(1).from()).isEqualTo("tenth@gmail.com");
                });
    }

    @Test void rejectsIncompleteDuplicateAndInvalidCooldownConfiguration() {
        for (String[] invalid : List.of(
                new String[]{"mail.accounts.6.username=sixth@gmail.com"},
                new String[]{"mail.accounts.10.password=only-password"},
                new String[]{"mail.accounts.10.username=f.i.r.s.t+alias@gmail.com", "mail.accounts.10.password=test"},
                new String[]{"mail.accounts.4.username=fourth@gmail.com"},
                new String[]{"mail.accounts.5.password=only-password"},
                new String[]{"mail.accounts.5.username=f.i.r.s.t+alias@gmail.com", "mail.accounts.5.password=test"},
                new String[]{"mail.accounts.2.username=second@gmail.com"},
                new String[]{"mail.accounts.3.password=only-password"},
                new String[]{"mail.accounts.2.username=FIRST@gmail.com", "mail.accounts.2.password=test"},
                new String[]{"mail.accounts.2.username=f.i.r.s.t+alias@googlemail.com", "mail.accounts.2.password=test"},
                new String[]{"mail.accounts.2.username=second@gmail.com", "mail.accounts.2.password=test",
                        "mail.accounts.3.username=second@gmail.com", "mail.accounts.3.password=test"},
                new String[]{"mail.quota-cooldown-seconds=0"})) {
            context.withPropertyValues(invalid).run(ctx -> assertThat(ctx).hasFailed());
        }
    }
}
