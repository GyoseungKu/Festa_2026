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
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2, 3, 4, 5, 100, Integer.MAX_VALUE})
    void designPersistsAndAppearsInEveryUserRead(int designNo) {
        var created = service.createAs(AUTHOR, "축하해!", "컴퓨터공학부", "2024100920", "홍길동", designNo);
        entityManager.flush();
        entityManager.clear();
        assertThat(created.designNo()).isEqualTo(designNo);
        assertThat(service.get(created.id(), READER).designNo()).isEqualTo(designNo);
        assertThat(service.getMine(AUTHOR).message().designNo()).isEqualTo(designNo);
        assertThat(service.list(READER, BirthdayMessageSort.LATEST, 0, 30, null).items())
                .filteredOn(item -> item.id().equals(created.id()))
                .extracting(item -> item.designNo()).containsExactly(designNo);
    }

    @Test
    void omittedDesignDefaultsToOneInServiceAndDatabase() {
        var created = service.createAs(AUTHOR, "축하해!", null, null, null, null);
        assertThat(created.designNo()).isEqualTo(1);
        jdbc.update("insert into birthday_messages(author_uuid,content,public_masked_student_no,public_masked_name,heart_count,created_at) values (?,?,?,?,0,CURRENT_TIMESTAMP)",
                java.nio.ByteBuffer.allocate(16).putLong(READER.getMostSignificantBits()).putLong(READER.getLeastSignificantBits()).array(),
                "기본 디자인", "미등록", "미등록");
        entityManager.clear();
        assertThat(service.getMine(READER).message().designNo()).isEqualTo(1);
    }


    @Test
    void randomPagesAreRepeatableAndCoverEveryMessageWithoutDuplicates() {
        for (int i = 0; i < 12; i++) {
            service.createAs(new UUID(0, i + 1), "축하 " + i, null, null, null, 1);
        }
        var first = service.list(READER, BirthdayMessageSort.RANDOM, 0, 4, null);
        assertThat(first.seed()).isNotNull().isBetween(0L, (1L << 53) - 1);
        assertThat(first.totalElements()).isEqualTo(12);
        assertThat(first.totalPages()).isEqualTo(3);
        var repeated = service.list(READER, BirthdayMessageSort.RANDOM, 0, 4, first.seed());
        assertThat(repeated.items()).isEqualTo(first.items());
        var all = new java.util.ArrayList<Long>();
        for (int page = 0; page < 3; page++) {
            all.addAll(service.list(READER, BirthdayMessageSort.RANDOM, page, 4, first.seed())
                    .items().stream().map(item -> item.id()).toList());
        }
        assertThat(all).hasSize(12).doesNotHaveDuplicates();
        var one = service.list(READER, BirthdayMessageSort.RANDOM, 0, 100, 1L);
        var two = service.list(READER, BirthdayMessageSort.RANDOM, 0, 100, 2L);
        assertThat(one.items()).isNotEqualTo(two.items());
        assertThat(service.list(READER, BirthdayMessageSort.RANDOM, Integer.MAX_VALUE, 100, 1L).items()).isEmpty();
        assertThat(service.list(READER, BirthdayMessageSort.LATEST, 0, 4, 1L).seed()).isNull();
    }

    @Test
    void emptyRandomPageStillReturnsASeed() {
        var result = service.list(READER, null, 0, 30, null);
        assertThat(result.items()).isEmpty();
        assertThat(result.seed()).isNotNull();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void deletePhysicallyRemovesMessageAndHeartsAndAllowsRewrite() {
        var first = service.createAs(AUTHOR, "첫 번째 축하 메시지", "컴퓨터공학부", "2024100920", "홍길동", 1);
        var hearted = service.addHeartAs(first.id(), READER);
        assertThat(hearted.heartCount()).isEqualTo(1);

        service.deleteOwnAs(first.id(), AUTHOR);
        var second = service.createAs(AUTHOR, "다시 작성한 축하 메시지", "컴퓨터공학부", "2024100920", "홍길동", 1);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.heartCount()).isZero();
        assertThat(service.getMine(AUTHOR).message().content()).isEqualTo("다시 작성한 축하 메시지");
    }

    @Test
    void heartPutAndDeleteAreIdempotent() {
        var message = service.createAs(AUTHOR, "생일 축하해", "컴퓨터공학부", "2024100920", "홍길동", 1);

        assertThat(service.addHeartAs(message.id(), READER).heartCount()).isEqualTo(1);
        assertThat(service.addHeartAs(message.id(), READER).heartCount()).isEqualTo(1);
        assertThat(service.removeHeartAs(message.id(), READER).heartCount()).isZero();
        assertThat(service.removeHeartAs(message.id(), READER).heartCount()).isZero();
    }
}
