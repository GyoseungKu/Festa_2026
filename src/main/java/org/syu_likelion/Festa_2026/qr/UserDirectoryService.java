package org.syu_likelion.Festa_2026.qr;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import org.syu_likelion.Festa_2026.sso.SsoException;
import org.syu_likelion.Festa_2026.user.*;

@Service
public class UserDirectoryService {
    private final FestivalUserRepository users;
    private final SsoInternalProfileClient profiles;
    private final QrService qr;
    private final org.syu_likelion.Festa_2026.wristband.WristbandService wristbands;

    public UserDirectoryService(FestivalUserRepository users, SsoInternalProfileClient profiles, QrService qr,
                                org.syu_likelion.Festa_2026.wristband.WristbandService wristbands) {
        this.users = users;
        this.profiles = profiles;
        this.qr = qr;
        this.wristbands = wristbands;
    }

    public Page<QrDtos.QrUserView> list(FestivalRole viewer, FestivalRole role, Boolean verified,
                                      Boolean paid, int page, int size) {
        if (viewer != FestivalRole.ADMIN && viewer != FestivalRole.SUPER_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_SEARCH_FORBIDDEN", "전체 회원 조회는 ADMIN 이상만 가능합니다.");
        }
        // Filter and paginate locally before requesting any SSO profiles. Never load all linked UUIDs.
        Specification<FestivalUser> filters = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (role != null) predicates.add(role == FestivalRole.BOOTH_MANAGER
                    ? cb.isTrue(root.get("boothManager")) : cb.equal(root.get("managementRole"), role));
            Predicate schoolVerified = cb.and(cb.equal(root.get("schoolVerificationStatus"), SchoolVerificationStatus.VERIFIED),
                    cb.isNotNull(root.get("schoolVerifiedAt")));
            if (verified != null) predicates.add(verified ? schoolVerified : cb.not(schoolVerified));
            Predicate feePaid = cb.and(schoolVerified, cb.isTrue(root.get("studentFeePaid")));
            if (paid != null) predicates.add(paid ? feePaid : cb.not(feePaid));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<FestivalUser> selected = users.findAll(filters, PageRequest.of(
                Math.max(0, Math.min(page, 1_000_000)), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "id")));
        if (selected.isEmpty()) return new org.springframework.data.domain.PageImpl<>(
                java.util.List.of(), selected.getPageable(), selected.getTotalElements());
        Map<UUID, InternalUserProfile> batch = profiles.getProfiles(selected.getContent().stream()
                .map(FestivalUser::getUserUuid).toList()).stream()
                .collect(Collectors.toMap(InternalUserProfile::userUuid, Function.identity(), (first, second) -> first));
        var statuses = wristbands.forPage(selected.getContent());
        return selected.map(user -> {
            InternalUserProfile profile = batch.get(user.getUserUuid());
            if (profile == null) throw new SsoException(502, "SSO profile batch did not contain requested user");
            return qr.toView(viewer, profile, new FestivalUserService.UserFestivalProfile(user.getRoles(),
                    user.getSchoolVerificationStatus(), user.getSchoolVerifiedAt(), user.isStudentFeePaid()), statuses.get(user.getUserUuid()));
        });
    }
}
