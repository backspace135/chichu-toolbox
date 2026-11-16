#!/system/bin/sh
# Cross-reboot lifecycle marker: executed by KernelSU at the post-fs-data stage.
MODDIR=${0%/*}
date >> "$MODDIR/post-fs-data-ran.txt"
