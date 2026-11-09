#!/system/bin/sh
MODDIR=${0%/*}
id
printf 'action-ok\n' > "$MODDIR/action-result.txt"
printf 'CHICHU_ACTION_OK\n'
