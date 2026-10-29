# syntax=docker/dockerfile:1

# Stage 1: Compilación de artefactos con JDK 21 Temurin
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copiar configuración de Gradle y wrappers
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ ./gradle/
COPY sovereign-seasons-core/build.gradle.kts ./sovereign-seasons-core/
COPY sovereign-seasons-pantry/build.gradle.kts ./sovereign-seasons-pantry/
COPY sovereign-seasons-logistics/build.gradle.kts ./sovereign-seasons-logistics/
COPY sovereign-seasons-commerce/build.gradle.kts ./sovereign-seasons-commerce/

# Copiar código fuente de los módulos implementados
COPY sovereign-seasons-core/src/ ./sovereign-seasons-core/src/
COPY sovereign-seasons-pantry/src/ ./sovereign-seasons-pantry/src/

# Compilar JARs sombreados con dependencias relocalizadas
RUN chmod +x ./gradlew && ./gradlew :sovereign-seasons-core:shadowJar :sovereign-seasons-pantry:shadowJar --no-daemon

# Stage 2: Servidor Paper 1.21.x containerizado
FROM eclipse-temurin:21-jre-jammy AS runtime

LABEL org.opencontainers.image.title="Sovereign Seasons Paper Server" \
      org.opencontainers.image.description="Servidor Paper 1.21.x containerizado con Sovereign Seasons Core y Pantry" \
      org.opencontainers.image.authors="Cristian <cristianguerrero1958@outlook.com>" \
      org.opencontainers.image.source="https://github.com/Consterna/sovereign-seasons" \
      org.opencontainers.image.licenses="GPL-3.0"

# Instalar utilidades para descarga automatizada de PaperMC
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    jq \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

ENV EULA=false \
    MEMORY=2G \
    MINECRAFT_VERSION=1.21.11 \
    SERVER_PORT=25565

WORKDIR /server

# Directorios de datos y despliegue del plugin
RUN mkdir -p /server/data /opt/plugins
COPY --from=builder /build/sovereign-seasons-core/build/libs/sovereign-seasons-core-*.jar /opt/plugins/sovereign-seasons-core.jar
COPY --from=builder /build/sovereign-seasons-pantry/build/libs/sovereign-seasons-pantry-*.jar /opt/plugins/sovereign-seasons-pantry.jar

# Script de inicio
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

EXPOSE 25565

VOLUME ["/server/data"]

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
