package org.syu_likelion.Festa_2026.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.syu_likelion.Festa_2026.analytics.FrontendAnalyticsDtos.EventBatchResponse;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.auth.TokenCookieManager;

class FrontendAnalyticsControllerTests {

    @Test
    void acceptsAnonymousBatchWithoutAuthorizationHeader() throws Exception {
        FrontendAnalyticsService service = mock(FrontendAnalyticsService.class);
        TokenCookieManager cookies = mock(TokenCookieManager.class);
        when(service.ingest(eq(null), eq(null), any()))
                .thenReturn(new AuthorizedResult<>(new EventBatchResponse(1), null, null));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new FrontendAnalyticsController(service, cookies)).build();

        mvc.perform(post("/api/analytics/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId":"da4d31fd-6ccb-4cba-a44f-20f27486f73a",
                                  "events":[{
                                    "eventId":"65d89a76-e521-40ee-80ed-4e27d17c58c2",
                                    "type":"PAGE_VIEW",
                                    "route":"/",
                                    "occurredAt":"2026-08-10T12:00:00Z"
                                  }]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.acceptedEvents").value(1));
    }

    @Test
    void acceptsBatchAndReturnsRotatedTokens() throws Exception {
        FrontendAnalyticsService service = mock(FrontendAnalyticsService.class);
        TokenCookieManager cookies = mock(TokenCookieManager.class);
        when(cookies.readRefreshToken(any())).thenReturn("refresh");
        when(cookies.create("new-refresh")).thenReturn("festivalRefreshToken=new-refresh; HttpOnly");
        when(service.ingest(eq("access"), eq("refresh"), any()))
                .thenReturn(new AuthorizedResult<>(new EventBatchResponse(1),
                        "new-access", "new-refresh"));
        FrontendAnalyticsController controller = new FrontendAnalyticsController(service, cookies);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/api/analytics/events")
                        .header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId":"da4d31fd-6ccb-4cba-a44f-20f27486f73a",
                                  "events":[{
                                    "eventId":"65d89a76-e521-40ee-80ed-4e27d17c58c2",
                                    "type":"PAGE_VIEW",
                                    "route":"/performances",
                                    "occurredAt":"2026-08-10T12:00:00Z"
                                  }]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.acceptedEvents").value(1))
                .andExpect(header().string("X-Access-Token", "new-access"))
                .andExpect(header().string("Set-Cookie", "festivalRefreshToken=new-refresh; HttpOnly"));
    }
}
