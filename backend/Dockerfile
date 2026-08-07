# syntax=docker/dockerfile:1

# ---- build stage ----------------------------------------------------------
# gradle:8-jdk21 이미지 대신 JDK + 프로젝트의 Gradle wrapper를 쓴다.
# wrapper가 gradle-wrapper.properties에 고정한 버전(8.14.5)으로 빌드해야
# 로컬과 이미지가 같은 Gradle로 빌드된다. 베이스 이미지의 Gradle을 쓰면 둘이 갈린다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 레이어를 먼저 굳힌다. src만 바뀌면 이 레이어가 캐시에서 재사용되어
# 매 빌드마다 의존성을 다시 받지 않는다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
# 테스트는 이미지 빌드에서 제외한다. OrderConcurrencyTest는 스레드 레이스를
# 최대 5회 반복하므로 빌드 시간이 환경에 따라 크게 흔들린다. 테스트는 CI/로컬에서 돌린다.
RUN ./gradlew --no-daemon bootJar -x test

# ---- runtime stage --------------------------------------------------------
# JRE만 남긴다. JDK와 Gradle 캐시를 최종 이미지에서 제외하기 위해 스테이지를 나눴다.
FROM eclipse-temurin:21-jre
WORKDIR /app

# compose healthcheck에서 쓴다. 이 프로젝트는 actuator를 넣지 않았으므로
# 실제 API 엔드포인트로 살아있음을 확인한다.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# root로 돌리지 않는다. 컨테이너가 뚫렸을 때 피해 범위를 줄인다.
RUN useradd --system --create-home --shell /usr/sbin/nologin appuser
COPY --from=build /app/build/libs/*.jar app.jar
RUN chown appuser:appuser /app/app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
