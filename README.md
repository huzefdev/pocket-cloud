# Termux Pocket Cloud: Complete Self-Hosted NAS & Media Server
Turn any unrooted Android device into a complete, automated private cloud server running:
- **Jellyfin Media Server** (with FFmpeg transcoding)
- **FileBrowser** (Fast web-based file management, replacing Google Drive/Photos)
- **Syncthing** (P2P real-time backup and multi-device file synchronization)
---
## Architecture Overview

| Service | Protocol / Port | Purpose | Replacement For |
| :--- | :--- | :--- | :--- |
| **FileBrowser** | HTTP / `:8080` | Web UI for uploading, organizing, and streaming files | Google Drive |
| **Jellyfin** | HTTP / `:8096` | Full media streaming dashboard with metadata & transcoding | Netflix / Plex |
| **Syncthing** | Web UI / `:8384` | Automated P2P continuous camera & folder backup | Google Photos Auto-Sync |

---
## Prerequisites
1. Install **Termux** from F-Droid (do not use the deprecated Google Play Store build).
2. Install **Termux:API** and **Termux:Boot** (optional, for auto-start).
3. Disable Android Battery Optimization for Termux:
   - Settings -> Battery -> Termux -> Set to **Unrestricted**.
4. Disable Phantom Process Killer (if running Android 12+):
   - Developer Options -> Disable child process restrictions.
---
## One click installation 

# Pocket Cloud: Complete Self-Hosted Android Server
## ⚡ 1-Click Automated Setup

Run this single command inside Termux:

```bash
curl -sL https://raw.githubusercontent.com/huzefdev/pocket-cloud/main/install.sh | bash

```

🛠️ Manual Step-by-Step Installation
If you prefer to install services manually, follow these steps:
Step 1: Base Environment & Storage

pkg update && pkg upgrade -y
```bash
termux-setup-storage
pkg install -y wget curl git tar jq proot ffmpeg proot-distro syncthing

```
Step 2: FileBrowser Setup (Replaces Google Drive)
```bash
curl -fsSL [https://raw.githubusercontent.com/filebrowser/get/master/get.sh](https://raw.githubusercontent.com/filebrowser/get/master/get.sh) | bash
mkdir -p ~/services/filebrowser
filebrowser config init -a 0.0.0.0 -p 8080 -r ~/storage/shared -d ~/services/filebrowser/filebrowser.db
filebrowser users add admin admin --perm.admin=true -d ~/services/filebrowser/filebrowser.db
```
Step 3: Syncthing Setup (Replaces Google Photos Sync)
Termux:
```bash
   pkg install syncthing -y
```

 * Syncthing start:
```bash
    syncthing
```

Step 4: Jellyfin Media Server (via Ubuntu PRoot)
```bash
proot-distro install ubuntu
proot-distro login ubuntu
```

Inside Ubuntu Container Shell:
```bash
apt update && apt upgrade -y
apt install -y curl wget gnupg lsb-release ffmpeg libicu-dev
mkdir -p /etc/apt/keyrings
curl -fsSL [https://repo.jellyfin.org/jellyfin_team.gpg.key](https://repo.jellyfin.org/jellyfin_team.gpg.key) | gpg --dearmor -o /etc/apt/keyrings/jellyfin.gpg
echo "deb [signed-by=/etc/apt/keyrings/jellyfin.gpg arch=$(dpkg --print-architecture)] [https://repo.jellyfin.org/ubuntu](https://repo.jellyfin.org/ubuntu) $(lsb_release -cs) main" | tee /etc/apt/sources.list.d/jellyfin.list
apt update && apt install -y jellyfin

```


Accessing Your Dashboards
Open your browser from any device connected to the same Wi-Fi network:
Files: http://<PHONE-IP>:8080 (Default: admin / admin)
Sync: http://<PHONE-IP>:8384
Media: http://<PHONE-IP>:8096
(Find your phone's IP inside Termux using ifconfig wlan0)
