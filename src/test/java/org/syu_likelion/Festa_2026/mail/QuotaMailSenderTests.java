package org.syu_likelion.Festa_2026.mail;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.net.SocketTimeoutException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mail.*;
import org.springframework.mail.javamail.*;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuotaMailSenderTests {
    private final JavaMailSender first = mock(JavaMailSender.class);
    private final JavaMailSender second = mock(JavaMailSender.class);
    private final JavaMailSender third = mock(JavaMailSender.class);
    private final MutableClock clock = new MutableClock();
    private final QuotaMailSender sender = new QuotaMailSender(List.of(
            new QuotaMailSender.Account(1, first, "first@gmail.com", null),
            new QuotaMailSender.Account(2, second, "second@gmail.com", "second@gmail.com"),
            new QuotaMailSender.Account(3, third, "third@gmail.com", "alias@example.com")),
            Duration.ofDays(1), clock);

    @Test void quotaFailoverReachesFifthAndSkipsAllFourLimitedAccounts() throws Exception {
        var fourth = mock(JavaMailSender.class);
        var fifth = mock(JavaMailSender.class);
        var fiveAccounts = new QuotaMailSender(List.of(
                new QuotaMailSender.Account(1, first, "first@gmail.com", null),
                new QuotaMailSender.Account(2, second, "second@gmail.com", "second@gmail.com"),
                new QuotaMailSender.Account(3, third, "third@gmail.com", "third@gmail.com"),
                new QuotaMailSender.Account(4, fourth, "fourth@gmail.com", "fourth@gmail.com"),
                new QuotaMailSender.Account(5, fifth, "fifth@gmail.com", "fifth@gmail.com")), Duration.ofDays(1), clock);
        for (var limited : List.of(first, second, third, fourth)) doThrow(quota()).when(limited).send(any(MimeMessage.class));
        fiveAccounts.send(message());
        fiveAccounts.send(message());
        var order = inOrder(first, second, third, fourth, fifth);
        order.verify(first).send(any(MimeMessage.class));
        order.verify(second).send(any(MimeMessage.class));
        order.verify(third).send(any(MimeMessage.class));
        order.verify(fourth).send(any(MimeMessage.class));
        order.verify(fifth, times(2)).send(any(MimeMessage.class));
        order.verifyNoMoreInteractions();
    }

    @Test void normalDeliveryUsesOnlyPrimary() throws Exception {
        sender.send(message());
        verify(first).send(any(MimeMessage.class));
        verifyNoInteractions(second, third);
    }

    @Test void quotaFailsOverInOrderAndSkipsLimitedAccounts() throws Exception {
        doThrow(quota()).when(first).send(any(MimeMessage.class));
        doThrow(quota()).when(second).send(any(MimeMessage.class));
        MimeMessage original = message();
        sender.send(original);
        sender.send(original);
        var order = inOrder(first, second, third);
        order.verify(first).send(any(MimeMessage.class));
        order.verify(second).send(any(MimeMessage.class));
        order.verify(third, times(2)).send(any(MimeMessage.class));
        verify(first, times(1)).send(any(MimeMessage.class));
        verify(second, times(1)).send(any(MimeMessage.class));
        var captured = ArgumentCaptor.forClass(MimeMessage.class);
        verify(third, times(2)).send(captured.capture());
        MimeMessage sent = captured.getValue();
        assertThat(((InternetAddress) sent.getFrom()[0]).getAddress()).isEqualTo("alias@example.com");
        assertThat(((InternetAddress) sent.getFrom()[0]).getPersonal()).isEqualTo("축제");
        assertThat(((InternetAddress) sent.getSender()).getAddress()).isEqualTo("third@gmail.com");
        assertThat(sent.getSubject()).isEqualTo("subject");
        assertThat(sent.getAllRecipients()).hasSize(2);
        assertThat(((MimeMultipart) sent.getContent()).getCount()).isEqualTo(2);
        assertThat(((InternetAddress) original.getFrom()[0]).getAddress()).isEqualTo("original@example.com");
    }

    @Test void allQuotaExhaustedFailsAndDoesNotRetryUntilCooldownEnds() throws Exception {
        for (JavaMailSender account : List.of(first, second, third))
            doThrow(quota()).when(account).send(any(MimeMessage.class));
        MimeMessage mail = message();
        assertThatThrownBy(() -> sender.send(mail)).isInstanceOf(MailSendException.class);
        assertThatThrownBy(() -> sender.send(mail)).hasMessageContaining("cooldown");
        for (JavaMailSender account : List.of(first, second, third))
            verify(account, times(1)).send(any(MimeMessage.class));
        clock.instant = clock.instant.plus(Duration.ofDays(1));
        doNothing().when(first).send(any(MimeMessage.class));
        sender.send(mail);
        verify(first, times(2)).send(any(MimeMessage.class));
        verify(third, times(1)).send(any(MimeMessage.class));
    }

    @Test void primaryAloneAlsoEntersCooldown() throws Exception {
        var only = new QuotaMailSender(List.of(new QuotaMailSender.Account(1, first, "first@gmail.com", null)),
                Duration.ofDays(1), clock);
        doThrow(quota()).when(first).send(any(MimeMessage.class));
        MimeMessage mail = message();
        assertThatThrownBy(() -> only.send(mail)).isInstanceOf(MailSendException.class);
        assertThatThrownBy(() -> only.send(mail)).hasMessageContaining("cooldown");
        verify(first, times(1)).send(any(MimeMessage.class));
    }

    @Test void authenticationTimeoutRecipientAndPartialDeliveryNeverFailOver() throws Exception {
        Address[] recipient = {new InternetAddress("recipient@example.com")};
        List<MailException> failures = List.of(
                new MailAuthenticationException("535 authentication failed"),
                new MailSendException("timeout", new SocketTimeoutException()),
                new MailSendException("recipient", new MessagingException("550 5.1.1 unknown recipient")),
                new MailSendException("partial", new SendFailedException("550 5.4.5 quota", null,
                        recipient, null, null)),
                new MailSendException("invalid", new SendFailedException("550 5.4.5 quota", null,
                        null, null, recipient)));
        for (MailException failure : failures) {
            doThrow(failure).when(first).send(any(MimeMessage.class));
            MimeMessage mail = message();
            assertThatThrownBy(() -> sender.send(mail)).isSameAs(failure);
        }
        verifyNoInteractions(second, third);
    }

    @Test void nestedQuotaDoesNotOverrideAmbiguousFailureAndCyclesTerminate() {
        MailSendException mixed = quota();
        mixed.addSuppressed(new SocketTimeoutException());
        assertThat(QuotaMailSender.isQuotaExceeded(mixed)).isFalse();
        MessagingException cycle = new MessagingException("550 5.4.5 quota");
        cycle.addSuppressed(cycleException(cycle));
        assertThat(QuotaMailSender.isQuotaExceeded(cycle)).isTrue();
        assertThat(QuotaMailSender.isQuotaExceeded(new MailSendException("550 5.4.5 arbitrary text"))).isFalse();
    }

    private RuntimeException cycleException(Throwable cause) { return new RuntimeException(cause); }

    private MailSendException quota() {
        MessagingException nested = new MessagingException("SMTP rejected");
        nested.setNextException(new MessagingException("550-5.4.5 Daily user sending limit exceeded"));
        return new MailSendException(Map.of(new Object(), nested));
    }

    private MimeMessage message() throws Exception {
        MimeMessage mail = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mail, true, "UTF-8");
        helper.setFrom("original@example.com", "축제");
        helper.setTo("recipient@example.com");
        helper.setBcc("bcc@example.com");
        helper.setSubject("subject");
        helper.setText("plain", "<b>html</b>");
        helper.addAttachment("note.txt", new org.springframework.core.io.ByteArrayResource(new byte[]{1, 2}));
        return mail;
    }

    private static class MutableClock extends Clock {
        Instant instant = Instant.parse("2026-09-23T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return instant; }
    }
}
