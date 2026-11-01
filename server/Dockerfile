# syntax=docker/dockerfile:1
# 빌드 컨텍스트: server/ (예: docker build -f server/Dockerfile server)
# Java 버전은 build.gradle.kts의 toolchain(25)과 함께 올려야 합니다.
# JAR는 아키텍처 독립적이므로 빌드는 runner의 native 플랫폼에서 하고, 런타임만 ARM64로 만듭니다.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace

COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
# 테스트는 GitHub Actions의 앞 단계에서 실행합니다. 여기서는 실행 가능한 bootJar만 만듭니다.
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre-noble AS runtime
ARG VCS_REF=unknown
LABEL org.opencontainers.image.source="https://github.com/chabinhwang/autolog-hyundai-2026" \
      org.opencontainers.image.revision="$VCS_REF" \
      org.opencontainers.image.title="AutoLog API"
WORKDIR /app
# 한글 환경변수(SEED_ORG_NAME 등)가 깨지지 않도록 UTF-8 로캘로 실행합니다.
ENV LANG=C.UTF-8

# healthcheck용 curl과 non-root 사용자를 준비합니다. DB 비밀번호 등은 이미지에 복사하지 않습니다.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 autolog \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin autolog
COPY --from=build --chown=10001:10001 /workspace/build/libs/*.jar /app/server.jar

USER 10001:10001
EXPOSE 8080
# exec 형식이므로 JVM이 종료 신호를 받고 graceful shutdown을 수행합니다.
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
