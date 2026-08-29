package org.syu_likelion.Festa_2026.bamboo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.syu_likelion.Festa_2026.error.ApiException;

/**
 * 대나무숲이 점유할 수 있는 동시 요청 수를 제한한다.
 *
 * <p>채팅은 스탬프·부스 지도·QR 과 같은 Tomcat 스레드풀을 공유한다. 상한이 없으면 채팅 폭주가
 * 스레드를 모두 점유해 나머지 기능이 함께 멈춘다. 축제 당일 더 중요한 것은 스탬프이므로,
 * 채팅이 거절당하더라도 남은 스레드는 항상 다른 기능에 남긴다.
 *
 * <p>컨트롤러마다 직접 감싸지 않고 인터셉터로 둔 이유는, 나중에 엔드포인트를 추가할 때
 * 이 보호를 빠뜨릴 수 없게 하기 위해서다.
 */
@Component
public class BambooConcurrencyInterceptor implements HandlerInterceptor {
    private static final String ACQUIRED = BambooConcurrencyInterceptor.class.getName() + ".acquired";

    private final Semaphore permits;

    public BambooConcurrencyInterceptor(BambooProperties properties) {
        this.permits = new Semaphore(properties.maxConcurrentRequests());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!permits.tryAcquire()) {
            response.setHeader("Retry-After", "1");
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "BAMBOO_BUSY",
                    "대나무숲이 혼잡합니다. 잠시 후 다시 시도해 주세요.");
        }
        request.setAttribute(ACQUIRED, Boolean.TRUE);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        if (request.getAttribute(ACQUIRED) == null) return;
        request.removeAttribute(ACQUIRED);
        permits.release();
    }

    int availablePermits() { return permits.availablePermits(); }
}
