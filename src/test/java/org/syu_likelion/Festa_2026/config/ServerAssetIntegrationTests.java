package org.syu_likelion.Festa_2026.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ServerAssetIntegrationTests {
    @Autowired MockMvc mvc;

    @Test void allServerFilesArePublicOnlyUnderAdminAssets() throws Exception {
        Path root = Path.of("src/main/resources/static");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                mvc.perform(get("/admin/assets/" + relative)).andExpect(status().isOk())
                        .andExpect(content().bytes(Files.readAllBytes(file)));
                mvc.perform(get("/" + relative)).andExpect(status().isNotFound());
            }
        }
        mvc.perform(get("/admin/assets/webjars/jsqr/1.4.0/dist/jsQR.js")).andExpect(status().isOk());
        mvc.perform(get("/webjars/jsqr/1.4.0/dist/jsQR.js")).andExpect(status().isNotFound());
        mvc.perform(get("/admin/assets/webjars/swagger-ui/index.html")).andExpect(status().isNotFound());
        mvc.perform(get("/favicon.ico")).andExpect(status().isNotFound());
    }

    @Test void renderedPublicPagesUseResolvableServerAssets() throws Exception {
        for (String page : new String[]{"/admin/login", "/terms/service", "/terms/privacy"}) {
            String html = mvc.perform(get(page)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var assets = Pattern.compile("(?:href|src)=\"(/admin/assets/[^\"]+)\"").matcher(html);
            int checked = 0;
            while (assets.find()) {
                mvc.perform(get(assets.group(1))).andExpect(status().isOk());
                checked++;
            }
            assertThat(checked).as(page).isGreaterThan(0);
            assertThat(html).doesNotContain("\"/css/", "\"/js/", "\"/images/", "\"/webjars/");
        }
    }
}
