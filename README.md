<div align="center">

# POCKET CLOUD
### Turn Any Android Device Into a Zero-Termux Private Cloud & Media Server

[![GitHub Release](https://img.shields.io/github/v/release/huzefdev/pocket-cloud?style=for-the-badge&color=EA580C&label=LATEST%20APK)](https://github.com/huzefdev/pocket-cloud/releases/latest)
[![Platform](https://img.shields.io/badge/PLATFORM-ANDROID%208.0%2B%20ARM64-10B981?style=for-the-badge)](https://github.com/huzefdev/pocket-cloud)
[![Stack](https://img.shields.io/badge/STACK-NATIVE%20JAVA%208%20%2B%20GO-0B0B0D?style=for-the-badge)](https://github.com/huzefdev/pocket-cloud)

**Replace Google Drive, Google Photos, and Plex/Netflix with a self-hosted bare-metal server running locally on your phone.**

[Download Latest APK](https://github.com/huzefdev/pocket-cloud/releases/latest) | [Discord Support](https://discord.gg/GcGkW5fvDh) | [Report Issue](https://github.com/huzefdev/pocket-cloud/issues)

</div>

---

## Architecture Overview

PocketCloud eliminates terminal setup, PRoot containers, and multi-tab browser juggling by embedding standalone native ARM64 daemons and an in-app glassmorphic workspace directly inside a lightweight Android APK.

| Service Module | Local / LAN Port | Engine Core | Replaces |
| :--- | :---: | :--- | :--- |
| **FileBrowser Vault** | `:8080` | Bundled Native ARM64 Binary + Guided Password Wizard | Google Drive |
| **PocketMedia Server** | `:8096` | Zero-Dependency HTTP Range Streaming Engine | Netflix / Plex |
| **Syncthing Mesh** | `:8384` | Bundled P2P Sync Node (`0.0.0.0` LAN Ready) | Google Photos Sync |

---

## Mode 1: Standalone Android App (Recommended - Zero Termux)

1. Download **PocketCloud-v0.xx.apk** from [Releases](https://github.com/huzefdev/pocket-cloud/releases/latest).
2. Install the APK and grant **All Files Access** so your local storage can be served across your Wi-Fi network.
3. Tap **Get Started / Boot Engine** on the Node dashboard.
4. Open **FileBrowser (:8080)** inside the app to complete the **1-Step Vault Password Wizard** with automatic login.
5. Connect from any laptop, PC, or Smart TV on the same Wi-Fi using your phone's displayed LAN IP (e.g., `http://192.168.1.x:8080`).

---

## Mode 2: Termux Bare-Metal CLI Stack (Advanced Users)

Prefer running the full Linux/PRoot stack directly inside Termux? Run the automated deployment script:

    curl -sL https://raw.githubusercontent.com/huzefdev/pocket-cloud/main/scripts/install.sh | bash

---

## Repository Structure

    pocket-cloud/
    ├── app/
    │   ├── AndroidManifest.xml       # Target SDK 34 Foreground Service & Workspace Declarations
    │   ├── res/                      # Standard Android XML Resources
    │   └── src/dev/huzef/pocketcloud/
    │       ├── core/                 # GlassTheme, HardwareTelemetry, VaultConfigManager
    │       ├── model/                # ServiceNode & TelemetrySnapshot Models
    │       ├── server/               # Embedded HTTP Range Media Server (:8096)
    │       ├── service/              # Foreground Daemon & WakeLock Orchestrator
    │       └── ui/                   # ParticleSphereView, Bento Dashboard & WebWorkspace
    ├── scripts/
    │   └── install.sh                # Automated Termux Bare-Metal Installer
    └── README.md

---

## Community & Support

- **Developer:** Shaikh Huzef Hanif (`huzef.dev@gmail.com`)
- **Discord Server:** https://discord.gg/GcGkW5fvDh
