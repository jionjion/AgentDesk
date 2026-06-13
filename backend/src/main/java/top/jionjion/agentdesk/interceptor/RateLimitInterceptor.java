package top.jionjion.agentdesk.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import top.jionjion.agentdesk.annotation.RateLimit;
import top.jionjion.agentdesk.security.UserContext;

import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 限流拦截器: 已登录请求按用户限流, 未登录请求按客户端 IP 限流.
 * <p>
 * 使用内存 ConcurrentHashMap 存储每个身份每个接口的请求时间戳队列,
 * 通过滑动窗口算法判断是否超过限流阈值.
 *
 * @author Jion
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /**
     * key = identity:handlerClass#method, value = 请求时间戳队列
     */
    private final ConcurrentHashMap<String, Deque<Long>> requestRecords = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        String identity = UserContext.isAuthenticated()
                ? "user:" + UserContext.getUserId()
                : "ip:" + clientIp(request);
        String key = identity + ":" + handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();

        long now = System.currentTimeMillis();
        long windowStart = now - rateLimit.windowSeconds() * 1000L;

        Deque<Long> timestamps = requestRecords.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        // 清除窗口外的过期记录
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= rateLimit.maxRequests()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, rateLimit.message());
        }

        timestamps.addLast(now);
        return true;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
