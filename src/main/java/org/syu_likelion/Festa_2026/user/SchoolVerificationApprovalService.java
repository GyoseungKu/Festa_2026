package org.syu_likelion.Festa_2026.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.schoolsso.SchoolAcademicProfile;
import org.syu_likelion.Festa_2026.schoolsso.SchoolSubjectHasher;

@Service
public class SchoolVerificationApprovalService {
    private final SchoolVerificationRequestRepository requests;
    private final FestivalUserService festivalUsers;
    private final SchoolSubjectHasher subjects;

    public SchoolVerificationApprovalService(SchoolVerificationRequestRepository requests,
                                             FestivalUserService festivalUsers,
                                             SchoolSubjectHasher subjects) {
        this.requests = requests;
        this.festivalUsers = festivalUsers;
        this.subjects = subjects;
    }

    @Transactional
    public void request(UUID userUuid, String currentName, String currentStudentNo, String currentDepartment,
                        SchoolAcademicProfile school) {
        String hash = subjects.hash(school.studentNo());
        SchoolVerificationRequest request = requests.findByUserUuid(userUuid).orElse(null);
        if (request == null) {
            request = new SchoolVerificationRequest(userUuid, currentName, currentStudentNo, currentDepartment,
                    school.name(), school.studentNo(), school.department(), hash, school.verifiedAt());
        } else {
            request.replace(currentName, currentStudentNo, currentDepartment, school.name(), school.studentNo(),
                    school.department(), hash, school.verifiedAt());
        }
        requests.save(request);
    }

    @Transactional(readOnly = true)
    public List<RequestResponse> list() {
        return requests.findAllByOrderByRequestedAtAsc().stream().map(RequestResponse::from).toList();
    }

    @Transactional
    public void approve(Long id) {
        SchoolVerificationRequest request = require(id);
        festivalUsers.verifySchool(request.getUserUuid(), new SchoolAcademicProfile(request.getSchoolStudentNo(),
                request.getSchoolDepartment(), request.getSchoolName(), null,
                request.getSchoolVerifiedAt(), Instant.now().plusSeconds(60)));
        requests.delete(request);
    }

    @Transactional
    public void delete(Long id) { requests.delete(require(id)); }

    @Transactional
    public void clear(UUID userUuid) { requests.deleteByUserUuid(userUuid); }

    private SchoolVerificationRequest require(Long id) {
        return requests.findByIdForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "SCHOOL_VERIFICATION_REQUEST_NOT_FOUND", "학생 인증 승인 요청을 찾을 수 없습니다."));
    }

    public record RequestResponse(Long id, UUID userUuid, String currentName, String currentStudentNo,
                                  String currentDepartment, String schoolName, String schoolStudentNo,
                                  String schoolDepartment, Instant requestedAt) {
        static RequestResponse from(SchoolVerificationRequest request) {
            return new RequestResponse(request.getId(), request.getUserUuid(), request.getCurrentName(),
                    request.getCurrentStudentNo(), request.getCurrentDepartment(), request.getSchoolName(),
                    request.getSchoolStudentNo(), request.getSchoolDepartment(), request.getRequestedAt());
        }
    }
}
