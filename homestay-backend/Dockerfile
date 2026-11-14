# ============================================
# homestay-backend 生产镜像（multi-stage）
# 构建：docker build -t homestay-backend .
# 运行：SPRING_PROFILES_ACTIVE=prod + 环境变量注入（见根目录 .env.example）
# ============================================

# ---- 构建阶段：Maven 打包 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 阿里云镜像加速（国内网络）
COPY settings-docker.xml /root/.m2/settings.xml
COPY pom.xml .
# 国内镜像源偶发断开或卡住连接：本地仓库放在 BuildKit 缓存中，重试不必从头下载；
# 限制并发、设置超时，重试时用 -U 重新下载上次失败的依赖
ENV MAVEN_NET_OPTS="-Dmaven.artifact.threads=2 -Daether.connector.connectTimeout=15000 -Daether.connector.requestTimeout=60000"
RUN --mount=type=cache,target=/root/.m2/repository \
    for i in 1 2 3 4 5; do \
      mvn -B -ntp $MAVEN_NET_OPTS -U dependency:go-offline && exit 0; \
      echo "依赖下载失败，第 $i 次重试"; sleep 5; \
    done; exit 1
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -ntp $MAVEN_NET_OPTS package -DskipTests

# ---- 运行阶段：JRE ----
FROM eclipse-temurin:17-jre
# curl 供容器健康检查使用（/actuator/health）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/homestay-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
