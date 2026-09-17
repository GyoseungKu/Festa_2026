package org.syu_likelion.Festa_2026.temporaryauth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "temporary-auth-ui.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:temporary-auth-disabled;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class TemporaryAuthUiDisabledTests {
    @Autowired MockMvc mvc;
    @Test
    void disabledUiDoesNotClaimFrontendRoutes() throws Exception {
        mvc.perform(get("/temporary-auth")).andExpect(status().isNotFound());
        mvc.perform(get("/syu-sso-test")).andExpect(status().isNotFound());
    }
}
