package org.syu_likelion.Festa_2026.admin;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.syu_likelion.Festa_2026.user.FestivalRole;

/** One catalog drives dashboard cards, their count, and sidebar navigation. */
@Component("adminFeatures")
public class AdminFeatures {
    private static final List<Feature> FEATURES = List.of(
            new Feature("/admin/wristbands", "무대 입장 팔찌 지급", "입장 팔찌 지급", "학생 인증·납부 여부를 확인하고 팔찌를 지급합니다. ADMIN 이상은 지급 철회와 이력을 관리합니다.", "STAFF 이상", "feature-icon", "", "◎", Access.STAFF),
            new Feature("/admin/student-fees", "학생회비 납부자 관리", "학생회비 납부자", "납부자 학번 등록·검색·삭제", "SUPER_ADMIN", "feature-icon", "", "₩", Access.SUPER_ADMIN),
            new Feature("/admin/sponsors", "협찬사 관리", "협찬사 관리", "협찬사 이름·소개·사진을 등록하고 부스를 연결합니다.", "ADMIN 이상", "feature-icon", "", "★", Access.ADMIN),
            new Feature("/admin/school-verifications", "학생 인증 승인", "학생인증 승인", "이름 또는 학번이 다른 학교 인증 요청을 비교하고 승인하거나 삭제합니다.", "SUPER_ADMIN", "feature-icon feature-icon-verification", "M12 2 4 5v6c0 5.2 3.4 9.8 8 11 4.6-1.2 8-5.8 8-11V5l-8-3Zm0 3 5 1.9V11c0 3.6-2.1 6.9-5 8-2.9-1.1-5-4.4-5-8V6.9L12 5Zm-1 4v3H8l4 4 4-4h-3V9h-2Z", "", Access.SUPER_ADMIN),
            new Feature("/admin/qr", "사용자 및 권한 관리", "사용자 및 권한 관리", "사용자를 검색하거나 QR로 조회하고, ADMIN 이상은 관리 권한을 변경합니다.", "운영 중", "feature-icon", "M3 3h7v7H3V3Zm2 2v3h3V5H5Zm9-2h7v7h-7V3Zm2 2v3h3V5h-3ZM3 14h7v7H3v-7Zm2 2v3h3v-3H5Zm9-2h3v3h-3v-3Zm4 0h3v3h-3v-3Zm-4 4h3v3h-3v-3Zm4 0h3v3h-3v-3Z", "", Access.OPERATORS),
            new Feature("/admin/system", "실시간 시스템 모니터링", "시스템 현황", "접속 규모, 요청 지연, JVM·CPU·디스크, DB와 로그 큐 상태를 확인합니다.", "SUPER_ADMIN", "feature-icon feature-icon-monitor", "M3 4h18v13H3V4Zm2 2v9h14V6H5Zm3 5 2-2 2 2 4-4 2 2-6 6-2-2-2 2-2-2 2-2Zm1 8h6v2H9v-2Z", "", Access.SUPER_ADMIN),
            new Feature("/admin/timetable", "타임테이블 관리", "타임테이블", "일정 시간, 공연팀 연결과 일정별 공개 시각을 관리합니다.", "ADMIN 이상", "feature-icon", "", "◷", Access.ADMIN),
            new Feature("/admin/performances", "공연팀 관리", "공연 관리", "연예인·동아리·개인 공연팀과 공개 일정, 미디어를 등록하고 관리합니다.", "운영 중", "feature-icon feature-icon-stage", "M4 4h16v10H4V4Zm2 2v6h12V6H6Zm-3 9h18v2H3v-2Zm4 3h10l2 3H5l2-3Z", "", Access.ADMIN),
            new Feature("/admin/booths", "부스 지도 관리", "부스 지도 관리", "부스 좌표, 운영 정보, 담당자와 이미지·동영상을 등록하고 관리합니다.", "ADMIN 이상", "feature-icon feature-icon-stage", "M12 2a7 7 0 0 0-7 7c0 5.2 7 13 7 13s7-7.8 7-13a7 7 0 0 0-7-7Zm0 4a3 3 0 1 1 0 6 3 3 0 0 1 0-6Z", "", Access.ADMIN),
            new Feature("/admin/polls", "투표 관리", "투표·응답 폼", "투표·응답 폼을 만들고 실시간 집계와 제출 내역을 확인합니다.", "ADMIN 이상", "feature-icon feature-icon-poll", "M5 3h14v18H5V3Zm2 2v14h10V5H7Zm2 2h6v2H9V7Zm0 4h6v2H9v-2Zm0 4h4v2H9v-2Z", "", Access.ADMIN),
            new Feature("/admin/stamps", "스탬프 지급 관리", "스탬프 지급 관리", "사용자 QR을 확인해 담당 부스의 스탬프를 지급하거나 회수합니다.", "부스 담당자 · ADMIN", "feature-icon feature-icon-stage", "M7 3h10v4a5 5 0 0 1-1.7 3.8V14H19v7H5v-7h3.7v-3.2A5 5 0 0 1 7 7V3Zm2 2v2a3 3 0 0 0 6 0V5H9Zm1.7 7.1V16H7v3h10v-3h-3.7v-3.9a5.3 5.3 0 0 1-2.6 0Z", "", Access.STAMPS),
            new Feature("/admin/bamboo", "대나무숲 운영", "오픈채팅 관리", "신고된 메시지를 가리거나 삭제하고, 작성자 차단과 킬스위치를 관리합니다.", "STAFF 이상", "feature-icon feature-icon-lost", "M4 4h16v12H7l-3 3V4Zm3 4h10v2H7V8Zm0 4h7v2H7v-2Z", "", Access.STAFF),
            new Feature("/admin/notices", "일반 공지 관리", "일반 공지 관리", "공지 작성, 사진·영상·문서 첨부와 상단 고정을 관리합니다.", "STAFF 이상", "feature-icon", "M4 5h16v14H4V5Zm2 2v10h12V7H6Zm2 2h8v2H8V9Zm0 4h5v2H8v-2Z", "", Access.STAFF),
            new Feature("/admin/lost-items", "분실물 공지 관리", "분실물 관리", "분실물 공지를 작성하고 반환 상태, 조회수와 상단 고정을 관리합니다.", "STAFF 이상", "feature-icon feature-icon-lost", "M4 5h16v14H4V5Zm2 2v10h12V7H6Zm2 2h8v2H8V9Zm0 4h5v2H8v-2Z", "", Access.STAFF),
            new Feature("/admin/birthday-messages", "생일축하 쪽지 관리", "생일축하 쪽지", "수야·수호 생일축하 쪽지와 작성자, 하트를 누른 사용자를 확인하고 관리합니다.", "STAFF 이상", "feature-icon feature-icon-birthday", "", "♥", Access.STAFF));

    public List<Feature> available(Set<FestivalRole> roles, FestivalRole primaryRole) {
        Set<FestivalRole> effective = roles != null ? roles
                : primaryRole == null ? Set.of() : Set.of(primaryRole);
        return FEATURES.stream().filter(feature -> feature.access().allows(effective)).toList();
    }

    public record Feature(String path, String title, String navigationTitle, String description,
                          String status, String iconClass, String iconPath, String iconSymbol, Access access) { }

    public enum Access {
        OPERATORS, ADMIN, STAFF, STAMPS, SUPER_ADMIN;

        boolean allows(Set<FestivalRole> roles) {
            boolean superAdmin = roles.contains(FestivalRole.SUPER_ADMIN);
            boolean admin = superAdmin || roles.contains(FestivalRole.ADMIN);
            boolean staff = admin || roles.contains(FestivalRole.STAFF);
            boolean booth = roles.contains(FestivalRole.BOOTH_MANAGER);
            return switch (this) {
                case OPERATORS -> staff || booth;
                case ADMIN -> admin;
                case STAFF -> staff;
                case STAMPS -> admin || booth;
                case SUPER_ADMIN -> superAdmin;
            };
        }
    }
}
