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
## Step-by-Step Installation
### Step 1: Base Environment & Storage Setup
Open Termux and execute:
```bash
# Update base repositories
pkg update && pkg upgrade -y
# Grant storage access permission to Termux
termux-setup-storage
# Install core utilities, build essentials, and network tools
pkg install -y wget curl git tar jq proot ffmpeg
