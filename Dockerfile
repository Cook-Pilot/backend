# ---- Stage 1: Build ----
# --platform=$BUILDPLATFORM: 빌드 스테이지는 항상 러너의 네이티브 아키텍처에서 돈다.
# jar는 바이트코드라 arch 독립이므로, multi-arch 빌드여도 Gradle은 딱 한 번만 실행된다.
# (이걸 안 붙이면 arm64 타겟용 Gradle 빌드가 QEMU 에뮬레이션으로 돌아 몇 배 느려진다.)
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Gradle wrapper + dependency 캐시 (build.gradle 변경 시에만 무효화)
COPY gradle/ gradle/
COPY gradlew .
RUN chmod +x gradlew && ./gradlew --version

COPY build.gradle settings.gradle* ./
RUN ./gradlew dependencies --no-daemon

# 소스 빌드
COPY src/ src/
RUN ./gradlew bootJar --no-daemon

# ---- Stage 2: Runtime ----
# 이 스테이지는 타겟 아키텍처별로 만들어진다. jar는 arch 독립이지만 JVM 바이너리는 아니다.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S app && adduser -S -u 1001 -G app app

COPY --from=build /app/build/libs/app.jar app.jar

USER app

EXPOSE 8080

# 컨테이너 메모리 한도의 75%까지 힙 사용 (기본값은 25%)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
