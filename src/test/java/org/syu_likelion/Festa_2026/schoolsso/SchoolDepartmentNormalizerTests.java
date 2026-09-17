package org.syu_likelion.Festa_2026.schoolsso;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class SchoolDepartmentNormalizerTests {
    @ParameterizedTest
    @CsvSource({
            "인공지능공학전공, 인공지능융합학부",
            "지능형반도체전공, 인공지능융합학부",
            "경영정보시스템전공, 인공지능융합학부",
            "컴퓨터공학전공, 컴퓨터공학부",
            "소프트웨어전공, 컴퓨터공학부",
            "항공관광전공, 항공관광외국어학부",
            "동양어문화전공, 항공관광외국어학부",
            "자유전공, 자유전공학부",
            "자유전공학부(인문사회), 자유전공학부",
            "첨단융합자유전공계열, 자유전공학부",
            "건축학과(5년제), 건축학과",
            "건축학과 (4년제), 건축학과",
            "건축학과( 5 년제 ), 건축학과",
            "' 소프트웨어전공 ', 컴퓨터공학부",
            "컴퓨터공학부, 컴퓨터공학부",
            "인공지능융합학부, 인공지능융합학부",
            "항공관광외국어학부, 항공관광외국어학부",
            "자유전공학부, 자유전공학부",
            "건축학과, 건축학과",
            "건축학과(야간), 건축학과(야간)",
            "건축공학과(4년제), 건축공학과(4년제)",
            "컴퓨터공학과, 컴퓨터공학과",
            "간호학과, 간호학과"
    })
    void schoolProfileUsesCanonicalDepartment(String input, String expected) {
        var profile = new SchoolAcademicProfile("20260001", input, "학생", "축제",
                Instant.EPOCH, Instant.EPOCH.plusSeconds(900));
        assertThat(profile.department()).isEqualTo(expected);
        assertThat(SchoolDepartmentNormalizer.normalize(profile.department())).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void missingValuesAreNotInvented(String input) {
        assertThat(SchoolDepartmentNormalizer.normalize(input)).isEqualTo(input);
    }
}
