package org.syu_likelion.Festa_2026.birthday;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "sso.client-id=test-client", "sso.client-secret=test-secret",
        "spring.datasource.url=jdbc:h2:mem:birthday-persistence;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Transactional
class BirthdayMessagePersistenceTests {
    private static final UUID AUTHOR = UUID.fromString("123e4567-e89b-12d3-a456-426614174020");
    private static final UUID READER = UUID.fromString("123e4567-e89b-12d3-a456-426614174021");

    @Autowired BirthdayMessageService service;

    @Test
    void deletePhysicallyRemovesMessageAndHeartsAndAllowsRewrite() {
        var first = service.createAs(AUTHOR, "첫 번째 축하 메시지", "컴퓨터공학부", "2024100920", "홍길동");
        var hearted = service.addHeartAs(first.id(), READER);
        assertThat(hearted.heartCount()).isEqualTo(1);

        service.deleteOwnAs(first.id(), AUTHOR);
        var second = service.createAs(AUTHOR, "다시 작성한 축하 메시지", "컴퓨터공학부", "2024100920", "홍길동");

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.heartCount()).isZero();
        assertThat(service.getMine(AUTHOR).message().content()).isEqualTo("다시 작성한 축하 메시지");
    }

    @Test
    void heartPutAndDeleteAreIdempotent() {
        var message = service.createAs(AUTHOR, "생일 축하해", "컴퓨터공학부", "2024100920", "홍길동");

        assertThat(service.addHeartAs(message.id(), READER).heartCount()).isEqualTo(1);
        assertThat(service.addHeartAs(message.id(), READER).heartCount()).isEqualTo(1);
        assertThat(service.removeHeartAs(message.id(), READER).heartCount()).isZero();
        assertThat(service.removeHeartAs(message.id(), READER).heartCount()).isZero();
    }
}
