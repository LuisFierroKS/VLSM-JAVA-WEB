# Usar una imagen de Java con el JDK
FROM openjdk:17-jdk-slim

# Configurar el directorio de trabajo
WORKDIR /app

# Copiar todos los archivos del proyecto al contenedor
COPY . /app

# Compilar el proyecto
RUN javac -cp "libs/*" -d out src/ProcesarDatos.java && \
    jar cvf procesar-datos.jar -C out .

# Comando para ejecutar la aplicación
CMD ["java", "-cp", "libs/*:procesar-datos.jar", "ProcesarDatos"]
