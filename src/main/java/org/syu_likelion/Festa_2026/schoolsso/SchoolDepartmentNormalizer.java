package org.syu_likelion.Festa_2026.schoolsso;

import java.util.regex.Pattern;

/** 학교가 보내는 세부전공을 회원정보에서 사용하는 학부·학과명으로 통일한다. */
final class SchoolDepartmentNormalizer {
    private static final Pattern ARCHITECTURE =
            Pattern.compile("건축학과\\s*\\(\\s*\\d+\\s*년제\\s*\\)");

    private SchoolDepartmentNormalizer() { }

    static String normalize(String value) {
        if (value == null) return null;
        String department = value.strip();
        if (department.contains("자유전공")) return "자유전공학부";
        if (ARCHITECTURE.matcher(department).matches()) return "건축학과";
        return switch (department) {
            case "인공지능공학전공", "지능형반도체전공", "경영정보시스템전공" -> "인공지능융합학부";
            case "컴퓨터공학전공", "소프트웨어전공" -> "컴퓨터공학부";
            case "항공관광전공", "동양어문화전공" -> "항공관광외국어학부";
            default -> department;
        };
    }
}
