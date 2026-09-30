package org.syu_likelion.Festa_2026.lostitem;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import org.syu_likelion.Festa_2026.storage.UploadMetadataPolicy;
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
public class R2LostItemImageStorage implements LostItemImageStorage {
    private static final Logger log = LoggerFactory.getLogger(R2LostItemImageStorage.class);
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");
    private static final Set<String> IMAGE_TYPES = EXTENSIONS.keySet();

    private final R2Properties properties;
    private final S3Client client;

    public R2LostItemImageStorage(R2Properties properties) {
        this.properties = properties;
        this.client = S3Client.builder()
                .endpointOverride(properties.endpoint())
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .forcePathStyle(true)
                .build();
    }

    @Override
    public StoredImage store(MultipartFile file) {
        validate(file);
        String contentType = file.getContentType();
        String key = properties.lostItemPrefix() + "/images/" + UUID.randomUUID()
                + "." + EXTENSIONS.get(contentType);
        UploadMetadataPolicy.validateLocation(properties.baseUrl(), key, 512);
        String original = UploadMetadataPolicy.originalFilename(file.getOriginalFilename());
        try {
            client.putObject(PutObjectRequest.builder()
                            .bucket(properties.bucket()).key(key).contentType(contentType)
                            .contentLength(file.getSize()).build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return new StoredImage(properties.baseUrl() + "/" + key, key, original);
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_UPLOAD_FAILED",
                    "분실물 사진을 저장하지 못했습니다.");
        }
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return;
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket()).key(storageKey).build());
        } catch (RuntimeException exception) {
            log.warn("Lost item image cleanup failed success=false");
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_IMAGE_FILE",
                    "빈 사진 파일은 업로드할 수 없습니다.");
        }
        if (!IMAGE_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMAGE_TYPE",
                    "JPG, PNG, WebP 사진만 업로드할 수 있습니다.");
        }
        if (file.getSize() > properties.imageMaxSize().toBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_FILE_TOO_LARGE",
                    "사진 한 장의 최대 크기는 10MB입니다.");
        }
    }

    @PreDestroy
    void close() {
        client.close();
    }
}
