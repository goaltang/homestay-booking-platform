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
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ---- 运行阶段：JRE ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/homestay-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
