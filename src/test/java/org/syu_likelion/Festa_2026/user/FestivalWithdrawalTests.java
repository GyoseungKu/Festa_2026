package org.syu_likelion.Festa_2026.user;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.ByteBuffer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.syu_likelion.Festa_2026.bamboo.BambooSequence;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.sso.SsoAuthClient;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:withdrawal;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class FestivalWithdrawalTests {
    @Autowired FestivalWithdrawalService withdrawal;
    @Autowired FestivalUserService profiles;
    @Autowired FestivalUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired BambooSequence sequence;
    @Autowired MockMvc mvc;
    @MockitoBean SsoAuthClient sso;

    @Test void deletesOnlyOwnUserAndPreservesAnonymousPostsAcrossRelinking() throws Exception {
        UUID id = UUID.randomUUID(), other = UUID.randomUUID();
        profiles.linkAndGetProfile(id); profiles.linkAndGetProfile(other);
        long oldId = users.findByUserUuid(id).orElseThrow().getId();
        jdbc.update("insert into festival_booths(latitude,longitude,name,operator,description,opens_at,closes_at,stamp_enabled,created_by,updated_by,created_at,updated_at) values (37,127,'삭제 테스트 부스','운영자','설명','09:00:00','18:00:00',true,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", bytes(id), bytes(id));
        long boothId = jdbc.queryForObject("select id from festival_booths where name = '삭제 테스트 부스'", Long.class);
        jdbc.update("insert into festival_booth_managers(booth_id,festival_user_id) values (?,?)", boothId, oldId);
        jdbc.update("insert into festival_booth_favorites(booth_id,festival_user_id) values (?,?)", boothId, oldId);
        jdbc.update("insert into festival_booth_stamps(booth_id,festival_user_id,granted_at,granted_by,grant_method) values (?,?,CURRENT_TIMESTAMP,?,'QR')", boothId, oldId, bytes(other));
        sequence.writeInOrder(seq -> {
            jdbc.update("insert into bamboo_messages(seq,user_uuid,anon_name,content,status,report_count,created_at) values (?,?,?,?,'VISIBLE',0,CURRENT_TIMESTAMP)",
                    seq, bytes(id), "기존 별명", "유지할 글");
            return null;
        });
        long cursor = sequence.current();
        jdbc.update("insert into birthday_messages(author_uuid,content,public_department,public_masked_student_no,public_masked_name,heart_count,created_at) values (?,?,?,?,?,0,CURRENT_TIMESTAMP)",
                bytes(id), "축하 글", "컴퓨터학과", "2022**1234", "홍*동");
        when(sso.getMe("access")).thenReturn(new UserDtos.MeResponse(id, "login", null, null, null,
                "이름", null, null, null, null, null, null, null, null, null));
        mvc.perform(delete("/api/users/me/festival").header("Authorization", "Bearer access"))
                .andExpect(status().isNoContent()).andExpect(header().exists("Set-Cookie"));
        verify(sso, never()).withdraw(anyString());
        assertThat(users.findByUserUuid(id)).isEmpty();
        assertThat(users.findByUserUuid(other)).isPresent();
        for (String table : java.util.List.of("festival_booth_managers", "festival_booth_favorites", "festival_booth_stamps"))
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where festival_user_id = ?", Long.class, oldId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from festival_booths where id = ?", Long.class, boothId)).isEqualTo(1);
        var post = jdbc.queryForMap("select * from bamboo_messages where content = '유지할 글'");
        assertThat(post.get("anon_name")).isEqualTo("알 수 없음");
        assertThat(((Number) post.get("seq")).longValue()).isGreaterThan(cursor);
        assertThat((byte[]) post.get("user_uuid")).isNotEqualTo(bytes(id));
        assertThat(jdbc.queryForObject("select public_masked_name from birthday_messages where content = '축하 글'", String.class))
                .isEqualTo("알 수 없음");
        withdrawal.withdraw(id); // Repeated deletion must not recreate a user.
        assertThat(users.findByUserUuid(id)).isEmpty();
        profiles.linkAndGetProfile(id);
        assertThat(users.findByUserUuid(id).orElseThrow().getId()).isNotEqualTo(oldId);
        assertThat(jdbc.queryForObject("select count(*) from bamboo_messages where user_uuid = ?", Long.class, bytes(id))).isZero();
    }

    @Test void lastSuperAdminCannotDeleteLocalAccount() {
        UUID id = UUID.randomUUID();
        FestivalUser user = new FestivalUser(id); user.changeManagementRole(FestivalRole.SUPER_ADMIN);
        users.saveAndFlush(user);
        assertThatThrownBy(() -> withdrawal.withdraw(id)).isInstanceOf(ApiException.class);
        assertThat(users.findByUserUuid(id)).isPresent();
        users.deleteById(user.getId());
    }

    @Test void missingBearerCannotDelete() throws Exception {
        mvc.perform(delete("/api/users/me/festival")).andExpect(status().isUnauthorized());
        verifyNoInteractions(sso);
    }

    private static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }
}
