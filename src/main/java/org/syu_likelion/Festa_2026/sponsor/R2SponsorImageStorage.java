package org.syu_likelion.Festa_2026.sponsor;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.performance.R2Properties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class R2SponsorImageStorage implements SponsorImageStorage {
    private static final Logger log = LoggerFactory.getLogger(R2SponsorImageStorage.class);
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");
    private final R2Properties properties;
    private final S3Client client;

    public R2SponsorImageStorage(R2Properties properties) {
        this.properties = properties;
        client = S3Client.builder().endpointOverride(properties.endpoint()).region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .forcePathStyle(true).build();
    }

    @Override public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new ApiException(HttpStatus.BAD_REQUEST, "SPONSOR_IMAGE_REQUIRED", "협찬사 이미지 파일이 비어 있습니다.");
        String type = file.getContentType();
        if (type == null || !TYPES.contains(type))
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_SPONSOR_IMAGE_TYPE",
                    "협찬사 이미지는 JPG, PNG, WebP만 업로드할 수 있습니다.");
        if (file.getSize() > properties.imageMaxSize().toBytes())
            throw new ApiException(HttpStatus.BAD_REQUEST, "SPONSOR_IMAGE_TOO_LARGE",
                    "협찬사 이미지는 파일당 최대 10MB입니다.");
        String key = "festa2026_sponsors/" + UUID.randomUUID() + "." + EXTENSIONS.get(type);
        try {
            client.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key)
                            .contentType(type).contentLength(file.getSize()).build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return new StoredImage(properties.baseUrl() + "/" + key, key);
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SPONSOR_IMAGE_UPLOAD_FAILED",
                    "협찬사 이미지를 저장하지 못했습니다.");
        }
    }

    @Override public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return;
        try { client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(storageKey).build()); }
        catch (RuntimeException exception) { log.warn("Sponsor image cleanup failed success=false"); }
    }
    @PreDestroy void close() { client.close(); }
}
