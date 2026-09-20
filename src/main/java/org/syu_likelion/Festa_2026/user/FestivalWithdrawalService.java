package org.syu_likelion.Festa_2026.user;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.syu_likelion.Festa_2026.bamboo.BambooSequence;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.fee.StudentFeeService;

@Service
public class FestivalWithdrawalService {
    private final EntityManager em;
    private final FestivalUserRepository users;
    private final BambooSequence sequence;
    private final StudentFeeService fees;

    public FestivalWithdrawalService(EntityManager em, FestivalUserRepository users,
                                    BambooSequence sequence, StudentFeeService fees) {
        this.em = em; this.users = users; this.sequence = sequence; this.fees = fees;
    }

    public boolean withdraw(UUID userUuid) {
        return withdraw(userUuid, () -> { });
    }

    /** Run upstream withdrawal only after local validation and SQL flush; upstream failure rolls back local changes. */
    boolean withdraw(UUID userUuid, Runnable beforeCommit) {
        // The sequence owns the transaction so anonymized names also reach polling clients after commit.
        return sequence.writeBatchInOrder(next -> {
            fees.lock();
            List<FestivalUser> superAdmins = users.findAllByManagementRoleForUpdate(FestivalRole.SUPER_ADMIN);
            FestivalUser user = users.findByUserUuidForUpdate(userUuid).orElse(null);
            if (user == null) {
                beforeCommit.run();
                return false;
            }
            if (user.getManagementRole() == FestivalRole.SUPER_ADMIN && superAdmins.size() <= 1)
                throw new ApiException(HttpStatus.CONFLICT, "LAST_SUPER_ADMIN_REQUIRED",
                        "마지막 SUPER_ADMIN은 권한을 다른 사용자에게 넘긴 후 서비스 정보를 삭제할 수 있습니다.");
            UUID anonymous = DeletedUserIdentity.create();
            em.flush();
            for (Long id : em.createQuery("select m.id from BambooMessage m where m.userUuid = :id order by m.id", Long.class)
                    .setParameter("id", userUuid).getResultList()) {
                em.createQuery("update BambooMessage set userUuid = :anonymous, anonName = :name, seq = :seq where id = :id")
                        .setParameter("anonymous", anonymous).setParameter("name", DeletedUserIdentity.NAME)
                        .setParameter("seq", next.getAsLong()).setParameter("id", id).executeUpdate();
            }
            update("BirthdayMessage", "authorUuid", ", publicDepartment = null, publicMaskedStudentNo = '', publicMaskedName = '알 수 없음'", userUuid, anonymous);
            update("LostItemNotice", "authorUuid", ", authorName = '알 수 없음'", userUuid, anonymous);
            update("BambooModerationAudit", "targetUserUuid", ", targetNickname = '알 수 없음'", userUuid, anonymous);
            update("BambooModerationAudit", "actorUuid", ", actorName = '알 수 없음'", userUuid, anonymous);
            // Keep the school-subject HMAC and state to prevent duplicate wristbands after rejoining.
            update("Wristband", "targetUserUuid", ", targetName = '알 수 없음'", userUuid, anonymous);
            update("Wristband", "activeUserUuid", "", userUuid, anonymous);
            update("Wristband", "issuedBy", ", issuerName = '알 수 없음'", userUuid, anonymous);
            update("WristbandEvent", "targetUserUuid", "", userUuid, anonymous);
            update("WristbandEvent", "actorUuid", ", actorName = '알 수 없음'", userUuid, anonymous);
            for (String entry : List.of("BambooMessage.deletedBy", "BambooReport.userUuid", "BirthdayMessageHeart.userUuid",
                    "LostItemNotice.lastModifiedByUuid", "PollSubmission.userUuid", "PollSubmission.singleVoteKey",
                    "FestivalPoll.createdBy", "FestivalPoll.updatedBy", "FestivalBooth.createdBy", "FestivalBooth.updatedBy",
                    "FestivalPerformance.createdBy", "FestivalPerformance.updatedBy", "BoothStamp.grantedBy",
                    "StampEvent.targetUserUuid", "StampEvent.actorUuid")) {
                String[] parts = entry.split("\\.");
                update(parts[0], parts[1], "", userUuid, anonymous);
            }
            for (String entity : List.of("BambooNickname", "SchoolVerificationRequest", "FestivalQrToken"))
                em.createQuery("delete from " + entity + " where userUuid = :id").setParameter("id", userUuid).executeUpdate();
            for (String entity : List.of("BoothFavorite", "BoothStamp"))
                em.createQuery("delete from " + entity + " where user.id = :id").setParameter("id", user.getId()).executeUpdate();
            em.createNativeQuery("delete from festival_booth_managers where festival_user_id = :id")
                    .setParameter("id", user.getId()).executeUpdate();
            users.delete(user);
            em.flush();
            em.clear();
            beforeCommit.run();
            return true;
        });
    }

    private void update(String entity, String field, String extra, UUID original, UUID anonymous) {
        em.createQuery("update " + entity + " set " + field + " = :anonymous" + extra + " where " + field + " = :original")
                .setParameter("anonymous", anonymous).setParameter("original", original).executeUpdate();
    }
}
