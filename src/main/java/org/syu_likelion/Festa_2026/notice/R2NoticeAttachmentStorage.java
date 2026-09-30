package org.syu_likelion.Festa_2026.notice;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import org.syu_likelion.Festa_2026.storage.UploadMetadataPolicy;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
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
public class R2NoticeAttachmentStorage implements NoticeAttachmentStorage {
    private static final Logger log = LoggerFactory.getLogger(R2NoticeAttachmentStorage.class);
    private final R2Properties properties;
    private final S3Client client;
    private final String prefix;
    private final DataSize documentMaxSize;

    public R2NoticeAttachmentStorage(R2Properties properties,
            @Value("${notice.storage-prefix:festa2026_notices}") String prefix,
            @Value("${notice.document-max-size:20MB}") DataSize documentMaxSize) {
        this.properties = properties;
        this.prefix = prefix.replaceAll("^/+|/+$", "");
        this.documentMaxSize = documentMaxSize;
        this.client = S3Client.builder().endpointOverride(properties.endpoint())
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .forcePathStyle(true).build();
    }

    @Override
    public StoredAttachment store(MultipartFile file) {
        var data = NoticeFilePolicy.validate(file, properties.imageMaxSize().toBytes(),
                properties.videoMaxSize().toBytes(), documentMaxSize.toBytes());
        String key = prefix + "/attachments/" + UUID.randomUUID() + "." + data.extension();
        UploadMetadataPolicy.validateLocation(properties.baseUrl(), key, 512);
        try (var input = file.getInputStream()) {
            var put = PutObjectRequest.builder().bucket(properties.bucket()).key(key)
                    .contentType(data.contentType()).contentLength(data.size());
            if (data.download()) put.contentDisposition(ContentDisposition.attachment()
                    .filename(data.filename(), java.nio.charset.StandardCharsets.UTF_8).build().toString());
            client.putObject(put.build(), RequestBody.fromInputStream(input, data.size()));
            return new StoredAttachment(properties.baseUrl() + "/" + key, key,
                    data.filename(), data.contentType(), data.size());
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_UPLOAD_FAILED", "공지 첨부파일을 저장하지 못했습니다.");
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) return;
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
        } catch (RuntimeException exception) {
            log.warn("Notice attachment cleanup failed success=false");
        }
    }

    @PreDestroy
    void close() { client.close(); }
}
