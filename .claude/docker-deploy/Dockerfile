# 基础镜像 (使用 Eclipse Temurin,OpenJDK 官方推荐发行版)
FROM eclipse-temurin:17-jre

# 维护者信息
LABEL maintainer="xiexu"

# 环境变量
ENV PARAMS=""
ENV JAVA_OPTS="-Xms512m -Xmx512m"
ENV TZ=Asia/Shanghai

# 时区配置
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# 工作目录
WORKDIR /app

# 添加应用 JAR（使用 spring-boot repackage 后的可执行 JAR）
COPY target/ai-rag-knowledge-app-exec.jar /app/ai-rag-knowledge-app.jar

# 暴露端口
EXPOSE 8090

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/ai-rag-knowledge-app.jar --spring.profiles.active=dev $PARAMS"]
