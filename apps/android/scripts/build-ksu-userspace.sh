#!/usr/bin/env bash
set -euo pipefail

# Run in the prepared build checkout inside ksu-build.Dockerfile's image.
export KSU_PACKAGE_NAME=com.chichu.toolbox
for target in arm64-v8a x86_64; do
    case "$target" in
        arm64-v8a) triple=aarch64-linux-android; assets=aarch64 ;;
        x86_64) triple=x86_64-linux-android; assets=x86_64 ;;
    esac
    cargo ndk -t "$target" --platform 26 check -p ksud --locked
    cargo ndk -t "$target" --platform 26 clippy -p ksud --locked
    cargo fmt --all
    cargo ndk -t "$target" --platform 26 build --release -p ksuinit --locked
    cp "target/$triple/release/ksuinit" "userspace/ksud/bin/$assets/ksuinit"
    cargo ndk -t "$target" --platform 26 build --release -p ksud --locked
done
