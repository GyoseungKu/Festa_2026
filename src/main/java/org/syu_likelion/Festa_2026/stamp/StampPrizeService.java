package org.syu_likelion.Festa_2026.stamp;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.qr.QrService;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import org.syu_likelion.Festa_2026.stamp.StampDtos.StampItemResponse;
import org.syu_likelion.Festa_2026.stamp.StampDtos.StampPrizeTargetResponse;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.FestivalUserRepository;

@Service
public class StampPrizeService {
    public static final int REQUIRED_STAMPS = 6;
    private final StampPrizeRepository prizes;
    private final BoothStampRepository stamps;
    private final FestivalUserRepository users;
    private final QrService qr;
    private final SsoInternalProfileClient profiles;
    private final Clock clock;

    public StampPrizeService(StampPrizeRepository prizes, BoothStampRepository stamps,
                             FestivalUserRepository users, QrService qr,
                             SsoInternalProfileClient profiles, Clock clock) {
        this.prizes = prizes; this.stamps = stamps; this.users = users;
        this.qr = qr; this.profiles = profiles; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StampPrizeTargetResponse lookupQrAs(FestivalRole role, String token) {
        requireAdmin(role);
        UUID target = qr.resolveUserUuid(token);
        if (users.findByUserUuid(target).isEmpty()) throw unlinked();
        InternalUserProfile profile = profiles.getProfile(target);
        List<BoothStamp> board = stamps.findAllByUserUserUuidOrderByGrantedAtAsc(target);
        return response(target, profile, board, prizes.findByTargetUserUuid(target).orElse(null));
    }

    @Transactional
    public StampPrizeTargetResponse grantQrAs(UUID actor, FestivalRole role, String token) {
        requireAdmin(role);
        UUID target = qr.resolveUserUuid(token);
        InternalUserProfile profile = profiles.getProfile(target);
        // Serialize redemption and stamp mutations for the same user.
        users.findByUserUuidForUpdate(target).orElseThrow(StampPrizeService::unlinked);
        if (prizes.findByTargetUserUuidForUpdate(target).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "STAMP_PRIZE_ALREADY_GRANTED", "이미 상품을 지급한 사용자입니다.");
        }
        List<BoothStamp> board = stamps.findAllForUserForUpdate(target);
        if (board.size() < REQUIRED_STAMPS) {
            throw new ApiException(HttpStatus.CONFLICT, "STAMP_PRIZE_NOT_READY", "스탬프를 6개 이상 모아야 상품을 지급할 수 있습니다.");
        }
        StampPrize prize = prizes.saveAndFlush(new StampPrize(target, actor, clock.instant(), board.size()));
        return response(target, profile, board, prize);
    }

    private StampPrizeTargetResponse response(UUID target, InternalUserProfile profile,
                                               List<BoothStamp> board, StampPrize prize) {
        List<StampItemResponse> items = board.stream().map(stamp -> new StampItemResponse(
                stamp.getBooth().getId(), stamp.getBooth().getName(), stamp.getBooth().getOperator(), stamp.getGrantedAt())).toList();
        return new StampPrizeTargetResponse(target, profile.name(), profile.studentNo(), profile.department(),
                items.size(), REQUIRED_STAMPS, items, prize == null && items.size() >= REQUIRED_STAMPS,
                prize != null, prize == null ? null : prize.getGrantedAt(), prize == null ? null : prize.getGrantedBy());
    }

    private void requireAdmin(FestivalRole role) {
        if (role != FestivalRole.ADMIN && role != FestivalRole.SUPER_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "STAMP_PRIZE_FORBIDDEN", "상품 지급은 ADMIN 이상만 처리할 수 있습니다.");
        }
    }
    private static ApiException unlinked() {
        return new ApiException(HttpStatus.BAD_REQUEST, "STAMP_USER_NOT_LINKED", "축제 서비스에 연결되지 않은 사용자입니다.");
    }
}
