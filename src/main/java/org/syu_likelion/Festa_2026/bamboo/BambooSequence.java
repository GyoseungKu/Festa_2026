package org.syu_likelion.Festa_2026.bamboo;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;
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
        counter.set(messages.findMaxSeq());
    }

    /**
     * 새 커서를 발급하고 그 값으로 쓰기 작업을 수행한 뒤 임계 구역 안에서 커밋한다.
     */
    public <T> T writeInOrder(LongFunction<T> work) {
        synchronized (writeLock) {
            return transactions.execute(status -> work.apply(counter.incrementAndGet()));
        }
    }

    /** 현재까지 발급된 마지막 커서. 클라이언트 최초 진입 시 시작점으로 쓴다. */
    public long current() {
        return counter.get();
    }
}
