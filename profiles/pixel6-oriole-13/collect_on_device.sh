#!/system/bin/sh
# Run on device as root. Skip maps and /dev/urandom.
OUT=/data/local/tmp/pixel6_fill
rm -rf "$OUT"
mkdir -p "$OUT/proc/net" "$OUT/proc/sys/kernel/random" "$OUT/sys/class/power_supply/battery" \
         "$OUT/sys/class/thermal/thermal_zone0" "$OUT/sys/class/android_usb/android0" \
         "$OUT/system" "$OUT/sysfs_extra"

copy() {
  src="$1"
  dst="$2"
  if [ -e "$src" ]; then
    mkdir -p "$(dirname "$dst")"
    cat "$src" > "$dst" 2>/dev/null
  fi
}

copy /proc/loadavg "$OUT/proc/loadavg"
copy /proc/uptime "$OUT/proc/uptime"
copy /proc/mounts "$OUT/proc/mounts"
copy /proc/version "$OUT/proc/version"
copy /proc/cpuinfo "$OUT/proc/cpuinfo"
copy /proc/meminfo "$OUT/proc/meminfo"
copy /proc/stat "$OUT/proc/stat"
copy /proc/sys/kernel/random/boot_id "$OUT/proc/sys/kernel/random/boot_id"
copy /proc/sys/kernel/random/entropy_avail "$OUT/proc/sys/kernel/random/entropy_avail"
copy /proc/sys/kernel/random/poolsize "$OUT/proc/sys/kernel/random/poolsize"
copy /proc/sys/kernel/random/write_wakeup_threshold "$OUT/proc/sys/kernel/random/write_wakeup_threshold"
copy /proc/sys/kernel/hostname "$OUT/proc/sys/kernel/hostname"
copy /proc/sys/kernel/osrelease "$OUT/proc/sys/kernel/osrelease"
copy /proc/sys/kernel/ostype "$OUT/proc/sys/kernel/ostype"

# net tables — not maps
for f in tcp tcp6 udp udp6 route if_inet6 arp igmp igmp6 dev_mcast wireless unix; do
  copy /proc/net/$f "$OUT/proc/net/$f"
done
copy /proc/net/dev "$OUT/proc/net/dev"

copy /sys/class/power_supply/battery/charge_full_design "$OUT/sys/class/power_supply/battery/charge_full_design"
copy /sys/class/power_supply/battery/charge_full "$OUT/sys/class/power_supply/battery/charge_full"
copy /sys/class/power_supply/battery/capacity "$OUT/sys/class/power_supply/battery/capacity"
copy /sys/class/power_supply/battery/status "$OUT/sys/class/power_supply/battery/status"
copy /sys/class/power_supply/battery/current_now "$OUT/sys/class/power_supply/battery/current_now"
copy /sys/class/power_supply/battery/voltage_now "$OUT/sys/class/power_supply/battery/voltage_now"
copy /sys/class/power_supply/battery/temp "$OUT/sys/class/power_supply/battery/temp"
copy /sys/class/thermal/thermal_zone0/type "$OUT/sys/class/thermal/thermal_zone0/type"
copy /sys/class/thermal/thermal_zone0/temp "$OUT/sys/class/thermal/thermal_zone0/temp"
copy /sys/class/android_usb/android0/state "$OUT/sys/class/android_usb/android0/state"

od -An -tx1 /proc/1/auxv > "$OUT/proc/auxv.od" 2>/dev/null

df -k > "$OUT/system/df.txt" 2>/dev/null
ip route > "$OUT/system/ip_route.txt" 2>/dev/null
ip -6 route > "$OUT/system/ip6_route.txt" 2>/dev/null
uname -a > "$OUT/system/uname.txt" 2>/dev/null
getprop > "$OUT/system/getprop.txt" 2>/dev/null
ps -A -o PID,USER,NAME,CMD > "$OUT/system/ps.txt" 2>/dev/null
ls -l /system/fonts > "$OUT/system/fonts_ls.txt" 2>/dev/null

# statfs of key mounts
{
  for p in / /system /system_ext /product /vendor /data /storage/emulated/0 /metadata /mnt; do
    echo "===$p==="
    stat -f -c '%s %b %f %a %c %d %S' "$p" 2>/dev/null
  done
} > "$OUT/system/statfs.txt"

# /system tree + stat, no maps
find /system -maxdepth 4 \( -type d -o -type f -o -type l \) 2>/dev/null | head -n 800 > "$OUT/system/system_tree.txt"
{
  find /system -maxdepth 3 \( -type d -o -type f \) 2>/dev/null | head -n 500 | while read p; do
    stat -c '%n|%s|%Y|%f|%u|%g' "$p" 2>/dev/null
  done
} > "$OUT/system/system_stat.txt"

# font stats
{
  find /system/fonts -maxdepth 1 -type f 2>/dev/null | while read p; do
    stat -c '%n|%s|%Y|%f|%u|%g' "$p" 2>/dev/null
  done
} > "$OUT/system/fonts_stat.txt"

# extra sysfs that Turing/dfp often read
for p in \
  /sys/class/net/wlan0/address \
  /sys/class/net/wlan0/operstate \
  /sys/class/net/wlan0/mtu \
  /sys/class/net/wlan0/speed \
  /sys/devices/system/cpu/online \
  /sys/devices/system/cpu/possible \
  /sys/devices/system/cpu/present \
  /sys/block/sda/size \
  /sys/class/drm/card0/device/vendor
 do
  copy "$p" "$OUT/sysfs_extra$p"
done

# telephony parcels
service call iphonesubinfo 1 s16 com.android.phone > "$OUT/system/imei.txt" 2>/dev/null
service call iphonesubinfo 7 s16 com.android.phone > "$OUT/system/imsi.txt" 2>/dev/null
service call iphonesubinfo 11 s16 com.android.phone > "$OUT/system/iccid.txt" 2>/dev/null
settings get secure advertising_id > "$OUT/system/gaid.txt" 2>/dev/null
settings get secure android_id > "$OUT/system/android_id.txt" 2>/dev/null

echo DONE
