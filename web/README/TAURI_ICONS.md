# Tauri Icons

`web/src-tauri/AppIcon.icon` is the Apple Icon Composer source for the macOS app icon. It defines the icon background fill, separate foreground groups, and dark/tinted appearance specializations.

Tauri currently consumes flattened icon files for desktop bundles, so `web/src-tauri/app-icon.png` is the exported Default rendition used by `tauri icon` to generate `web/src-tauri/icons`.

`web/src-tauri/dmg-volume-icon.svg` is the source for the DMG file and mounted volume icon. It is intentionally separate from the app icon so the installer image can use a disk-shaped metaphor while `UniRhy.app` keeps the branded app icon.

Regenerate the Tauri icon outputs from the Icon Composer source on macOS:

```sh
cd web
"/Applications/Xcode.app/Contents/Applications/Icon Composer.app/Contents/Executables/ictool" \
  src-tauri/AppIcon.icon \
  --export-image \
  --output-file src-tauri/app-icon.png \
  --platform macOS \
  --rendition Default \
  --width 1024 \
  --height 1024 \
  --scale 1
./node_modules/.bin/tauri icon src-tauri/icon-manifest.json
```

Regenerate the DMG/volume icon from the SVG source:

```sh
cd web
work_dir="$(mktemp -d)"
iconset="$work_dir/dmg-volume.iconset"
base_png="$work_dir/dmg-volume-1024.png"
mkdir -p "$iconset"
sips -s format png -z 1024 1024 src-tauri/dmg-volume-icon.svg --out "$base_png"
sips -z 16 16 "$base_png" --out "$iconset/icon_16x16.png"
sips -z 32 32 "$base_png" --out "$iconset/icon_16x16@2x.png"
sips -z 32 32 "$base_png" --out "$iconset/icon_32x32.png"
sips -z 64 64 "$base_png" --out "$iconset/icon_32x32@2x.png"
sips -z 128 128 "$base_png" --out "$iconset/icon_128x128.png"
sips -z 256 256 "$base_png" --out "$iconset/icon_128x128@2x.png"
sips -z 256 256 "$base_png" --out "$iconset/icon_256x256.png"
sips -z 512 512 "$base_png" --out "$iconset/icon_256x256@2x.png"
sips -z 512 512 "$base_png" --out "$iconset/icon_512x512.png"
sips -z 1024 1024 "$base_png" --out "$iconset/icon_512x512@2x.png"
iconutil -c icns "$iconset" -o src-tauri/icons/dmg-volume.icns
```
