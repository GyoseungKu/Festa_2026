package org.syu_likelion.Festa_2026.booth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import org.syu_likelion.Festa_2026.user.FestivalUser;
import org.syu_likelion.Festa_2026.user.FestivalUserRepository;

@Service
public class BoothManagerDirectory {
    private static final int PROFILE_BATCH_SIZE = 100;
    private final FestivalUserRepository users;
    private final SsoInternalProfileClient profiles;
    public BoothManagerDirectory(FestivalUserRepository users, SsoInternalProfileClient profiles) {
        this.users = users; this.profiles = profiles;
    }

    public List<ManagerCandidate> candidates() {
        List<FestivalUser> linked = users.findAll();
        List<UUID> ids = linked.stream().map(FestivalUser::getUserUuid).toList();
        Map<UUID, InternalUserProfile> byId = new HashMap<>();
        for (int start = 0; start < ids.size(); start += PROFILE_BATCH_SIZE) {
            List<UUID> batch = ids.subList(start, Math.min(start + PROFILE_BATCH_SIZE, ids.size()));
            try {
                byId.putAll(profiles.getProfiles(batch).stream().collect(Collectors.toMap(
                        InternalUserProfile::userUuid, Function.identity(), (first, ignored) -> first)));
            } catch (RuntimeException unavailable) {
                // UUID 후보는 유지하여 SSO 프로필 장애 중에도 담당자 지정이 가능하게 합니다.
            }
        }
        List<ManagerCandidate> result = new ArrayList<>();
        for (UUID id : ids) {
            InternalUserProfile profile = byId.get(id);
            result.add(new ManagerCandidate(id, profile == null ? null : profile.name(),
                    profile == null ? null : profile.loginId(), profile == null ? null : profile.studentNo(),
                    profile == null ? null : profile.department()));
        }
        return result.stream().sorted(Comparator.comparing(ManagerCandidate::displayName)).toList();
    }

    public record ManagerCandidate(UUID userUuid, String name, String loginId, String studentNo, String department) {
        public String displayName() {
            if (name != null && !name.isBlank()) return name;
            if (loginId != null && !loginId.isBlank()) return loginId;
            return userUuid.toString();
        }
        public String searchableText() {
            return String.join(" ", displayName(), loginId == null ? "" : loginId,
                    studentNo == null ? "" : studentNo, department == null ? "" : department, userUuid.toString());
        }
    }
}
