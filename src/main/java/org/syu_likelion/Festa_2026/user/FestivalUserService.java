package org.syu_likelion.Festa_2026.user;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.syu_likelion.Festa_2026.error.ApiException;

@Service
public class FestivalUserService {
    private final FestivalUserRepository repository;

    public FestivalUserService(FestivalUserRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Set<FestivalRole> linkAndGetRoles(UUID userUuid) {
        if (userUuid == null) throw new IllegalArgumentException("SSO response did not contain userUuid");
        return repository.findByUserUuid(userUuid).orElseGet(() -> create(userUuid)).getRoles();
    }

    @Transactional(readOnly = true)
    public Set<FestivalRole> getRoles(UUID userUuid) {
        return repository.findByUserUuid(userUuid).map(FestivalUser::getRoles).orElseGet(Set::of);
    }

    @Transactional(readOnly = true)
    public List<UUID> getLinkedUserUuids() { return repository.findAllUserUuids(); }

    @Transactional
    public Set<FestivalRole> updateManagementRole(UUID actorUuid, FestivalRole actorRole,
                                                   UUID targetUuid, FestivalRole requestedRole) {
        if (actorRole != FestivalRole.ADMIN && actorRole != FestivalRole.SUPER_ADMIN)
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_ROLE_MANAGE_FORBIDDEN",
                    "사용자 권한은 ADMIN 이상만 변경할 수 있습니다.");
        if (requestedRole == null || requestedRole == FestivalRole.BOOTH_MANAGER)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MANAGEMENT_ROLE",
                    "관리 권한은 USER, STAFF, ADMIN, SUPER_ADMIN 중에서 선택해 주세요.");
        FestivalUser preview = repository.findByUserUuid(targetUuid).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "FESTIVAL_USER_NOT_FOUND", "축제 연동 사용자를 찾을 수 없습니다."));
        List<FestivalUser> lockedSuperAdmins = preview.getManagementRole() == FestivalRole.SUPER_ADMIN
                && requestedRole != FestivalRole.SUPER_ADMIN
                ? repository.findAllByManagementRoleForUpdate(FestivalRole.SUPER_ADMIN) : List.of();
        FestivalUser target = lockedSuperAdmins.stream().filter(user -> targetUuid.equals(user.getUserUuid()))
                .findFirst().orElseGet(() -> repository.findByUserUuidForUpdate(targetUuid).orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "FESTIVAL_USER_NOT_FOUND", "축제 연동 사용자를 찾을 수 없습니다.")));
        FestivalRole current = target.getManagementRole();
        if (actorRole == FestivalRole.ADMIN) {
            boolean protectedTarget = actorUuid.equals(targetUuid)
                    || current == FestivalRole.ADMIN || current == FestivalRole.SUPER_ADMIN;
            boolean privilegedRequest = requestedRole == FestivalRole.ADMIN || requestedRole == FestivalRole.SUPER_ADMIN;
            if (protectedTarget || privilegedRequest)
                throw new ApiException(HttpStatus.FORBIDDEN, "USER_ROLE_ESCALATION_FORBIDDEN",
                        "ADMIN은 다른 USER 또는 STAFF의 권한만 USER·STAFF 범위에서 변경할 수 있습니다.");
        }
        if (current == FestivalRole.SUPER_ADMIN && requestedRole != FestivalRole.SUPER_ADMIN
                && lockedSuperAdmins.size() <= 1)
                throw new ApiException(HttpStatus.CONFLICT, "LAST_SUPER_ADMIN_REQUIRED",
                        "마지막 SUPER_ADMIN의 권한은 낮출 수 없습니다.");
        target.changeManagementRole(requestedRole);
        return target.getRoles();
    }

    private FestivalUser create(UUID userUuid) {
        try {
            return repository.saveAndFlush(new FestivalUser(userUuid));
        } catch (DataIntegrityViolationException concurrentInsert) {
            return repository.findByUserUuid(userUuid).orElseThrow(() -> concurrentInsert);
        }
    }
}
