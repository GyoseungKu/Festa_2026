package org.syu_likelion.Festa_2026.bamboo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.syu_likelion.Festa_2026.error.ApiException;

class BambooConcurrencyInterceptorTests {

    @Test
    void refusesRequestsBeyondTheLimitSoOtherFeaturesKeepTheirThreads() {
        BambooConcurrencyInterceptor interceptor = interceptor(2);
        MockHttpServletRequest first = new MockHttpServletRequest();
        MockHttpServletRequest second = new MockHttpServletRequest();
        MockHttpServletRequest third = new MockHttpServletRequest();

        assertThat(interceptor.preHandle(first, new MockHttpServletResponse(), null)).isTrue();
        assertThat(interceptor.preHandle(second, new MockHttpServletResponse(), null)).isTrue();
        assertThat(interceptor.availablePermits()).isZero();

        MockHttpServletResponse rejected = new MockHttpServletResponse();
        assertThatThrownBy(() -> interceptor.preHandle(third, rejected, null))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_BUSY");
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("1");
    }

    @Test
    void releasesThePermitWhenTheRequestFinishes() {
        BambooConcurrencyInterceptor interceptor = interceptor(1);
        MockHttpServletRequest request = new MockHttpServletRequest();

        interceptor.preHandle(request, new MockHttpServletResponse(), null);
        interceptor.afterCompletion(request, new MockHttpServletResponse(), null, null);

        assertThat(interceptor.availablePermits()).isEqualTo(1);
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), null))
                .isTrue();
    }

    @Test
    void neverReleasesAPermitItDidNotAcquire() {
        BambooConcurrencyInterceptor interceptor = interceptor(1);
        MockHttpServletRequest rejected = new MockHttpServletRequest();

        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), null);
        assertThatThrownBy(() -> interceptor.preHandle(rejected, new MockHttpServletResponse(), null))
                .isInstanceOf(ApiException.class);
        interceptor.afterCompletion(rejected, new MockHttpServletResponse(), null, null);

        assertThat(interceptor.availablePermits()).isZero();
    }

    @Test
    void releasesOnlyOncePerRequest() {
        BambooConcurrencyInterceptor interceptor = interceptor(1);
        MockHttpServletRequest request = new MockHttpServletRequest();

        interceptor.preHandle(request, new MockHttpServletResponse(), null);
        interceptor.afterCompletion(request, new MockHttpServletResponse(), null, null);
        interceptor.afterCompletion(request, new MockHttpServletResponse(), null, null);

        assertThat(interceptor.availablePermits()).isEqualTo(1);
    }

    @Test
    void rejectsAnOversizedBodyBeforeItConsumesAConcurrencyPermit() {
        BambooConcurrencyInterceptor interceptor = interceptor(1);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(new byte[20_000]);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), null))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BAMBOO_REQUEST_TOO_LARGE");
        assertThat(interceptor.availablePermits()).isEqualTo(1);
    }

    private BambooConcurrencyInterceptor interceptor(int permits) {
        return new BambooConcurrencyInterceptor(new BambooProperties(permits, Duration.ofSeconds(5),
                10, 200, 10, 100_000, List.of()));
    }
}
