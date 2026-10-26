#!/usr/bin/env bash
set -euo pipefail

DATA_DIR="/server/data"
PLUGINS_DIR="${DATA_DIR}/plugins"
PAPER_JAR="${DATA_DIR}/paper.jar"
MINECRAFT_VERSION="${MINECRAFT_VERSION:-1.21.4}"
MEMORY="${MEMORY:-2G}"
EULA="${EULA:-false}"

cd "${DATA_DIR}"

echo "=========================================================="
echo "🌾 Sovereign Seasons - Paper Server Initialization"
echo "Minecraft Version: ${MINECRAFT_VERSION}"
echo "Allocated Memory : ${MEMORY}"
echo "=========================================================="

# Check EULA acceptance
if [[ "${EULA,,}" == "true" ]]; then
    echo "eula=true" > eula.txt
    echo "[INFO] EULA accepted via environment variable."
else
    echo "eula=false" > eula.txt
    echo "[ERROR] You must accept the Minecraft EULA by setting EULA=true."
    echo "[ERROR] Exiting server initialization."
    exit 1
fi

# Ensure plugins directory exists and synchronize plugin jar
mkdir -p "${PLUGINS_DIR}"
if [[ -f "/opt/plugins/sovereign-seasons-core.jar" ]]; then
    cp -u /opt/plugins/sovereign-seasons-core.jar "${PLUGINS_DIR}/sovereign-seasons-core.jar" 2>/dev/null || cp /opt/plugins/sovereign-seasons-core.jar "${PLUGINS_DIR}/sovereign-seasons-core.jar"
    echo "[INFO] Sovereign Seasons Core plugin deployed to ${PLUGINS_DIR}/sovereign-seasons-core.jar"
fi

# Download Paper jar if not present
if [[ ! -f "${PAPER_JAR}" ]]; then
    echo "[INFO] Paper jar not found. Fetching latest build for Paper ${MINECRAFT_VERSION} from PaperMC v3 API..."
    
    USER_AGENT="sovereign-seasons-server/1.0 (cristianguerrero1958@outlook.com)"
    API_URL="https://fill.papermc.io/v3/projects/paper/versions/${MINECRAFT_VERSION}/builds/latest"
    DOWNLOAD_URL=$(curl -fsSL -H "User-Agent: ${USER_AGENT}" "${API_URL}" | jq -r '.downloads["server:default"].url // empty' 2>/dev/null || echo "")
    
    if [[ -z "${DOWNLOAD_URL}" || "${DOWNLOAD_URL}" == "null" ]]; then
        echo "[ERROR] Could not resolve Paper build for version ${MINECRAFT_VERSION}. Please mount a valid paper.jar to ${PAPER_JAR}."
        exit 1
    fi
    
    echo "[INFO] Downloading Paper jar from ${DOWNLOAD_URL}..."
    curl -fsSL -H "User-Agent: ${USER_AGENT}" -o "${PAPER_JAR}" "${DOWNLOAD_URL}"
    echo "[INFO] Paper ${MINECRAFT_VERSION} downloaded successfully."
fi

# JVM Flags - Aikar's optimized G1GC flags for Minecraft Paper servers
JAVA_FLAGS=(
    "-Xms${MEMORY}"
    "-Xmx${MEMORY}"
    "-XX:+UseG1GC"
    "-XX:+ParallelRefProcEnabled"
    "-XX:MaxGCPauseMillis=200"
    "-XX:+UnlockExperimentalVMOptions"
    "-XX:+DisableExplicitGC"
    "-XX:+AlwaysPreTouch"
    "-XX:G1NewSizePercent=30"
    "-XX:G1MaxNewSizePercent=40"
    "-XX:G1ReservePercent=20"
    "-XX:G1HeapWastePercent=5"
    "-XX:G1MixedGCCountTarget=4"
    "-XX:G1InitiatingHeapOccupancyPercent=15"
    "-XX:G1MixedGCLiveThresholdPercent=90"
    "-XX:G1RSetUpdatingPauseTimePercent=5"
    "-XX:SurvivorRatio=32"
    "-XX:+PerfDisableSharedMem"
    "-XX:MaxTenuringThreshold=1"
    "-Dusing.aikars.flags=https://mcflags.emc.gs"
    "-Daikars.new.flags=true"
)

echo "[INFO] Starting Paper server..."
exec java "${JAVA_FLAGS[@]}" -jar "${PAPER_JAR}" --nogui
