package org.syu_likelion.Festa_2026.bamboo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooMessageResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooStreamResponse;

/**
 * 이 기능에서 가장 중요한 검증.
 *
 * <p>AUTO_INCREMENT 는 INSERT 시점에 값을 할당하고 커밋은 그 뒤에 일어나므로, 동시 INSERT 에서
 * 커밋 순서가 뒤집히면 커서 기반 조회가 중간 메시지를 영구히 건너뛴다. 여기서는 폴링
 * 클라이언트와 똑같이 커서를 따라 훑으면서 <b>모든 메시지가 정확히 한 번씩</b> 전달되는지 본다.
 *
 * <p>쓰기 경로가 자체 트랜잭션을 열고 닫으므로 {@code @Transactional}로 롤백할 수 없다.
 * 매 테스트 전에 직접 정리한다.
 */
@SpringBootTest(properties = {
        "sso.client-id=test-client", "sso.client-secret=test-secret",
        "spring.datasource.url=jdbc:h2:mem:bamboo-sequence;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        // 이 테스트들은 연속 작성이 목적이므로 빈도 제한을 푼다. 제한 자체는 BambooRateLimiterTests 가 검증한다.
        "bamboo.write-interval=0s",
        "bamboo.writes-per-minute=1000000",
        "bamboo.writes-per-hour=1000000"
})
class BambooSequenceConcurrencyTests {
    private static final UUID AUTHOR = UUID.fromString("123e4567-e89b-12d3-a456-4266141740b0");
    private static final int WRITERS = 600;
    private static final int THREADS = 32;
    private static final int PAGE_SIZE = 7;
    private static final int LEADING_EDGE_PAGE_SIZE = 500;
    private static final int DRAIN_POLLS = 20;

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
        service.claimNickname(AUTHOR, "졸린사자42");
    }

    /**
     * 이 테스트만이 커밋 순서 버그를 실제로 잡는다.
     *
     * <p>쓰기가 모두 끝난 뒤에 훑으면 그 시점에는 전부 커밋되어 있으므로 순서가 뒤집혀도 드러나지
     * 않는다. 커서를 진행시키는 독자가 <b>쓰기와 동시에</b> 돌아야, 아직 커밋되지 않은 번호를
     * 건너뛰고 지나가 그 메시지를 영구히 놓치는 상황이 재현된다.
     */
    @Test
    void aPollingReaderRunningAlongsideWritersReceivesEveryMessageExactlyOnce() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch writersDone = new CountDownLatch(WRITERS);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        for (int index = 0; index < WRITERS; index++) {
            int number = index;
            pool.submit(() -> {
                try {
                    start.await();
                    service.createAs(AUTHOR, "메시지 " + number);
                } catch (Throwable failure) {
                    failures.add(failure);
                } finally {
                    writersDone.countDown();
                }
            });
        }

        List<BambooMessageResponse> seen = new ArrayList<>();
        Thread reader = new Thread(() -> {
            long cursor = 0;
            int drainedPolls = 0;
            while (drainedPolls < DRAIN_POLLS) {
                BambooStreamResponse page = service.stream(AUTHOR, cursor, LEADING_EDGE_PAGE_SIZE);
                if (page.messages().isEmpty()) {
                    if (writersDone.getCount() == 0) drainedPolls++;
                } else {
                    drainedPolls = 0;
                    seen.addAll(page.messages());
                    cursor = page.cursor();
                }
                Thread.onSpinWait();
            }
        }, "bamboo-reader");

        reader.start();
        start.countDown();
        assertThat(writersDone.await(60, TimeUnit.SECONDS)).as("쓰기가 시간 안에 끝나지 않음").isTrue();
        pool.shutdown();
        reader.join(60_000);

        assertThat(reader.isAlive()).as("독자가 끝나지 않음").isFalse();
        assertThat(failures).isEmpty();
        assertThat(messages.count()).isEqualTo(WRITERS);
        assertThat(seen).extracting(BambooMessageResponse::id).doesNotHaveDuplicates();
        assertThat(seen).extracting(BambooMessageResponse::content)
                .containsExactlyInAnyOrderElementsOf(expectedContents());
    }

    @Test
    void everyConcurrentlyWrittenMessageIsStoredAndReachableAfterwards() throws Exception {
        List<Throwable> failures = writeConcurrently();
        assertThat(failures).isEmpty();
        assertThat(messages.count()).isEqualTo(WRITERS);

        List<BambooMessageResponse> delivered = sweepFromStart();

        assertThat(delivered).hasSize(WRITERS);
        assertThat(delivered).extracting(BambooMessageResponse::id).doesNotHaveDuplicates();
        assertThat(delivered).extracting(BambooMessageResponse::seq).doesNotHaveDuplicates();
        assertThat(delivered).extracting(BambooMessageResponse::content)
                .containsExactlyInAnyOrderElementsOf(expectedContents());
    }

    @Test
    void cursorSweepReturnsMessagesInStrictlyIncreasingSequenceOrder() throws Exception {
        assertThat(writeConcurrently()).isEmpty();

        List<BambooMessageResponse> delivered = sweepFromStart();

        assertThat(delivered).isNotEmpty();
        for (int index = 1; index < delivered.size(); index++) {
            assertThat(delivered.get(index).seq())
                    .as("커서 순서가 깨짐: %s번째", index)
                    .isGreaterThan(delivered.get(index - 1).seq());
        }
    }

    @Test
    void aReaderJoiningMidwayNeverSeesAMessageTwiceOrMissesOne() throws Exception {
        assertThat(writeConcurrently()).isEmpty();
        long midpoint = sweepFromStart().get(WRITERS / 2).seq();

        List<BambooMessageResponse> tail = sweepFrom(midpoint);

        assertThat(tail).hasSize(WRITERS - (WRITERS / 2) - 1);
        assertThat(tail).extracting(BambooMessageResponse::id).doesNotHaveDuplicates();
        assertThat(tail).allSatisfy(message -> assertThat(message.seq()).isGreaterThan(midpoint));
    }

    // ------------------------------------------------------------------ 헬퍼

    private List<Throwable> writeConcurrently() throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(WRITERS);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int index = 0; index < WRITERS; index++) {
                int number = index;
                pool.submit(() -> {
                    try {
                        start.await();
                        service.createAs(AUTHOR, "메시지 " + number);
                    } catch (Throwable failure) {
                        failures.add(failure);
                    } finally {
                        finished.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(finished.await(60, TimeUnit.SECONDS)).as("쓰기가 시간 안에 끝나지 않음").isTrue();
        } finally {
            pool.shutdownNow();
        }
        return failures;
    }

    /** 폴링 클라이언트와 동일하게 응답의 cursor 를 그대로 되돌려주며 끝까지 훑는다. */
    private List<BambooMessageResponse> sweepFromStart() {
        return sweepFrom(0L);
    }

    private List<BambooMessageResponse> sweepFrom(long start) {
        List<BambooMessageResponse> collected = new ArrayList<>();
        long cursor = start;
        for (int guard = 0; guard <= WRITERS; guard++) {
            BambooStreamResponse page = service.stream(AUTHOR, cursor, PAGE_SIZE);
            if (page.messages().isEmpty()) return collected;
            collected.addAll(page.messages());
            cursor = page.cursor();
        }
        throw new AssertionError("커서가 끝까지 진행하지 못함");
    }

    private List<String> expectedContents() {
        List<String> expected = new ArrayList<>(WRITERS);
        for (int index = 0; index < WRITERS; index++) expected.add("메시지 " + index);
        return expected;
    }
}
