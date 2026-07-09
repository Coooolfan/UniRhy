#!/usr/bin/env bash
set -euo pipefail

# Tauri 的 DMG bundler 会把 app 图标当作卷图标，没有单独配置项（tauri-apps/tauri#9253）。
# 本脚本在打包后把挂载后的卷图标替换成硬盘造型的 dmg-volume.icns：
# 转成可写镜像 → 挂载 → 替换 .VolumeIcon.icns → 置自定义图标标记 → 重新压缩回只读。
# 只改动 DMG 卷层面的图标，不触碰 UniRhy.app，因此不影响 app 代码签名。
#
# 参数（环境变量）：
#   DMG_FILE   目标 DMG 路径
#   ICNS_FILE  卷图标 .icns 路径（默认 web/src-tauri/icons/dmg-volume.icns）

DMG_FILE="${DMG_FILE:?需要设置 DMG_FILE}"
ICNS_FILE="${ICNS_FILE:-web/src-tauri/icons/dmg-volume.icns}"

if [[ ! -f "$DMG_FILE" ]]; then
  echo "::error::DMG 文件不存在: $DMG_FILE"
  exit 1
fi
if [[ ! -f "$ICNS_FILE" ]]; then
  echo "::error::卷图标文件不存在: $ICNS_FILE"
  exit 1
fi

WORK="$(mktemp -d)"
RW_DMG="$WORK/rw.dmg"
MNT="$WORK/mnt"
mkdir -p "$MNT"

cleanup() {
  hdiutil detach "$MNT" >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

hdiutil convert "$DMG_FILE" -format UDRW -o "$RW_DMG" >/dev/null
hdiutil attach "$RW_DMG" -nobrowse -noverify -mountpoint "$MNT" >/dev/null
cp "$ICNS_FILE" "$MNT/.VolumeIcon.icns"
SetFile -a C "$MNT"
hdiutil detach "$MNT" >/dev/null
rm -f "$DMG_FILE"
hdiutil convert "$RW_DMG" -format UDZO -imagekey zlib-level=9 -o "$DMG_FILE" >/dev/null

echo "::notice::已写入自定义 DMG 卷图标: $DMG_FILE"
