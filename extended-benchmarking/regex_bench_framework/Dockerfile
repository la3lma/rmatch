FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
ENV RMATCH_JAVA_TARGET=21

RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    ca-certificates \
    coreutils \
    curl \
    findutils \
    git \
    maven \
    openjdk-21-jdk \
    python3 \
    python3-pip \
    python3-venv \
    wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace/rmatch

# Build context should be repository root.
COPY . /workspace/rmatch

WORKDIR /workspace/rmatch/extended-benchmarking/regex_bench_framework

RUN python3 -m venv .venv \
    && . .venv/bin/activate \
    && pip install --upgrade pip setuptools wheel \
    && pip install -e . \
    && bash engines/java-native-naive/build.sh \
    && bash engines/re2j/build.sh \
    && bash engines/rmatch/build.sh

CMD ["/bin/bash"]
