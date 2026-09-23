package org.syu_likelion.Festa_2026.mail;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Process-local quota cooldown. Each message is attempted at most once per account. */
public final class QuotaMailSender extends JavaMailSenderImpl {
    private static final Logger log = LoggerFactory.getLogger(QuotaMailSender.class);
    record Account(int slot, JavaMailSender sender, String username, String from) { }
    private final List<Account> accounts;
    private final List<AtomicLong> limitedUntil;
    private final long cooldownMillis;
    private final Clock clock;

    QuotaMailSender(List<Account> accounts, Duration cooldown, Clock clock) {
        this.accounts = List.copyOf(accounts);
        this.limitedUntil = accounts.stream().map(account -> new AtomicLong()).toList();
        this.cooldownMillis = cooldown.toMillis();
        if (cooldownMillis <= 0) throw new IllegalArgumentException("Mail quota cooldown must be positive");
        this.clock = clock;
        setDefaultEncoding("UTF-8");
    }

    // All JavaMailSender entry points, including SimpleMailMessage and preparators, reach doSend.
    @Override
    protected void doSend(MimeMessage[] messages, Object[] originalMessages) throws MailException {
        for (MimeMessage message : messages) sendOne(message);
    }

    private void sendOne(MimeMessage original) {
        MailException lastQuota = null;
        for (int i = 0; i < accounts.size(); i++) {
            if (clock.millis() < limitedUntil.get(i).get()) continue;
            Account account = accounts.get(i);
            try {
                // Isolate each attempt; retain multipart bodies, inline resources and recipients.
                MimeMessage message = new MimeMessage(original);
                if (account.from() != null) {
                    String personal = message.getFrom() != null && message.getFrom().length > 0
                            && message.getFrom()[0] instanceof InternetAddress from ? from.getPersonal() : null;
                    message.setFrom(new InternetAddress(account.from(), personal, "UTF-8"));
                }
                message.setSender(new InternetAddress(account.username()));
                account.sender().send(message);
                return;
            } catch (MailException failure) {
                if (!isQuotaExceeded(failure)) throw failure;
                limitedUntil.get(i).accumulateAndGet(clock.millis() + cooldownMillis, Math::max);
                lastQuota = failure;
                log.warn("SMTP account slot {} reached quota; entering cooldown", account.slot());
            } catch (MessagingException | IOException failure) {
                throw new MailPreparationException("Could not prepare SMTP account message", failure);
            }
        }
        if (lastQuota != null) throw lastQuota;
        throw new MailSendException("All configured SMTP accounts are in quota cooldown");
    }

    static boolean isQuotaExceeded(Throwable error) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> pending = new ArrayDeque<>();
        pending.add(error);
        boolean quota = false;
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (current instanceof MailAuthenticationException || current instanceof AuthenticationFailedException
                    || current instanceof IOException) return false;
            if (current instanceof SendFailedException failed) {
                if (failed.getValidSentAddresses() != null && failed.getValidSentAddresses().length > 0) return false;
                if (failed.getInvalidAddresses() != null && failed.getInvalidAddresses().length > 0) return false;
            }
            String message = current.getMessage();
            if (current instanceof MessagingException && message != null) {
                if (message.matches("(?s).*\\b550[- ]5\\.4\\.5\\b.*")) quota = true;
                // A different SMTP rejection in the same failure tree prevents failover.
                else if (message.matches("(?s).*\\b[45]\\d{2}[- ].*")) return false;
            }
            if (current.getCause() != null) pending.add(current.getCause());
            if (current instanceof MessagingException mail && mail.getNextException() != null)
                pending.add(mail.getNextException());
            if (current instanceof MailSendException mail) pending.addAll(mail.getFailedMessages().values());
            Collections.addAll(pending, current.getSuppressed());
        }
        return quota;
    }
}
