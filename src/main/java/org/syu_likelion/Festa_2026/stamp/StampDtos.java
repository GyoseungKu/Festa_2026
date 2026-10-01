package org.syu_likelion.Festa_2026.stamp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class StampDtos {
    private StampDtos() { }
    public record QrStampRequest(@NotBlank @Size(max = 128) String token) { }
    public record StampTargetResponse(UUID userUuid, String name, String studentNo, String department,
                                      boolean stamped, Instant grantedAt) { }
    public record StampItemResponse(Long boothId, String boothName, String operator, Instant grantedAt, org.syu_likelion.Festa_2026.booth.BoothCategory category) { }
    public record MyStampBoardResponse(boolean participated, int stampCount, List<StampItemResponse> stamps) { }
    public record StampPrizeTargetResponse(UUID userUuid, String name, String studentNo, String department,
                                           int stampCount, int requiredStampCount, List<StampItemResponse> stamps,
                                           boolean eligible, boolean prizeGranted, Instant prizeGrantedAt,
                                           UUID prizeGrantedBy) { }
    public record CurrentStampResponse(UUID userUuid, String name, String studentNo,
                                       Instant grantedAt, UUID grantedBy, StampMethod method) { }
    public record StampHistoryResponse(Long id, StampAction action, StampMethod method,
                                       UUID targetUserUuid, String targetName, UUID actorUuid,
                                       String actorName, Instant occurredAt) { }
    public record BoothStampAdminResponse(Long boothId, String boothName,
                                          List<CurrentStampResponse> currentStamps,
                                          List<StampHistoryResponse> history,
                                          int historyPage, int historySize,
                                          long historyTotalElements, int historyTotalPages) {
        public BoothStampAdminResponse(Long boothId, String boothName,
                                       List<CurrentStampResponse> currentStamps,
                                       List<StampHistoryResponse> history) {
            this(boothId, boothName, currentStamps, history, 0,
                    Math.max(1, history.size()), history.size(), history.isEmpty() ? 0 : 1);
        }
    }
}
