package org.syu_likelion.Festa_2026.poll;

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
import org.syu_likelion.Festa_2026.performance.R2Properties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class R2PollQuestionMediaStorage implements PollQuestionMediaStorage {
    private static final Logger log = LoggerFactory.getLogger(R2PollQuestionMediaStorage.class);
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/webm", "video/quicktime");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp",
            "video/mp4", "mp4", "video/webm", "webm", "video/quicktime", "mov");
    private final R2Properties properties;
    private final S3Client client;

    public R2PollQuestionMediaStorage(R2Properties properties) {
        this.properties = properties;
        client = S3Client.builder().endpointOverride(properties.endpoint()).region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .forcePathStyle(true).build();
    }

    @Override public StoredMedia store(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new ApiException(HttpStatus.BAD_REQUEST, "POLL_QUESTION_MEDIA_REQUIRED", "질문 미디어 파일이 비어 있습니다.");
        String type = file.getContentType();
        PollQuestionMediaKind kind = IMAGE_TYPES.contains(type) ? PollQuestionMediaKind.IMAGE
                : VIDEO_TYPES.contains(type) ? PollQuestionMediaKind.VIDEO : null;
        if (kind == null) throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_POLL_QUESTION_MEDIA_TYPE",
                "질문 미디어는 JPG, PNG, WebP, MP4, WebM, MOV만 업로드할 수 있습니다.");
        long max = kind == PollQuestionMediaKind.IMAGE
                ? properties.imageMaxSize().toBytes() : properties.videoMaxSize().toBytes();
        if (file.getSize() > max) throw new ApiException(HttpStatus.BAD_REQUEST, "POLL_QUESTION_MEDIA_TOO_LARGE",
                kind == PollQuestionMediaKind.IMAGE ? "이미지는 파일당 최대 10MB입니다." : "동영상은 파일당 최대 200MB입니다.");
        String folder = kind == PollQuestionMediaKind.IMAGE ? "images" : "videos";
        String key = properties.pollPrefix() + "/questions/" + folder + "/" + UUID.randomUUID()
                + "." + EXTENSIONS.get(type);
        try {
            client.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key)
                            .contentType(type).contentLength(file.getSize()).build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            String original = StringUtils.cleanPath(file.getOriginalFilename() == null
                    ? "upload" : file.getOriginalFilename());
            return new StoredMedia(kind, properties.baseUrl() + "/" + key, key, original);
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "POLL_QUESTION_MEDIA_UPLOAD_FAILED",
                    "질문 미디어를 저장하지 못했습니다.");
        }
    }

    @Override public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return;
        try { client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(storageKey).build()); }
        catch (RuntimeException exception) { log.warn("Poll question media cleanup failed success=false"); }
    }
    @PreDestroy void close() { client.close(); }
}
