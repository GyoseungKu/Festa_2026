package org.syu_likelion.Festa_2026.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.performance.PerformanceDtos.PerformanceMutationRequest;

@SpringBootTest
@Transactional
class PerformanceDescriptionPersistenceTests {
    @Autowired PerformanceService service;
    @Autowired FestivalPerformanceRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void fullLengthKoreanDescriptionSurvivesCreateAndReload() {
        String description = "가".repeat(5000);
        var created = service.createAs(UUID.randomUUID(), request(description), List.of(), List.of());
        entityManager.flush();
        entityManager.clear();
        assertThat(repository.findById(created.id()).orElseThrow().getDescription()).isEqualTo(description);
    }

    @Test
    void descriptionOverLimitIsRejectedBeforeInsert() {
        long before = repository.count();
        assertThatThrownBy(() -> service.createAs(UUID.randomUUID(), request("가".repeat(5001)), List.of(), List.of()))
                .isInstanceOf(ApiException.class);
        assertThat(repository.count()).isEqualTo(before);
    }

    private PerformanceMutationRequest request(String description) {
        Instant now = Instant.now();
        return new PerformanceMutationRequest(PerformanceCategory.CLUB, "공연팀", List.of("학생"),
                now.plusSeconds(3600), now.plusSeconds(7200), description,
                List.of(), List.of(), List.of(), now);
    }
}
