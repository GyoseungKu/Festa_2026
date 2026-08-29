package org.syu_likelion.Festa_2026.bamboo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooMessageResponse;
import org.syu_likelion.Festa_2026.error.ApiException;

@SpringBootTest(properties = {
        "sso.client-id=test-client", "sso.client-secret=test-secret",
        "spring.datasource.url=jdbc:h2:mem:bamboo-moderation;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "bamboo.write-interval=0s",
        "bamboo.writes-per-minute=1000000",
        "bamboo.writes-per-hour=1000000"
})
class BambooModerationTests {
    private static final UUID AUTHOR = UUID.fromString("123e4567-e89b-12d3-a456-4266141740f0");
    private static final UUID READER = UUID.fromString("123e4567-e89b-12d3-a456-4266141740f1");
    private static final UUID ANOTHER = UUID.fromString("123e4567-e89b-12d3-a456-4266141740f2");
    private static final UUID STAFF = UUID.fromString("123e4567-e89b-12d3-a456-4266141740f3");

    @Autowired BambooService service;
    @Autowired BambooRateLimiter rateLimiter;
    @Autowired BambooMessageRepository messages;
    @Autowired BambooNicknameRepository nicknames;
    @Autowired BambooReportRepository reports;
    @Autowired BambooSettingsRepository settings;

    @BeforeEach
    void reset() {
        rateLimiter.clear();
        reports.deleteAll();
        messages.deleteAll();
        nicknames.deleteAll();
        settings.deleteAll();
        service.claimNickname(AUTHOR, "졸린사자42");
        service.claimNickname(READER, "조용한여우07");
        service.claimNickname(ANOTHER, "씩씩한펭귄33");
    }

    // ------------------------------------------------------------------ 신고

    @Test
    void cannotReportYourOwnMessage() {
        var message = service.createAs(AUTHOR, "내 메시지");

        assertCode(() -> service.reportAs(AUTHOR, message.id(), BambooReportReason.ABUSE),
                "BAMBOO_SELF_REPORT_NOT_ALLOWED");
    }

    @Test
    void cannotReportTheSameMessageTwice() {
        var message = service.createAs(AUTHOR, "신고될 메시지");
        service.reportAs(READER, message.id(), BambooReportReason.ABUSE);

        assertCode(() -> service.reportAs(READER, message.id(), BambooReportReason.SPAM),
                "BAMBOO_ALREADY_REPORTED");
        assertThat(service.reportCountOf(message.id())).isEqualTo(1);
    }

    @Test
    void countsReportsFromDifferentPeople() {
        var message = service.createAs(AUTHOR, "신고될 메시지");

        service.reportAs(READER, message.id(), BambooReportReason.ABUSE);
        service.reportAs(ANOTHER, message.id(), BambooReportReason.PERSONAL_INFO);

        assertThat(service.reportCountOf(message.id())).isEqualTo(2);
        assertThat(messages.findById(message.id()).orElseThrow().getReportCount()).isEqualTo(2);
    }

    @Test
    void cannotReportAMessageThatIsAlreadyGone() {
        var message = service.createAs(AUTHOR, "지워질 메시지");
        service.changeStatus(List.of(message.id()), BambooMessageStatus.DELETED, STAFF);

        assertCode(() -> service.reportAs(READER, message.id(), BambooReportReason.ABUSE),
                "BAMBOO_MESSAGE_NOT_FOUND");
    }

    @Test
    void surfacesReportedMessagesWithTheirReasons() {
        var message = service.createAs(AUTHOR, "신고될 메시지");
        service.reportAs(READER, message.id(), BambooReportReason.ABUSE);
        service.reportAs(ANOTHER, message.id(), BambooReportReason.ABUSE);

        var reported = service.reportedMessages(0, 30);

        assertThat(reported.items()).hasSize(1);
        assertThat(reported.items().getFirst().reportCount()).isEqualTo(2);
        assertThat(reported.items().getFirst().reasons()).containsEntry(BambooReportReason.ABUSE, 2L);
        assertThat(reported.items().getFirst().content()).isEqualTo("신고될 메시지");
    }

    // ------------------------------------------------------------------ 작성 차단

    @Test
    void mutedAuthorCannotWriteButCanStillRead() {
        var message = service.createAs(AUTHOR, "차단 전 메시지");
        service.muteAuthorOf(message.id(), 30);

        assertCode(() -> service.createAs(AUTHOR, "차단 후 메시지"), "BAMBOO_MUTED");
        assertThat(service.history(AUTHOR, null, 50).messages()).hasSize(1);
    }

    @Test
    void muteCanBeLifted() {
        var message = service.createAs(AUTHOR, "차단 전 메시지");
        service.muteAuthorOf(message.id(), 30);
        assertCode(() -> service.createAs(AUTHOR, "차단 중"), "BAMBOO_MUTED");

        service.muteAuthorOf(message.id(), 0);

        assertThat(service.createAs(AUTHOR, "해제 후 메시지").content()).isEqualTo("해제 후 메시지");
    }

    @Test
    void anExpiredMuteNoLongerBlocksWriting() {
        service.createAs(AUTHOR, "차단 전 메시지");
        BambooNickname nickname = nicknames.findById(AUTHOR).orElseThrow();
        nickname.mute(Instant.now().minusSeconds(60));
        nicknames.saveAndFlush(nickname);

        assertThat(service.createAs(AUTHOR, "만료 후 메시지").content()).isEqualTo("만료 후 메시지");
    }

    @Test
    void mutingOnePersonDoesNotAffectAnother() {
        var message = service.createAs(AUTHOR, "차단될 사람의 메시지");
        service.muteAuthorOf(message.id(), 30);

        assertThat(service.createAs(READER, "다른 사람 메시지").content()).isEqualTo("다른 사람 메시지");
    }

    // ------------------------------------------------------------------ 상태 변경

    @Test
    void hidingRemovesTheMessageFromHistoryAndPushesTheChangeDownTheStream() {
        var message = service.createAs(AUTHOR, "가려질 메시지");
        long cursor = service.stream(READER, 0L, 50).cursor();

        int changed = service.changeStatus(List.of(message.id()), BambooMessageStatus.HIDDEN, STAFF);

        assertThat(changed).isEqualTo(1);
        assertThat(service.history(READER, null, 50).messages()).isEmpty();
        var delivered = service.stream(READER, cursor, 50).messages();
        assertThat(delivered).hasSize(1);
        assertThat(delivered.getFirst().status()).isEqualTo(BambooMessageStatus.HIDDEN);
        assertThat(delivered.getFirst().content()).isNull();
    }

    @Test
    void restoringAMessageBringsItBackToHistory() {
        var message = service.createAs(AUTHOR, "되살릴 메시지");
        service.changeStatus(List.of(message.id()), BambooMessageStatus.HIDDEN, STAFF);

        service.changeStatus(List.of(message.id()), BambooMessageStatus.VISIBLE, STAFF);

        assertThat(service.history(READER, null, 50).messages())
                .extracting(BambooMessageResponse::content).containsExactly("되살릴 메시지");
    }

    @Test
    void changingStatusIsIdempotentAndReportsOnlyRealChanges() {
        var first = service.createAs(AUTHOR, "첫 번째");
        var second = service.createAs(AUTHOR, "두 번째");

        assertThat(service.changeStatus(List.of(first.id(), second.id()),
                BambooMessageStatus.DELETED, STAFF)).isEqualTo(2);
        assertThat(service.changeStatus(List.of(first.id(), second.id()),
                BambooMessageStatus.DELETED, STAFF)).isZero();
    }

    @Test
    void deletionIsAlwaysSoftSoTheRecordSurvives() {
        var message = service.createAs(AUTHOR, "증거로 남아야 하는 메시지");

        service.changeStatus(List.of(message.id()), BambooMessageStatus.DELETED, STAFF);

        BambooMessage stored = messages.findById(message.id()).orElseThrow();
        assertThat(stored.getContent()).isEqualTo("증거로 남아야 하는 메시지");
        assertThat(stored.getDeletedBy()).isEqualTo(STAFF);
        assertThat(stored.getDeletedAt()).isNotNull();
    }

    // ------------------------------------------------------------------ 닉네임 강제 변경

    @Test
    void renamingAnAuthorAlsoUpdatesTheirPastMessages() {
        var first = service.createAs(AUTHOR, "첫 번째");
        service.createAs(AUTHOR, "두 번째");

        service.renameAuthorOf(first.id(), "차분한코알라11");

        assertThat(service.history(READER, null, 50).messages())
                .extracting(BambooMessageResponse::anonName)
                .containsOnly("차분한코알라11");
        assertThat(service.nicknameOf(AUTHOR)).isEqualTo("차분한코알라11");
    }

    @Test
    void renamingRejectsANicknameSomeoneElseHolds() {
        var message = service.createAs(AUTHOR, "메시지");

        assertCode(() -> service.renameAuthorOf(message.id(), "조용한여우07"), "BAMBOO_NICKNAME_TAKEN");
    }

    @Test
    void renamingRejectsOperatorImpersonation() {
        var message = service.createAs(AUTHOR, "메시지");

        assertCode(() -> service.renameAuthorOf(message.id(), "총학운영진"), "BAMBOO_NICKNAME_INVALID");
    }

    // ------------------------------------------------------------------ 설정

    @Test
    void killSwitchSurvivesThroughTheSettingsApi() {
        service.updateSettings(false, null, null, false);

        assertCode(() -> service.stream(AUTHOR, 0L, 50), "BAMBOO_CLOSED");

        service.updateSettings(true, null, null, false);
        assertThat(service.stream(AUTHOR, 0L, 50).messages()).isEmpty();
    }

    private void assertCode(ThrowingCallable call, String expectedCode) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo(expectedCode);
    }
}
