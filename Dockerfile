# Etapa 1: Compilación y empaquetado con Maven
FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /workspace/app

# Copiar archivos de Maven Wrapper
COPY mvnw .
COPY .mvn .mvn

# Dar permisos de ejecución al Maven Wrapper
RUN chmod +x mvnw

# Copiar pom.xml y descargar dependencias
# Esto permite aprovechar la caché de Docker
COPY pom.xml .
RUN ./mvnw dependency:go-offline -B

# Copiar código fuente y compilar
COPY src ./src
RUN ./mvnw clean package -DskipTests -B


# Etapa 2: Imagen de ejecución ligera
FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

# Crear usuario no-root para mayor seguridad
RUN addgroup -S spring && adduser -S spring -G spring

# Copiar JAR generado desde la etapa de build
COPY --from=build /workspace/app/target/*.jar app.jar

# Cambiar propietario del JAR
RUN chown spring:spring app.jar

# Ejecutar como usuario no-root
USER spring

# Render asigna el puerto mediante la variable PORT
ENV PORT=8080

EXPOSE ${PORT}

# Ejecutar aplicación usando el puerto asignado por Render
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -jar app.jar --server.port=${PORT}"]