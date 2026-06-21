# Bambu Printer LAN

A native **Android** app to slice, control, and monitor 3D printers over your **LAN**
(or an optional relay) — no cloud required. The whole UI is **bilingual** (Cantonese
粵語 + English, shown together).

一個原生 **Android** App，透過你嘅 **區域網（LAN）**（或可選嘅中繼）切片、控制同監察
3D 打印機 — 唔需要雲端。成個介面都係 **雙語**（粵語 + English，一齊顯示）。

> Built on the PrusaSlicer/Slic3r/BambuStudio lineage (AGPL-3.0). See [`NOTICE`](NOTICE).
> 基於 PrusaSlicer/Slic3r/BambuStudio（AGPL-3.0）。詳見 [`NOTICE`](NOTICE)。

## Features / 功能
- **Prepare 準備** — import STL/OBJ/3MF/STEP/AMF (multi-file), workspace with auto-save, and a
  **native STL slicer** that produces real G-code on-device.
  匯入模型（可多檔）、有自動儲存嘅工作區，同埋喺裝置上產生真 G-code 嘅 **原生 STL 切片引擎**。
- **Device 裝置** — connect **LAN-direct** (TLS MQTT 8883 + access code) or via the relay; multiple
  printers, **QR quick-add**, live status, controls (pause/resume/stop, light, speed), AMS, HMS,
  and **print-send** (FTPS upload + start).
  **LAN 直連**（TLS MQTT 8883 + 存取碼）或經中繼；支援多打印機、**掃 QR 快速加入**、即時狀態、
  控制（暫停／繼續／停止、燈、速度）、AMS、HMS，同 **傳送列印**（FTPS 上載 + 開始）。
- **Tools / Labs 工具** — Batch Printer Sender, Material Fidget Lab, and **AI Labs** (Feature
  Suggestion, AI Filament, Sidechat, Community Miner, Auto-Fix) using your own Anthropic API key.
  批次列印傳送、Material 玩具實驗室，同用你自己 Anthropic API key 嘅 **AI 實驗室**。
- **Settings 設定** — relay / Home Assistant / Discord config, configurable clock, settings bundle.
  中繼／Home Assistant／Discord 設定、可調時鐘、設定套件匯入匯出。
- **Relay 中繼** (optional, `relay/`) — a Dockerized bridge that multiplexes one printer
  connection to many app clients and enables remote access without the cloud.
  可選嘅 Docker 中繼：將一個打印機連線分發畀多個 App，亦可唔經雲端遠端連線。

No secrets are committed — access codes, tokens, and API keys are entered at runtime and stored
on-device; the signing keystore is generated locally and gitignored.
唔會提交任何機密 — 存取碼、權杖、API key 都係執行時輸入並存喺裝置；簽署金鑰本機產生並已 gitignore。

## Build a signed APK — one command / 一鍵建置已簽署 APK

**Windows** (double-click or run / 雙擊或執行):
```bat
build-signed-apk.bat
```
**Linux / macOS**:
```bash
./build-signed-apk.sh
```
Both work on a clean machine: they bootstrap a JDK 17, the Android SDK + NDK + CMake, and Gradle,
generate a self-signed keystore (gitignored), and build.
兩個喺乾淨機器都用得：自動裝 JDK 17、Android SDK + NDK + CMake 同 Gradle，產生自簽金鑰，然後建置。

Output / 輸出: `app/build/outputs/apk/release/app-release.apk`. Or open the project in **Android
Studio** and Run / 或者用 **Android Studio** 開啟然後 Run。

## Run the relay (optional) — `docker compose up` / 執行中繼（可選）
```bash
cd relay
cp .env.example .env        # set PRINTER_SERIAL / PRINTER_IP / PRINTER_ACCESS_CODE
                            # 填打印機序號／IP／存取碼
                            # RELAY_TOKEN is optional — leave empty for a LAN-only relay
                            # RELAY_TOKEN 可留空（純 LAN 唔使驗證）
docker compose up -d --build
curl -s localhost:8979/healthz
```
Then in the app: **Settings → Relay URL** = `http://<host>:8979`, then **Device → Relay printers →
Refresh** (or **Add to relay** / scan a QR). On Linux, uncomment `network_mode: host` in
`relay/docker-compose.yml` for LAN auto-discovery; otherwise the printer IP is used. Don't expose
port 8979 publicly — use a private tunnel (Tailscale/WireGuard).

之後喺 App：**設定 → 中繼網址** = `http://<host>:8979`，再去 **裝置 → 中繼打印機 → 重新整理**
（或 **加入中繼**／掃 QR）。Linux 想自動探索就喺 `relay/docker-compose.yml` 開 `network_mode: host`；
否則用填咗嘅打印機 IP。唔好將 8979 開放上公網 — 用私人通道（Tailscale/WireGuard）。

## License / 授權
**AGPL-3.0-or-later** — see [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).
