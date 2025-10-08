FROM mcr.microsoft.com/vscode/devcontainers/java:17

USER root
RUN apt-get update && \
    apt-get install -y wget unzip screen netcat && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /workspace/tools
WORKDIR /workspace
USER vscode
