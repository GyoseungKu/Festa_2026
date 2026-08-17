package org.syu_likelion.Festa_2026.performance;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("r2")
public record R2Properties(
        URI endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        String baseUrl,
        String performancePrefix,
        String lostItemPrefix,
        String boothPrefix,
        DataSize imageMaxSize,
        DataSize videoMaxSize) {

    public R2Properties {
        if (endpoint == null) throw new IllegalArgumentException("r2.endpoint must be configured");
        region = required(region, "r2.region");
        accessKey = required(accessKey, "r2.access-key");
        secretKey = required(secretKey, "r2.secret-key");
        bucket = required(bucket, "r2.bucket");
        baseUrl = required(baseUrl, "r2.base-url").replaceAll("/+$", "");
        performancePrefix = performancePrefix == null || performancePrefix.isBlank()
                ? "festa2026_performance" : performancePrefix.replaceAll("^/+|/+$", "");
        lostItemPrefix = lostItemPrefix == null || lostItemPrefix.isBlank()
                ? "festa2026_lost_items" : lostItemPrefix.replaceAll("^/+|/+$", "");
        boothPrefix = boothPrefix == null || boothPrefix.isBlank()
                ? "festa2026_booths" : boothPrefix.replaceAll("^/+|/+$", "");
        imageMaxSize = imageMaxSize == null ? DataSize.ofMegabytes(10) : imageMaxSize;
        videoMaxSize = videoMaxSize == null ? DataSize.ofMegabytes(200) : videoMaxSize;
    }

    private static String required(String value, String key) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " must be configured");
        return value;
    }
}
