#!/system/bin/sh
# Cross-reboot lifecycle marker: executed by KernelSU at the service stage.
MODDIR=${0%/*}
n=$(cat "$MODDIR/boot-count" 2>/dev/null || echo 0)
n=$((n + 1))
printf '%s\n' "$n" > "$MODDIR/boot-count"
{ date; id; } >> "$MODDIR/service-ran.txt"
