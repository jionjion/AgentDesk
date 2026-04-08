package top.jionjion.agentdesk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解, 基于用户维度进行请求频率限制.
 * <p>
 * 在 Controller 方法上标注, 限制同一用户在指定时间窗口内的最大请求次数.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 时间窗口内允许的最大请求次数, 默认 5 次
     */
    int maxRequests() default 5;

    /**
     * 时间窗口大小(秒), 默认 60 秒
     */
    int windowSeconds() default 60;

    /**
     * 限流提示信息
     */
    String message() default "请求过于频繁, 请稍后再试";
}
