# Giai đoạn 1: build bằng Maven + JDK 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Copy pom.xml trước để Docker cache lớp tải thư viện, chỉ tải lại khi pom đổi.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Giai đoạn 2: image chạy, chỉ cần JRE nên nhẹ hơn nhiều
FROM eclipse-temurin:21-jre
# Không chạy bằng root.
RUN useradd --system --uid 1001 appuser
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
