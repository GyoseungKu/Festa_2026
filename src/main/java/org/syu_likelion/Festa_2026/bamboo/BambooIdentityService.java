package org.syu_likelion.Festa_2026.bamboo;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.syu_likelion.Festa_2026.qr.QrService;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@Service
public class BambooIdentityService {
    private static final Logger log = LoggerFactory.getLogger(BambooIdentityService.class);
    private final QrService users;
    private final BambooNicknameRepository nicknames;
    private final Clock clock;

    public BambooIdentityService(QrService users, BambooNicknameRepository nicknames, Clock clock) {
        this.users = users;
        this.nicknames = nicknames;
        this.clock = clock;
    }

    public SearchResult search(FestivalRole role, UUID actor, String query, int page) {
        BambooAdminService.requireRole(role == null ? Set.of() : Set.of(role), FestivalRole.SUPER_ADMIN);
        try {
            var result = users.searchAs(role, query, page, 20);
            var ids = result.items().stream().map(item -> item.userUuid()).toList();
            var byId = nicknames.findAllById(ids).stream()
                    .collect(Collectors.toMap(BambooNickname::getUserUuid, Function.identity()));
            Instant now = clock.instant();
            var items = result.items().stream().map(user -> {
                var nickname = byId.get(user.userUuid());
                Instant until = nickname == null ? null : nickname.getMutedUntil();
                return new User(user.userUuid(), user.name(), user.studentNo(), user.department(),
                        user.phone(), user.email(), user.loginId(), nickname == null ? null : nickname.getNickname(),
                        until, until != null && until.isAfter(now));
            }).toList();
            log.info("bamboo identity search actorUuid={} actorRole={} targetUuids={} success=true",
                    actor, role, ids);
            return new SearchResult(items, result.page(), result.totalPages(), result.totalElements());
        } catch (RuntimeException failure) {
            log.info("bamboo identity search actorUuid={} actorRole={} success=false", actor, role);
            throw failure;
        }
    }

    public record User(UUID userUuid, String name, String studentNo, String department, String phone,
                       String email, String loginId, String nickname, Instant mutedUntil, boolean muted) { }
    public record SearchResult(List<User> items, int page, int totalPages, long totalElements) { }
}
