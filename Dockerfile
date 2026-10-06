# Etapa 1: Compilación y empaquetado con Maven
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace/app

# Copiar archivos de Maven Wrapper
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw

# Copiar pom.xml y descargar dependencias (cache optimizado)
COPY pom.xml .
RUN ./mvnw dependency:go-offline -B

# Copiar código fuente y compilar
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Etapa 2: Imagen de ejecución ligera
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Crear usuario no-root para mayor seguridad
RUN addgroup -S spring && adduser -S spring -G spring

# Copiar JAR desde etapa de build
COPY --from=build /workspace/app/target/*.jar app.jar

# Cambiar permisos y usuario
RUN chown spring:spring app.jar
USER spring

# Render asigna el puerto dinámicamente via variable PORT
ENV PORT=8080
EXPOSE ${PORT}

# Ejecutar aplicación con puerto dinámico para Render
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -jar app.jar --server.port=${PORT}"]