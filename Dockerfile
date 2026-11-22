#------------阶段一：构建------------
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

#maven 镜像源（阿里云），放到 Maven 的全局配置位置
COPY .docker/maven-settings.xml /root/.m2/settings.xml

#先只拷 pom ——让【下载依赖】这一层能被缓存
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 再拷源码，编译打包
COPY src ./src
RUN mvn -B -q clean package -DskipTests

#------------阶段二： 运行------------
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
