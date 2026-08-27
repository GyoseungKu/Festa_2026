package org.syu_likelion.Festa_2026.temporaryauth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "temporary-auth-ui.enabled=true",
        "school-sso.enabled=false",
        "school-sso.client-id=festa-2026",
        "school-sso.authorize-url=https://www.syu.ac.kr/festa-sso/authorize",
        "school-sso.callback-url=https://festa.syu-likelion.org/auth/sso/callback"
})
@AutoConfigureMockMvc
class TemporaryAuthUiControllerTests {
    @Autowired MockMvc mvc;

    @Test
    void temporaryPortalRendersBothSignupMethods() throws Exception {
        mvc.perform(get("/temporary-auth"))
                .andExpect(status().isOk())
                .andExpect(view().name("temporary-auth"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("학교 SSO에서 불러오기")))
                .andExpect(content().string(containsString("직접 입력")))
                .andExpect(content().string(containsString("academicInfoSource")));
    }

    @Test
    void oldTemporaryUrlRemainsAsAlias() throws Exception {
        mvc.perform(get("/syu-sso-test"))
                .andExpect(status().isOk())
                .andExpect(view().name("temporary-auth"));
    }
}
