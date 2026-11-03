FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/nfs-demo/ /app/
USER 10001:10001
ENTRYPOINT ["java", "-cp", "/app/classes:/app/lib/*", "example.NfsDemo"]
CMD ["nfs", "/export"]
