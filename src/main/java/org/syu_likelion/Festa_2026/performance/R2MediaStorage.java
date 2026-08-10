package org.syu_likelion.Festa_2026.performance;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.error.ApiException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class R2MediaStorage implements MediaStorage {
    private static final Logger log = LoggerFactory.getLogger(R2MediaStorage.class);
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/gif", "gif",
            "video/mp4", "mp4", "video/webm", "webm", "video/quicktime", "mov");
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/webm", "video/quicktime");

    private final R2Properties properties;
    private final S3Client client;

    public R2MediaStorage(R2Properties properties) {
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
    public StoredFile store(MultipartFile file, PerformanceMediaKind kind) {
        validate(file, kind);
        String contentType = file.getContentType();
        String folder = kind == PerformanceMediaKind.IMAGE ? "images" : "videos";
        String key = properties.performancePrefix() + "/" + folder + "/"
                + UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();
            client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            String original = StringUtils.cleanPath(file.getOriginalFilename() == null
                    ? "upload" : file.getOriginalFilename());
            return new StoredFile(properties.baseUrl() + "/" + key, key, original);
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_UPLOAD_FAILED",
                    "미디어 파일을 저장하지 못했습니다.");
        }
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return;
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket()).key(storageKey).build());
        } catch (RuntimeException exception) {
            log.warn("Performance media cleanup failed success=false");
        }
    }

    private void validate(MultipartFile file, PerformanceMediaKind kind) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_MEDIA_FILE", "빈 파일은 업로드할 수 없습니다.");
        }
        String contentType = file.getContentType();
        boolean accepted = kind == PerformanceMediaKind.IMAGE
                ? IMAGE_TYPES.contains(contentType) : VIDEO_TYPES.contains(contentType);
        if (!accepted) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MEDIA_TYPE",
                    kind == PerformanceMediaKind.IMAGE
                            ? "JPG, PNG, WebP, GIF 이미지만 업로드할 수 있습니다."
                            : "MP4, WebM, MOV 동영상만 업로드할 수 있습니다.");
        }
        long maxSize = kind == PerformanceMediaKind.IMAGE
                ? properties.imageMaxSize().toBytes() : properties.videoMaxSize().toBytes();
        if (file.getSize() > maxSize) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_FILE_TOO_LARGE",
                    kind == PerformanceMediaKind.IMAGE
                            ? "이미지는 파일당 최대 10MB입니다."
                            : "동영상은 파일당 최대 200MB입니다.");
        }
    }

    @PreDestroy
    void close() {
        client.close();
    }
}
