# ============================================================
#  电商订单智能客服 —— Java 业务服务镜像（多阶段构建）
# ============================================================
#  为什么用"多阶段"？
#    阶段 1 里装 Maven + JDK + 源码，把 jar 编出来；
#    阶段 2 只把编好的那一个 jar 拷进一个干净的 JRE 里。
#    最终镜像里没有 Maven、没有源码、没有 .m2 依赖仓库
#    —— 体积从 600MB+ 降到 200MB 左右，也更安全。
#
#  单独构建（平时不用手敲，docker compose 会自动做）：
#    docker build -t commerce-service/java:local .
#
#  常用参数（构建时覆盖，不用改文件）：
#    --build-arg 目前没有；版本升级改下面 FROM 的 tag 即可
# ============================================================

# ---------------- 阶段 1：编译 ----------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 换成阿里云镜像源。不换的话从国外中央仓库拉依赖会慢到怀疑人生。
COPY .docker/maven-settings.xml /root/.m2/settings.xml

# 先只拷 pom.xml 跑一次"把依赖都下下来"。
# 这一步单独成层是为了吃 Docker 的层缓存：以后只要你没改 pom.xml，
# 重新打镜像时这层直接命中缓存，几秒钟跳过。
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -q dependency:go-offline -DskipTests || true

# 再拷源码，真正编译打包（跳过单元测试，加快速度）
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B clean package -DskipTests

# ---------------- 阶段 2：运行 ----------------
FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

# 只把 jar 拿过来，别的什么都不带
COPY --from=build /build/target/*.jar /app/app.jar

EXPOSE 8080

# --server.port=8080 写死在这里，防止被环境里同名的变量顶掉。
# 所有敏感配置（数据库密码 / jwt.secret / internal.token）都不进镜像，
# 由 docker-compose 在启动时用环境变量注入。
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --server.port=8080"]
