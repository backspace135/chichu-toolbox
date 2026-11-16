FROM rust:1.91-bookworm
RUN apt-get update && apt-get install -y --no-install-recommends clang libclang-dev curl unzip ca-certificates && rm -rf /var/lib/apt/lists/*
RUN curl -fL https://dl.google.com/android/repository/android-ndk-r29-linux.zip -o /tmp/ndk.zip && unzip -q /tmp/ndk.zip -d /opt && rm /tmp/ndk.zip
ENV ANDROID_NDK_HOME=/opt/android-ndk-r29
RUN rustup target add aarch64-linux-android x86_64-linux-android && rustup component add clippy rustfmt && cargo install cargo-ndk --locked
WORKDIR /work
