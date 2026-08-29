package org.syu_likelion.Festa_2026.bamboo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.syu_likelion.Festa_2026.error.ApiException;

/**
 * 쓰기 경로가 REQUIRES_NEW 로 자체 트랜잭션을 열고 닫으므로 테스트에 {@code @Transactional}을
 * 걸어도 롤백되지 않는다. 매 테스트 전에 직접 정리한다.
 */
@SpringBootTest(properties = {
        "sso.client-id=test-client", "sso.client-secret=test-secret",
        "spring.datasource.url=jdbc:h2:mem:bamboo-service;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class BambooServiceTests {
    private static final UUID AUTHOR = UUID.fromString("123e4567-e89b-12d3-a456-4266141740a0");
    private static final UUID OTHER = UUID.fromString("123e4567-e89b-12d3-a456-4266141740a1");
    private static final UUID STAFF = UUID.fromString("123e4567-e89b-12d3-a456-4266141740a2");

    @Autowired BambooService service;
    @Autowired BambooSequence sequence;
    @Autowired BambooMessageRepository messages;
    @Autowired BambooNicknameRepository nicknames;
    @Autowired BambooSettingsRepository settings;

    @BeforeEach
    void reset() {
        messages.deleteAll();
        nicknames.deleteAll();
        settings.deleteAll();
    }

    // ------------------------------------------------------------------ 닉네임

    @Test
    void rejectsNicknameThatOnlyDiffersByHomoglyphs() {
        service.claimNickname(AUTHOR, "bamboo1");

        assertThatThrownBy(() -> service.claimNickname(OTHER, "bambool"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_NICKNAME_TAKEN");
    }

    @Test
    void rejectsSecondClaimBySameUser() {
        service.claimNickname(AUTHOR, "졸린사자42");

        assertThatThrownBy(() -> service.claimNickname(AUTHOR, "조용한여우07"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_NICKNAME_ALREADY_SET");
    }

    @Test
    void rejectsOperatorImpersonation() {
        assertThatThrownBy(() -> service.claimNickname(AUTHOR, "총학운영진"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_NICKNAME_BLOCKED");
    }

    @Test
    void rejectsNicknameWithDisallowedCharacters() {
        assertThatThrownBy(() -> service.claimNickname(AUTHOR, "사자 두마리"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_NICKNAME_INVALID");
    }

    @Test
    void suggestsNicknameThatIsNotYetTaken() {
        String suggested = service.suggestNickname().nickname();

        assertThat(nicknames.existsByNicknameKey(BambooNicknamePolicy.normalize(suggested))).isFalse();
        assertThat(service.claimNickname(AUTHOR, suggested).nickname()).isEqualTo(suggested);
    }

    // ------------------------------------------------------------------ 작성

    @Test
    void writingRequiresNicknameFirst() {
        assertThatThrownBy(() -> service.createAs(AUTHOR, "닉네임 없이 작성"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_NICKNAME_REQUIRED");
    }

    @Test
    void storesNicknameSnapshotOnTheMessage() {
        service.claimNickname(AUTHOR, "졸린사자42");

        var created = service.createAs(AUTHOR, "무대 미쳤다");

        assertThat(created.anonName()).isEqualTo("졸린사자42");
        assertThat(created.content()).isEqualTo("무대 미쳤다");
        assertThat(created.status()).isEqualTo(BambooMessageStatus.VISIBLE);
        assertThat(created.mine()).isTrue();
    }

    // ------------------------------------------------------------------ 조회

    @Test
    void historyReturnsOldestFirstAndHidesRemovedMessages() {
        service.claimNickname(AUTHOR, "졸린사자42");
        var first = service.createAs(AUTHOR, "첫 번째");
        var second = service.createAs(AUTHOR, "두 번째");
        var third = service.createAs(AUTHOR, "세 번째");
        remove(second.id());

        var history = service.history(AUTHOR, null, 50);

        assertThat(history.messages()).extracting("id").containsExactly(first.id(), third.id());
        assertThat(history.messages()).extracting("content").containsExactly("첫 번째", "세 번째");
    }

    @Test
    void removalTravelsDownTheSameCursorAsNewMessages() {
        service.claimNickname(AUTHOR, "졸린사자42");
        var created = service.createAs(AUTHOR, "지워질 메시지");
        long cursorAfterCreate = service.stream(OTHER, 0L, 50).cursor();

        remove(created.id());
        var delivered = service.stream(OTHER, cursorAfterCreate, 50);

        assertThat(delivered.messages()).hasSize(1);
        assertThat(delivered.messages().getFirst().id()).isEqualTo(created.id());
        assertThat(delivered.messages().getFirst().status()).isEqualTo(BambooMessageStatus.DELETED);
        assertThat(delivered.messages().getFirst().content()).isNull();
        assertThat(delivered.cursor()).isGreaterThan(cursorAfterCreate);
    }

    @Test
    void streamWithoutCursorStartsFromNowInsteadOfReplayingEverything() {
        service.claimNickname(AUTHOR, "졸린사자42");
        service.createAs(AUTHOR, "이전 메시지");

        assertThat(service.stream(AUTHOR, null, 50).messages()).isEmpty();
    }

    @Test
    void marksOtherPeopleMessagesAsNotMine() {
        service.claimNickname(AUTHOR, "졸린사자42");
        service.createAs(AUTHOR, "내 메시지");

        var seen = service.history(OTHER, null, 50).messages();

        assertThat(seen).hasSize(1);
        assertThat(seen.getFirst().mine()).isFalse();
    }

    // ------------------------------------------------------------------ 킬스위치

    @Test
    void killSwitchBlocksReadingAndWriting() {
        service.claimNickname(AUTHOR, "졸린사자42");
        service.createAs(AUTHOR, "닫히기 전 메시지");
        close();

        assertThatThrownBy(() -> service.stream(AUTHOR, 0L, 50))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_CLOSED");
        assertThatThrownBy(() -> service.createAs(AUTHOR, "닫힌 뒤 메시지"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_CLOSED");
    }

    @Test
    void roomStaysReadableWhileClosedSoClientsCanExplainWhy() {
        close();

        var room = service.room(AUTHOR);

        assertThat(room.enabled()).isFalse();
        assertThat(room.nickname()).isNull();
    }

    @Test
    void closingTimeMakesTheRoomReadOnly() {
        service.claimNickname(AUTHOR, "졸린사자42");
        BambooSettings current = service.currentSettings();
        current.update(null, null, Instant.now().minusSeconds(60), false, Instant.now());
        settings.saveAndFlush(current);

        assertThatThrownBy(() -> service.createAs(AUTHOR, "종료 후 메시지"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_READ_ONLY");
        assertThat(service.history(AUTHOR, null, 50).messages()).isEmpty();
    }

    // ------------------------------------------------------------------ 헬퍼

    /** 관리자 삭제는 3단계 작업이다. 여기서는 커서 재발급 동작만 직접 흉내낸다. */
    private void remove(Long messageId) {
        sequence.writeInOrder(seq -> {
            BambooMessage message = messages.findById(messageId).orElseThrow();
            message.changeStatus(BambooMessageStatus.DELETED, seq, STAFF, Instant.now());
            return messages.save(message);
        });
    }

    private void close() {
        BambooSettings current = service.currentSettings();
        current.update(false, null, null, true, Instant.now());
        settings.saveAndFlush(current);
    }
}
