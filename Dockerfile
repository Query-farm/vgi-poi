# Copyright 2026 Query Farm LLC - https://query.farm
#
# Single image that serves every transport of the `vgi-poi` VGI worker:
#   docker run ... IMG            -> HTTP server on $PORT      (default; Fly.io / local)
#   docker run -i ... IMG stdio   -> stdio worker DuckDB spawns on-host
#   docker run ... IMG unix       -> AF_UNIX launcher on $SOCK (on-host)
# See docker-entrypoint.sh.
#
# This worker is STATELESS: Apache POI parses whatever workbook bytes/paths a
# query hands it, so there is no /data volume, no model registry, and no
# `farm.query.vgi.volumes` mount-discovery label. The image is just the shaded
# fat JAR + a JRE + a tiny entrypoint.
# syntax=docker/dockerfile:1

# ---- build stage -----------------------------------------------------------
# Build the shaded fat JAR (JUnit skipped; the gating CI already ran the full
# suite). The Gradle user home is a BuildKit cache mount, so dependency
# downloads (POI 6.0.0 + vgi/vgirpc from Maven Central) are cached across builds
# but do not persist into the image. The JAR is copied to a fixed name before
# the layer ends because cache mounts are not part of the final image.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

COPY gradle gradle
COPY gradlew gradlew
COPY gradle.properties settings.gradle.kts build.gradle.kts ./
COPY src src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --no-configuration-cache shadowJar \
    && cp "$(ls build/libs/vgi-poi-*-all.jar | head -1)" /worker.jar

# ---- runtime stage ---------------------------------------------------------
# jre-jammy (Ubuntu) so the HEALTHCHECK below has a real `curl`, and so we get a
# JRE-only footprint (no JDK/compiler).
FROM eclipse-temurin:21-jre-jammy

# Build metadata, wired from docker/metadata-action outputs in CI.
ARG VERSION=0.0.0
ARG GIT_COMMIT=unknown
ARG SOURCE_URL=https://github.com/Query-farm/vgi-poi

# Standard OCI labels + the VGI transport-advertisement label. `transports`
# lists the NETWORK transports this image serves (http); stdio and the AF_UNIX
# launcher are on-host spawn modes, not network transports, so they are not
# listed.
LABEL org.opencontainers.image.title="vgi-poi" \
      org.opencontainers.image.description="Read & write Microsoft Excel (.xls/.xlsx) spreadsheets as DuckDB/SQL functions — a VGI worker backed by Apache POI (stdio + HTTP + unix)" \
      org.opencontainers.image.source="${SOURCE_URL}" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${GIT_COMMIT}" \
      org.opencontainers.image.licenses="MIT" \
      farm.query.vgi.transports='["http"]'

# PORT   — fixed HTTP listen port (the entrypoint binds 0.0.0.0:$PORT).
# The version the worker advertises over VGI comes from VGI_POI_GIT_COMMIT
# (see Main.GIT_COMMIT), not from the build ARG above.
ENV PORT=8000 \
    VGI_POI_GIT_COMMIT=${GIT_COMMIT}

WORKDIR /app

# curl backs the HEALTHCHECK below; nothing else is needed at runtime.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /worker.jar /app/worker.jar
COPY --chmod=0755 docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

# Run unprivileged. No state, no volume — there is nothing to own or persist.
RUN useradd --create-home --uid 10001 app
USER app

EXPOSE 8000

# Readiness probe for HTTP mode. Inert for a short-lived stdio container, which
# has no HTTP server (the probe just fails harmlessly there).
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS "http://localhost:${PORT:-8000}/health" || exit 1

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["http"]
