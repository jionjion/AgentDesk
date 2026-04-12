package top.jionjion.agentdesk.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
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
 * 限流拦截器: 基于用户维度的滑动窗口限流.
 * <p>
 * 使用内存 ConcurrentHashMap 存储每个用户每个接口的请求时间戳队列,
 * 通过滑动窗口算法判断是否超过限流阈值.
 *
 * @author Jion
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /**
     * key = userId:handlerClass#method, value = 请求时间戳队列
     */
    private final ConcurrentHashMap<String, Deque<Long>> requestRecords = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        // 用户必须已认证
        if (!UserContext.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未认证, 无法访问该接口");
        }

        Long userId = UserContext.getUserId();
        String key = userId + ":" + handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();

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
}
