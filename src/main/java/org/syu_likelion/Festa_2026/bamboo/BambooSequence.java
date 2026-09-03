package org.syu_likelion.Festa_2026.bamboo;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 변경 스트림 커서({@code seq}) 발급기.
 *
 * <p>InnoDB의 AUTO_INCREMENT는 INSERT 시점에 값을 할당하고 커밋은 그 뒤에 일어나므로,
 * 동시 INSERT에서 커밋 순서가 뒤집히면 커서 기반 조회가 중간 메시지를 영구히 건너뛴다.
 * 애플리케이션이 순번을 발급하고 <b>같은 임계 구역 안에서 커밋</b>하면 커밋 순서와
 * {@code seq} 순서가 일치하므로 그 구간이 존재하지 않는다.
 *
 * <p>{@code @Transactional} 메서드 안에서 {@code synchronized}를 잡으면 커밋이 임계 구역
 * 밖에서 일어나 보장이 깨진다. 그래서 {@link TransactionTemplate}으로 트랜잭션을
 * 임계 구역 안쪽에 명시적으로 넣는다.
 *
 * <p>롤백된 트랜잭션은 발급받은 번호를 소모해 결번을 남기지만, 조회는 {@code seq > cursor}
 * 범위 스캔이므로 결번은 영향이 없다.
 */
@Component
public class BambooSequence {
    // ponytail: 단일 인스턴스 전제. 단건 INSERT ~1ms이므로 초당 1000건이 천장.
    //           스케일아웃하면 단일 writer 큐 또는 DB 시퀀스로 교체.
    private final Object writeLock = new Object();
    private final AtomicLong counter = new AtomicLong();
    /** DB 커밋까지 끝난 마지막 커서. 진행 중인 트랜잭션 번호는 외부에 노출하지 않는다. */
    private final AtomicLong committed = new AtomicLong();
    private final BambooMessageRepository messages;
    private final TransactionTemplate transactions;

    public BambooSequence(BambooMessageRepository messages, PlatformTransactionManager transactionManager) {
        this.messages = messages;
        // REQUIRES_NEW: 호출부가 트랜잭션 안에 있더라도 임계 구역 안에서 새로 열고 닫는다.
        // 바깥 트랜잭션에 합류하면 커밋이 임계 구역 밖으로 나가 순서 보장이 깨진다.
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @PostConstruct
    void restore() {
        long restored = messages.findMaxSeq();
        counter.set(restored);
        committed.set(restored);
    }

    /**
     * 새 커서를 발급하고 그 값으로 쓰기 작업을 수행한 뒤 임계 구역 안에서 커밋한다.
     */
    public <T> T writeInOrder(LongFunction<T> work) {
        return writeBatchInOrder(next -> work.apply(next.getAsLong()));
    }

    /**
     * 하나의 트랜잭션에서 여러 변경 커서를 발급한다. 관리자 닉네임 변경처럼 여러 메시지가
     * 한꺼번에 바뀌어도 각 메시지가 고유한 커서를 받아 기존 폴링 클라이언트에 전달된다.
     */
    public <T> T writeBatchInOrder(Function<LongSupplier, T> work) {
        synchronized (writeLock) {
            long[] lastIssued = {committed.get()};
            LongSupplier next = () -> {
                long issued = counter.incrementAndGet();
                lastIssued[0] = issued;
                return issued;
            };
            T result = transactions.execute(status -> work.apply(next));
            // TransactionTemplate.execute가 정상 반환한 시점에는 커밋까지 완료되어 있다.
            committed.set(lastIssued[0]);
            return result;
        }
    }

    /**
     * 메시지 목록과 그 목록의 스트림 시작 커서를 같은 쓰기 경계에서 읽는다.
     * 최초 과거 조회 도중 새 쓰기가 끼어 목록에는 없지만 커서에는 포함되는 유실을 막는다.
     */
    public <T> T readSnapshot(java.util.function.Supplier<T> work) {
        synchronized (writeLock) {
            return work.get();
        }
    }

    /** 현재까지 발급된 마지막 커서. 클라이언트 최초 진입 시 시작점으로 쓴다. */
    public long current() {
        return committed.get();
    }
}
