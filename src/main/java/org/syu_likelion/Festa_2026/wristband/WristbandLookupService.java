package org.syu_likelion.Festa_2026.wristband;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.qr.QrService;
import org.syu_likelion.Festa_2026.qr.QrDtos.QrUserView;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@Service
public class WristbandLookupService {
    private final QrService qr;
    private final WristbandService wristbands;
    public WristbandLookupService(QrService qr, WristbandService wristbands) {
        this.qr = qr; this.wristbands = wristbands;
    }
    public record Candidate(UUID userUuid, String name, String studentNo, String department,
                            WristbandService.Eligibility eligibility) { }
    public record Search(List<Candidate> items, int page, int totalPages, long totalElements) { }

    public Candidate scan(FestivalRole role, String token) {
        WristbandService.requireStaff(role);
        if (token == null || token.isBlank() || token.length() > 128)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QR_TOKEN", "QR을 다시 스캔해 주세요.");
        // Use UUID only for the operation; return a dedicated view with STAFF masking.
        return candidate(role, qr.scanAs(FestivalRole.ADMIN, token.strip()));
    }
    public Search search(FestivalRole role, String query, int page) {
        WristbandService.requireStaff(role);
        var result = qr.searchAs(FestivalRole.ADMIN, query, Math.max(0, page), 20);
        return new Search(result.items().stream().map(u -> candidate(role, u)).toList(),
                result.page(), result.totalPages(), result.totalElements());
    }
    private Candidate candidate(FestivalRole role, QrUserView user) {
        return new Candidate(user.userUuid(), role == FestivalRole.STAFF ? QrService.maskName(user.name()) : user.name(),
                role == FestivalRole.STAFF ? QrService.maskStudentNo(user.studentNo()) : user.studentNo(),
                user.department(), wristbands.eligibility(role, user.userUuid()));
    }
}
