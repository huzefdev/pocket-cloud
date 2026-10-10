#!/data/data/com.termux/files/usr/bin/bash
set -e

# ================= ANSI COLOR CODES =================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
BOLD='\033[1m'
NC='\033[0m' # No Color

clear

# ================= ASCII CYBER BANNER =================
echo -e "${CYAN}"
cat << "BANNER"
  ____   ____   ____ _  _______ _____   ____ _     ___  _   _ ____  
 |  _ \ / __ \ / ___| |/ / ____|_   _| / ___| |   / _ \| | | |  _ \ 
 | |_) | |  | | |   | ' /|  _|   | |  | |   | |  | | | | | | | | | |
 |  __/| |__| | |___| . \| |___  | |  | |___| |__| |_| | |_| | |_| |
 |_|    \____/ \____|_|\_\_____| |_|   \____|_____\___/ \___/|____/ 
BANNER
echo -e "${PURPLE}       >> SYSTEM ARCHITECTURE: HUZEF DEV ENGINE v1.0 <<       ${NC}"
echo -e "${WHITE}------------------------------------------------------------${NC}"
echo -e "${YELLOW}[!] Initializing Pocket Cloud Deployment Protocol...${NC}"
echo -e "${WHITE}------------------------------------------------------------${NC}"
echo ""

# Prevent CPU deep sleep
termux-wake-lock

echo -e "${BLUE}[*] Requesting Android Storage Access Permissions...${NC}"
termux-setup-storage
echo -e "${YELLOW}[!] Check your phone screen and tap 'ALLOW'. Initializing storage links...${NC}"
sleep 4

# Core toolchain installation
echo -e "${CYAN}[+] Updating package registry and injecting essential binaries...${NC}"
pkg update -y && pkg install -y wget curl git tar jq proot ffmpeg proot-distro syncthing

# ----------------- 1. FILEBROWSER DEPLOYMENT -----------------
echo ""
echo -e "${WHITE}============================================================${NC}"
echo -e "${GREEN}[1/3] DEPLOYING FILEBROWSER (CLOUD STORAGE ENGINE)${NC}"
echo -e "${WHITE}============================================================${NC}"
curl -fsSL https://raw.githubusercontent.com/filebrowser/get/master/get.sh | bash

mkdir -p ~/services/filebrowser
echo -e "${CYAN}[*] Initializing local database and mounting /sdcard/shared...${NC}"
filebrowser config init -a 0.0.0.0 -p 8080 -r ~/storage/shared -d ~/services/filebrowser/filebrowser.db
filebrowser users add admin admin --perm.admin=true -d ~/services/filebrowser/filebrowser.db || true
echo -e "${GREEN}[✓] FileBrowser Engine Activated on port 8080!${NC}"

# ----------------- 2. SYNCTHING DEPLOYMENT -----------------
echo ""
echo -e "${WHITE}============================================================${NC}"
echo -e "${GREEN}[2/3] DEPLOYING SYNCTHING (PEER-TO-PEER DATA ENGINE)${NC}"
echo -e "${WHITE}============================================================${NC}"
echo -e "${CYAN}[*] Generating cryptographic identities and configs...${NC}"
syncthing generate --home=~/.config/syncthing || true
sed -i 's/<address>127.0.0.1:8384<\/address>/<address>0.0.0.0:8384<\/address>/' ~/.config/syncthing/config.xml || true
echo -e "${GREEN}[✓] Syncthing P2P Bridge Configured on port 8384!${NC}"

# ----------------- 3. JELLYFIN PROOT DEPLOYMENT -----------------
echo ""
echo -e "${WHITE}============================================================${NC}"
echo -e "${GREEN}[3/3] INJECTING JELLYFIN STREAMING CORE (UBUNTU PROOT)${NC}"
echo -e "${WHITE}============================================================${NC}"
if ! proot-distro list | grep -q "ubuntu (installed)"; then
    echo -e "${YELLOW}[*] Downloading container environment (Ubuntu Core)...${NC}"
    proot-distro install ubuntu
fi

echo -e "${CYAN}[*] Setting up hardware transcoding and media dependencies inside PRoot...${NC}"
proot-distro login ubuntu -- env DEBIAN_FRONTEND=noninteractive bash -c '
apt update -y && apt install -y curl wget gnupg lsb-release ffmpeg libicu-dev
mkdir -p /etc/apt/keyrings
curl -fsSL https://repo.jellyfin.org/jellyfin_team.gpg.key | gpg --dearmor --yes -o /etc/apt/keyrings/jellyfin.gpg
echo "deb [signed-by=/etc/apt/keyrings/jellyfin.gpg arch=$(dpkg --print-architecture)] https://repo.jellyfin.org/ubuntu $(lsb_release -cs) main" | tee /etc/apt/sources.list.d/jellyfin.list
apt update -y && apt install -y jellyfin
'
echo -e "${GREEN}[✓] Jellyfin Media Server Engine Successfully Compiled!${NC}"

# ----------------- 4. UNIFIED DAEMON LAUNCHER -----------------
echo ""
echo -e "${CYAN}[*] Compiling Master Launcher script at ~/start-services.sh...${NC}"
cat << 'EOF' > ~/start-services.sh
#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock
mkdir -p ~/services/filebrowser ~/.config/syncthing

nohup filebrowser -d ~/services/filebrowser/filebrowser.db > ~/services/filebrowser/filebrowser.log 2>&1 &
nohup syncthing -no-browser -home=~/.config/syncthing > ~/.config/syncthing/syncthing.log 2>&1 &
nohup proot-distro login ubuntu --bind /data/data/com.termux/files/home/storage/shared:/media/storage -- jellyfin > ~/jellyfin.log 2>&1 &

IP=$(ifconfig wlan0 2>/dev/null | grep 'inet ' | awk '{print $2}')
[ -z "$IP" ] && IP="127.0.0.1"

echo -e "\033[0;32m"
echo "=========================================================="
echo "         POCKET CLOUD SYSTEM ONLINE (HUZEF DEV)           "
echo "=========================================================="
echo -e "\033[1;37m"
echo -e " [1] FileBrowser  : \033[0;36mhttp://${IP}:8080\033[1;37m (admin/admin)"
echo -e " [2] Syncthing    : \033[0;36mhttp://${IP}:8384\033[1;37m"
echo -e " [3] Jellyfin     : \033[0;36mhttp://${IP}:8096\033[1;37m"
echo -e "\033[0;32m"
echo "=========================================================="
echo -e "\033[0m"
EOF
chmod +x ~/start-services.sh

# ================= FINAL COMPLETION MESSAGE =================
echo ""
echo -e "${GREEN}============================================================${NC}"
echo -e "${BOLD}${WHITE}    [✓] DEPLOYMENT COMPLETE — ARCHITECTURE READY TO RUN!    ${NC}"
echo -e "${GREEN}============================================================${NC}"
echo -e "${YELLOW}To ignite all background servers right now, execute:${NC}"
echo -e "${CYAN}${BOLD}bash ~/start-services.sh${NC}"
echo ""
