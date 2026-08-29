package org.syu_likelion.Festa_2026.bamboo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.syu_likelion.Festa_2026.error.ApiException;

/**
 * 작성 경로에 제한 장치가 실제로 물려 있는지 확인한다.
 * 개별 규칙은 {@link BambooRateLimiterTests}에서 시간을 조작해 검증한다.
 */
@SpringBootTest(properties = {
        "sso.client-id=test-client", "sso.client-secret=test-secret",
        "spring.datasource.url=jdbc:h2:mem:bamboo-write-guard;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "bamboo.blocked-words[0]=금지된표현",
        "bamboo.blocked-words[1]=시발",
        "bamboo.blocked-words[2]=banned"
})
class BambooWriteGuardTests {
    private static final UUID AUTHOR = UUID.fromString("123e4567-e89b-12d3-a456-4266141740e0");

    @Autowired BambooService service;
    @Autowired BambooRateLimiter rateLimiter;
    @Autowired BambooMessageRepository messages;
    @Autowired BambooNicknameRepository nicknames;
    @Autowired BambooSettingsRepository settings;

    @BeforeEach
    void reset() {
        rateLimiter.clear();
        messages.deleteAll();
        nicknames.deleteAll();
        settings.deleteAll();
        nicknames.findById(AUTHOR).ifPresent(nicknames::delete);
        service.claimNickname(AUTHOR, "졸린사자42");
    }

    @Test
    void rateLimitIsWiredIntoTheWritePath() {
        service.createAs(AUTHOR, "첫 메시지");

        assertThatThrownBy(() -> service.createAs(AUTHOR, "둘째 메시지"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_TOO_FAST");
        assertThat(messages.count()).isEqualTo(1);
    }

    @Test
    void blockedWordsAreRejectedBeforeAnythingIsStored() {
        assertThatThrownBy(() -> service.createAs(AUTHOR, "이건 금지된표현 이야"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_CONTENT_BLOCKED");
        assertThat(messages.count()).isZero();
    }

    /**
     * 회귀 방지. 공백까지 지우고 대조하면 "다시 발표"가 "다시발표"가 되어 짧은 금칙어에 걸린다.
     * 정상 문장을 막는 쪽이 회피를 허용하는 쪽보다 훨씬 큰 문제다.
     */
    @Test
    void doesNotBlockOrdinaryTextThatOnlyMatchesAcrossAWordBoundary() {
        assertThat(service.createAs(AUTHOR, "다시 발표할게").content()).isEqualTo("다시 발표할게");
    }

    @Test
    void stillBlocksTheSameWordWhenItAppearsForReal() {
        assertThatThrownBy(() -> service.createAs(AUTHOR, "이거 진짜 시발"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_CONTENT_BLOCKED");
    }

    @Test
    void foldsCaseAndFullWidthFormsWhenMatching() {
        assertThatThrownBy(() -> service.createAs(AUTHOR, "this is BANNED"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_CONTENT_BLOCKED");
    }

    @Test
    void aRejectedWriteDoesNotConsumeTheRateAllowance() {
        assertThatThrownBy(() -> service.createAs(AUTHOR, "금지된표현"))
                .isInstanceOf(ApiException.class);

        assertThat(service.createAs(AUTHOR, "정상 메시지").content()).isEqualTo("정상 메시지");
    }
}
