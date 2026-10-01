package org.syu_likelion.Festa_2026.mail;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.mail.host")
@EnableConfigurationProperties(MailProperties.class)
public class MailAccountConfiguration {
    @Bean
    JavaMailSender mailSender(MailProperties primary, Environment environment) {
        List<QuotaMailSender.Account> accounts = new ArrayList<>();
        Set<String> usernames = new HashSet<>();
        for (int slot = 1; slot <= 5; slot++) {
            String prefix = "mail.accounts." + slot + ".";
            String username = slot == 1 ? primary.getUsername() : environment.getProperty(prefix + "username", "");
            String password = slot == 1 ? primary.getPassword() : environment.getProperty(prefix + "password", "");
            boolean hasUser = username != null && !username.isBlank();
            boolean hasPassword = password != null && !password.isBlank();
            if (!hasUser && !hasPassword && slot != 1) continue;
            if (!hasUser || !hasPassword)
                throw new IllegalArgumentException("Both SMTP credentials are required for slot " + slot);
            username = username.trim();
            if (!usernames.add(canonicalAccount(username)))
                throw new IllegalArgumentException("Duplicate SMTP account in slot " + slot);
            String from = slot == 1 ? null : environment.getProperty(prefix + "from", "").trim();
            if (slot != 1 && from.isBlank()) from = username;
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(primary.getHost());
            if (primary.getPort() != null) sender.setPort(primary.getPort());
            sender.setProtocol(primary.getProtocol());
            if (primary.getDefaultEncoding() != null) sender.setDefaultEncoding(primary.getDefaultEncoding().name());
            sender.setUsername(username);
            sender.setPassword(password);
            Properties properties = new Properties();
            properties.putAll(primary.getProperties());
            if (from != null) properties.setProperty("mail.smtp.from", from);
            sender.setJavaMailProperties(properties);
            accounts.add(new QuotaMailSender.Account(slot, sender, username, from));
        }
        long seconds = environment.getProperty("mail.quota-cooldown-seconds", Long.class, 86400L);
        return new QuotaMailSender(accounts, Duration.ofSeconds(seconds), Clock.systemUTC());
    }

    private static String canonicalAccount(String username) {
        String normalized = username.toLowerCase(Locale.ROOT);
        int at = normalized.lastIndexOf('@');
        if (at > 0 && Set.of("gmail.com", "googlemail.com").contains(normalized.substring(at + 1))) {
            String local = normalized.substring(0, at).split("\\+", 2)[0].replace(".", "");
            return local + "@gmail.com";
        }
        return normalized;
    }
}
