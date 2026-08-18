package org.syu_likelion.Festa_2026.booth;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import org.syu_likelion.Festa_2026.booth.BoothDtos.BoothMediaOrderRequest;

@SpringBootTest
class BoothMediaPersistenceTests {
    private static final UUID ACTOR = UUID.fromString("123e4567-e89b-12d3-a456-426614174091");

    @Autowired FestivalBoothRepository repository;
    @Autowired BoothService service;
    @Autowired TransactionTemplate transactions;

    @Test
    void integratedImageAndVideoOrderPersistsThroughLockedMutation() {
        AtomicReference<Long> boothId = new AtomicReference<>();
        AtomicReference<List<Long>> mediaIds = new AtomicReference<>();
        transactions.executeWithoutResult(status -> {
            FestivalBooth booth = new FestivalBooth(new BigDecimal("37.6432000"),
                    new BigDecimal("127.1059000"), "정렬 테스트 부스", "테스트 운영팀", "설명",
                    LocalTime.of(10, 0), LocalTime.of(18, 0), false, Set.of(), ACTOR);
            booth.addMedia(new BoothMedia(BoothMediaKind.IMAGE, "https://cdn.test/one.webp", "one", "one.webp"));
            booth.addMedia(new BoothMedia(BoothMediaKind.VIDEO, "https://cdn.test/two.mp4", "two", "two.mp4"));
            FestivalBooth saved = repository.saveAndFlush(booth);
            boothId.set(saved.getId());
            mediaIds.set(saved.getMedia().stream().map(BoothMedia::getId).toList());
        });

        Long imageId = mediaIds.get().get(0);
        Long videoId = mediaIds.get().get(1);
        service.orderMediaAs(boothId.get(), new BoothMediaOrderRequest(List.of(videoId, imageId), videoId));

        transactions.executeWithoutResult(status -> {
            FestivalBooth reloaded = repository.findById(boothId.get()).orElseThrow();
            assertThat(reloaded.getMedia()).extracting(BoothMedia::getId)
                    .containsExactly(videoId, imageId);
            assertThat(reloaded.getMedia()).extracting(BoothMedia::isRepresentative)
                    .containsExactly(true, false);
        });
    }
}
