package org.syu_likelion.Festa_2026.schoolsso;

import java.io.Serializable;
import java.time.Instant;

public record SchoolAcademicProfile(
        String studentNo,
        String department,
        String name,
        String consentTarget,
        Instant verifiedAt,
        Instant expiresAt) implements Serializable {
    public SchoolAcademicProfile {
        department = SchoolDepartmentNormalizer.normalize(department);
    }
}
