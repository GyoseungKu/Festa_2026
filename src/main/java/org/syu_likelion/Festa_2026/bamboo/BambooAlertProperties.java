package org.syu_likelion.Festa_2026.bamboo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 신고 누적 알림 설정.
 *
 * <p>{@link BambooProperties}와 분리한 이유는 이쪽만 메일 인프라에 의존하고,
 * 메일이 구성되지 않은 환경에서도 채팅 자체는 그대로 동작해야 하기 때문이다.
 */
@ConfigurationProperties("bamboo.alert")
public record BambooAlertProperties(
        boolean enabled,
        int threshold,
        String from,
        String serviceUrl,
        int maxPerMinute,
        int previewLength) {

    public BambooAlertProperties {
        if (threshold <= 0) threshold = 5;
        if (serviceUrl == null || serviceUrl.isBlank()) serviceUrl = "https://festa.syu-likelion.org";
        if (maxPerMinute <= 0) maxPerMinute = 10;
        if (previewLength <= 0) previewLength = 50;
    }

    boolean deliverable() {
        return enabled && from != null && !from.isBlank();
    }
}
