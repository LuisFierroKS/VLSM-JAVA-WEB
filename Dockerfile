FROM openjdk:17-jdk-slim
WORKDIR /app
COPY . /app
RUN javac -cp "libs/*" -d out src/main/java/ProcesarDatos.java && \
    jar cvf procesar-datos.jar -C out . -C src/main/resources/ .
CMD ["java", "-cp", "libs/*:procesar-datos.jar", "ProcesarDatos"]
