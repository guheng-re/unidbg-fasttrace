#!/system/bin/sh
# Walk /system in find/readdir order (depth <= 4) for Turing #18.
STAT_OUT=/data/local/tmp/system_stat2.txt
DIR_OUT=/data/local/tmp/system_dirs2.txt
rm -f "$STAT_OUT" "$DIR_OUT"
touch "$STAT_OUT" "$DIR_OUT"

find /system -maxdepth 4 2>/dev/null | while IFS= read -r p; do
  stat -c '%n|%s|%Y|%f|%u|%g' "$p" >> "$STAT_OUT" 2>/dev/null
done

find /system -maxdepth 3 -type d 2>/dev/null | while IFS= read -r d; do
  echo "===${d}===" >> "$DIR_OUT"
  if ls -f "$d" >/dev/null 2>&1; then
    ls -f "$d" 2>/dev/null | while IFS= read -r n; do
      [ "$n" = "." ] && continue
      [ "$n" = ".." ] && continue
      echo "$n"
    done >> "$DIR_OUT"
  else
    ls -1 "$d" 2>/dev/null >> "$DIR_OUT"
  fi
done

wc -l "$STAT_OUT" "$DIR_OUT"
echo DONE
