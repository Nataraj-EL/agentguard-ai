#!/bin/bash
# Portable Environment Config
export JAVA_HOME="/home/nataraj/Downloads/AgentGuard-AI/portable-env/jdk-17.0.19+10"
export PATH="/home/nataraj/Downloads/AgentGuard-AI/portable-env/apache-maven-3.9.6/bin:$PATH"

echo "☕ [AgentGuard] Launching Core backend utilizing sandboxed environment..."
echo "Java Location: $JAVA_HOME"
java -version
mvn -version

cd "/home/nataraj/Downloads/AgentGuard-AI/backend"
mvn spring-boot:run
