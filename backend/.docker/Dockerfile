# 基础镜像: Eclipse Temurin JRE 25, 基于 Alpine Linux (体积小)
FROM registry.cn-beijing.aliyuncs.com/jionjion/eclipse-temurin:25-jre-alpine


# 工作目录
WORKDIR /app

# 时区设置为上海
RUN apk add --no-cache tzdata curl \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && apk del tzdata

# 创建非 root 用户, 以最小权限运行应用, 降低安全风险
RUN addgroup -S app && adduser -S app -G app
RUN mkdir -p /app/logs && chown -R app:app /app

# 拷贝构建好的 JAR 包, 并设置文件归属为 app 用户
COPY --chown=app:app target/*.jar app.jar

# 切换到非 root 用户
USER app

# 暴露 Spring Boot 默认端口
EXPOSE 8080

# JVM 参数与 Spring 环境配置
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms256m -Xmx512m"

# 健康检查: 每 30 秒检测一次, 启动后 60 秒开始, 连续 3 次失败则标记为不健康
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
