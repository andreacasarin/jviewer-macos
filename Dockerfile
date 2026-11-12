FROM eclipse-temurin:8-jdk-jammy

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        python3 libxext6 libxi6 libxrender1 libxtst6 fontconfig fonts-dejavu-core && \
    rm -rf /var/lib/apt/lists/*

COPY jviewer-starter.py /usr/local/bin/
RUN chmod +x /usr/local/bin/jviewer-starter.py

ENV DISPLAY="host.docker.internal:0"

ENTRYPOINT ["/usr/local/bin/jviewer-starter.py"]
