package org.syu_likelion.Festa_2026.timetable;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.performance.FestivalPerformance;
import org.syu_likelion.Festa_2026.performance.FestivalPerformanceRepository;
import org.syu_likelion.Festa_2026.timetable.TimetableDtos.*;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

@Service
@Transactional(readOnly = true)
public class TimetableService {
    private final FestivalScheduleRepository repository;
    private final FestivalPerformanceRepository performances;
    private final UserService users;
    private final Clock clock;

    public TimetableService(FestivalScheduleRepository repository, FestivalPerformanceRepository performances,
                            UserService users, Clock clock) {
        this.repository = repository;
        this.performances = performances;
        this.users = users;
        this.clock = clock;
    }

    public List<ScheduleResponse> list() {
        Instant now = clock.instant();
        return repository.findAllByOrderByStartsAtAscIdAsc().stream()
                .map(item -> response(item, false, now)).toList();
    }

    public ScheduleResponse detail(Long id) {
        return response(find(id), false, clock.instant());
    }

    public AuthorizedResult<List<ScheduleResponse>> listAdmin(String access, String refresh) {
        var auth = manager(access, refresh);
        return rotated(auth, listAll());
    }

    @Transactional
    public AuthorizedResult<ScheduleResponse> create(String access, String refresh, MutationRequest request) {
        var auth = manager(access, refresh);
        return rotated(auth, createAs(auth.body().userUuid(), request));
    }

    @Transactional
    public AuthorizedResult<ScheduleResponse> update(Long id, String access, String refresh, MutationRequest request) {
        var auth = manager(access, refresh);
        return rotated(auth, updateAs(id, auth.body().userUuid(), request));
    }

    @Transactional
    public AuthorizedResult<Void> delete(Long id, String access, String refresh) {
        var auth = manager(access, refresh);
        deleteAs(id);
        return rotated(auth, null);
    }

    // Admin web callers authenticate and check ADMIN before calling these methods.
    public List<ScheduleResponse> listAll() {
        Instant now = clock.instant();
        return repository.findAllByOrderByStartsAtAscIdAsc().stream()
                .map(item -> response(item, true, now)).toList();
    }

    public ScheduleResponse getAdmin(Long id) { return response(find(id), true, clock.instant()); }

    @Transactional
    public ScheduleResponse createAs(UUID actor, MutationRequest request) {
        validate(request);
        var item = new FestivalSchedule(request.title().trim(), request.startsAt(), request.endsAt(),
                request.publishedAt(), linkedPerformance(request.performanceId()), actor);
        return response(repository.saveAndFlush(item), true, clock.instant());
    }

    @Transactional
    public ScheduleResponse updateAs(Long id, UUID actor, MutationRequest request) {
        validate(request);
        var item = find(id);
        item.update(request.title().trim(), request.startsAt(), request.endsAt(), request.publishedAt(),
                linkedPerformance(request.performanceId()), actor);
        return response(repository.saveAndFlush(item), true, clock.instant());
    }

    @Transactional
    public void deleteAs(Long id) { repository.delete(find(id)); }

    private FestivalSchedule find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "SCHEDULE_NOT_FOUND", "일정을 찾을 수 없습니다."));
    }

    private FestivalPerformance linkedPerformance(Long id) {
        if (id == null) return null;
        return performances.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "PERFORMANCE_NOT_FOUND", "연결할 공연팀을 찾을 수 없습니다."));
    }

    private void validate(MutationRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()
                || request.title().trim().length() > 150 || request.startsAt() == null
                || request.endsAt() == null || request.publishedAt() == null
                || (request.performanceId() != null && request.performanceId() <= 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE", "필수 일정 정보를 확인해 주세요.");
        }
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE_TIME",
                    "종료일시는 시작일시보다 늦어야 합니다.");
        }
    }

    private AuthorizedResult<MeResponse> manager(String access, String refresh) {
        var auth = users.getMe(access, refresh);
        var roles = auth.body().festivalRoles();
        if (roles == null || (!roles.contains(FestivalRole.ADMIN) && !roles.contains(FestivalRole.SUPER_ADMIN))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TIMETABLE_MANAGE_FORBIDDEN",
                    "타임테이블은 ADMIN 이상만 관리할 수 있습니다.");
        }
        return auth;
    }

    private ScheduleResponse response(FestivalSchedule item, boolean admin, Instant now) {
        boolean published = !item.getPublishedAt().isAfter(now);
        var team = item.getPerformance();
        boolean showTeam = team != null && (admin || (published && !team.getPublishedAt().isAfter(now)));
        return new ScheduleResponse(item.getId(), admin || published ? item.getTitle() : "TBA",
                item.getStartsAt(), item.getEndsAt(), item.getPublishedAt(), published,
                showTeam ? new PerformanceLink(team.getId(), team.getTeamName()) : null);
    }

    private <T> AuthorizedResult<T> rotated(AuthorizedResult<MeResponse> auth, T body) {
        return new AuthorizedResult<>(body, auth.newAccessToken(), auth.newRefreshToken());
    }
}
