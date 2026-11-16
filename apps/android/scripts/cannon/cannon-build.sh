#!/bin/bash
# cannon (Redmi Note 9 5G, MIUI V14.0.6.0.SJECNXM) 4.14 kernel + KernelSU build.
# Base: cannon-r-oss 30fa96c9 + baseline.patch (UDI fix + fts8719 firmware blob).
# KernelSU: tiann/KernelSU 730f6446 (UAPI 6) with the 4.14 compat layer.
# Bound to the toolbox: package com.chichu.toolbox, cert 0x02e8 / 7ba84aa1...1418.
# Config: real stock config extracted from the device (stock-config.gz).
set -euo pipefail
cd "$(dirname "$0")/work"

# 1) Baseline patch to the kernel tree.
if (cd src && git apply --check ../baseline.patch 2>/dev/null); then
  (cd src && git apply ../baseline.patch)
  echo "[cannon] baseline.patch applied"
else
  echo "[cannon] baseline.patch already applied"
fi

# 2) Wire KernelSU into the tree.
[ -e src/drivers/kernelsu ] || ln -s ../../ksu/kernel src/drivers/kernelsu
grep -q 'drivers/kernelsu/Kconfig' src/drivers/Kconfig || \
  sed -i 's|^endmenu$|source "drivers/kernelsu/Kconfig"\nendmenu|' src/drivers/Kconfig
grep -q 'obj-$(CONFIG_KSU)' src/drivers/Makefile || \
  printf '\nobj-$(CONFIG_KSU) += kernelsu/\n' >> src/drivers/Makefile

# 3) Toolchain image (Debian bookworm clang 14.0.6, gcc for host tools).
docker build -q -t cannon-ksu-build -f ../docker/Dockerfile.cannon ../docker
echo "[cannon] toolchain image ready"

# 4) Config: stock config from the device + KernelSU + identifiable localversion.
# Written inside the container: previous docker builds own the out/ directory.
mkdir -p out
docker run --rm -v "$PWD":/work cannon-ksu-build bash -c '
zcat /work/stock-config.gz > /work/out/.config
cat >> /work/out/.config <<EOF
CONFIG_KSU=y
# CONFIG_KSU_DEBUG is not set
CONFIG_LOCALVERSION="-chichu-ksu"
# CONFIG_LOCALVERSION_AUTO is not set
EOF
'

# 5) Build with the standing resource limits: 4 CPU, 8 GiB, no extra swap, low shares.
docker run --rm --cpus=4 --memory=8g --memory-swap=8g --cpu-shares=128 \
  -v "$PWD":/work -w /work cannon-ksu-build bash -c '
set -e
cd /work/src
make -j4 O=/work/out ARCH=arm64 CC=clang LD=ld.lld \
  CROSS_COMPILE=aarch64-linux-gnu- CROSS_COMPILE_ARM32=arm-linux-gnueabi- \
  CLANG_TRIPLE=aarch64-linux-gnu- \
  "CLANG_FLAGS=--target=aarch64-linux-gnu --prefix=/usr/bin/aarch64-linux-gnu- --gcc-toolchain=/usr -no-integrated-as -Werror=unknown-warning-option" \
  "KCFLAGS=-Wno-unused-but-set-variable -Wno-error=void-pointer-to-int-cast -fno-builtin-stpcpy" \
  olddefconfig
make -j4 O=/work/out ARCH=arm64 CC=clang LD=ld.lld \
  CROSS_COMPILE=aarch64-linux-gnu- CROSS_COMPILE_ARM32=arm-linux-gnueabi- \
  CLANG_TRIPLE=aarch64-linux-gnu- \
  "CLANG_FLAGS=--target=aarch64-linux-gnu --prefix=/usr/bin/aarch64-linux-gnu- --gcc-toolchain=/usr -no-integrated-as -Werror=unknown-warning-option" \
  "KCFLAGS=-Wno-unused-but-set-variable -Wno-error=void-pointer-to-int-cast -fno-builtin-stpcpy" \
  KSU_PACKAGE_NAME=com.chichu.toolbox \
  KSU_EXPECTED_SIZE=0x02e8 \
  KSU_EXPECTED_HASH=7ba84aa188e91b87bdc931e5a102d289e6f524a871c9294c6fd20821a1d11418 \
  Image.gz
sha256sum /work/out/arch/arm64/boot/Image.gz
'
echo "[cannon] build done"
