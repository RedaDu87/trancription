FROM eclipse-temurin:21-jdk-jammy

RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY . /app
RUN ./mvnw -q -DskipTests package || mvn -q -DskipTests package

EXPOSE 8080
ENV JAVA_OPTS=""
CMD ["bash","-lc","java $JAVA_OPTS -jar target/AudioTranscriptor-0.0.1.jar"]
