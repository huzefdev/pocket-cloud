cat << 'EOF' > install.sh
#!/data/data/com.termux/files/usr/bin/bash

# ================= CYBERPUNK COLOR MATRIX =================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
BOLD='\033[1m'
NC='\033[0m'

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
echo -e "${PURPLE}       >> CYBERPUNK HOMELAB MATRIX // HUZEF DEV ENGINE v1.0 <<       ${NC}"
echo -e "${WHITE}----------------------------------------------------------------------${NC}"
echo -e "${YELLOW}[!] SYSTEM READY: Transforming Device Into Bare-Metal Cloud Node${NC}"
echo -e "${WHITE}----------------------------------------------------------------------${NC}"
echo ""

prompt_step() {
    local prompt_text="$1"
    while true; do
        echo -ne "${BOLD}${CYAN}[?] ${prompt_text} ${YELLOW}[Y/N]: ${NC}"
        read -r choice
        case "$choice" in
            [Yy]* ) return 0 ;;
            [Nn]* ) return 1 ;;
            * ) echo -e "${RED}[!] Invalid response. Press Y for YES or N for NO.${NC}" ;;
        esac
    done
}

# Master confirmation
if ! prompt_step "Initialize Full Pocket Cloud Deployment Protocol?"; then
    echo -e "${RED}[X] Deployment aborted by operator.${NC}"
    exit 0
fi

# Prevent sleep & request base storage
termux-wake-lock
echo -e "${BLUE}[*] Requesting Android storage privileges...${NC}"
termux-setup-storage
sleep 3

echo -e "${CYAN}[+] Syncing package registry & installing core dependencies...${NC}"
pkg update -y && pkg install -y wget curl git tar jq proot proot-distro

# ================= STEP 1: FILEBROWSER =================
echo ""
echo -e "${WHITE}----------------------------------------------------------------------${NC}"
echo -e "${PURPLE}[NODE 01] MODULE: FILEBROWSER (CLOUD STORAGE ENGINE)${NC}"
echo -e "${WHITE}----------------------------------------------------------------------${NC}"

if prompt_step "Deploy FileBrowser Engine (Google Drive Replacement)?"; then
    echo -e "${CYAN}[*] Downloading FileBrowser binary payload...${NC}"
    curl -fsSL https://raw.githubusercontent.com/filebrowser/get/master/get.sh | bash
    
    mkdir -p ~/services/filebrowser
    echo -e "${CYAN}[*] Provisioning local database & mounting /sdcard/shared...${NC}"
    filebrowser config init -a 0.0.0.0 -p 8080 -r ~/storage/shared -d ~/services/filebrowser/filebrowser.db
    filebrowser users add admin admin --perm.admin=true -d ~/services/filebrowser/filebrowser.db || true
    echo -e "${GREEN}[✓] FileBrowser Online -> Port 8080 (admin/admin)${NC}"
else
    echo -e "${YELLOW}[-] Skipping FileBrowser deployment.${NC}"
fi

# ================= STEP 2: JELLYFIN =================
echo ""
echo -e "${WHITE}----------------------------------------------------------------------${NC}"
echo -e "${PURPLE}[NODE 02] MODULE: JELLYFIN (MEDIA STREAMING & FFMPEG HARDWARE TRANSCODE)${NC}"
echo -e "${WHITE}----------------------------------------------------------------------${NC}"

if prompt_step "Deploy Jellyfin Streaming Core inside Ubuntu PRoot?"; then
    if ! proot-distro list | grep -q "ubuntu (installed)"; then
        echo -e "${YELLOW}[*] Downloading Ubuntu PRoot isolated container...${NC}"
        proot-distro install ubuntu
    fi

    echo -e "${CYAN}[*] Provisioning Jellyfin, FFmpeg, and dependencies inside container...${NC}"
    proot-distro login ubuntu -- env DEBIAN_FRONTEND=noninteractive bash -c '
    apt update -y && apt install -y curl wget gnupg lsb-release ffmpeg libicu-dev
    mkdir -p /etc/apt/keyrings
    curl -fsSL https://repo.jellyfin.org/jellyfin_team.gpg.key | gpg --dearmor --yes -o /etc/apt/keyrings/jellyfin.gpg
    echo "deb [signed-by=/etc/apt/keyrings/jellyfin.gpg arch=$(dpkg --print-architecture)] https://repo.jellyfin.org/ubuntu $(lsb_release -cs) main" | tee /etc/apt/sources.list.d/jellyfin.list
    apt update -y && apt install -y jellyfin
    mkdir -p /media/storage
    '
    echo -e "${GREEN}[✓] Jellyfin Streaming Core Online -> Port 8096${NC}"
else
    echo -e "${YELLOW}[-] Skipping Jellyfin deployment.${NC}"
fi

# ================= STEP 3: SYNCTHING =================
echo ""
echo -e "${WHITE}----------------------------------------------------------------------${NC}"
echo -e "${PURPLE}[NODE 03] MODULE: SYNCTHING (PEER-TO-PEER BACKUP & DATA MESH)${NC}"
echo -e "${WHITE}----------------------------------------------------------------------${NC}"

if prompt_step "Deploy Syncthing Engine (Google Photos Auto-Sync Replacement)?"; then
    echo -e "${CYAN}[*] Installing Syncthing binary package...${NC}"
    pkg install -y syncthing
    
    echo -e "${CYAN}[*] Generating cryptographic keys and configuring LAN bridge...${NC}"
    syncthing generate --home=~/.config/syncthing || true
    sed -i 's/<address>127.0.0.1:8384<\/address>/<address>0.0.0.0:8384<\/address>/' ~/.config/syncthing/config.xml || true
    echo -e "${GREEN}[✓] Syncthing P2P Node Online -> Port 8384${NC}"
else
    echo -e "${YELLOW}[-] Skipping Syncthing deployment.${NC}"
fi

# ================= COMPILE MASTER DAEMON LAUNCHER =================
echo ""
echo -e "${CYAN}[*] Generating Master Launcher daemon at ~/start-services.sh...${NC}"
cat << 'LAUNCHER' > ~/start-services.sh
#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock
mkdir -p ~/services/filebrowser ~/.config/syncthing

# Launch deployed services
command -v filebrowser >/dev/null 2>&1 && nohup filebrowser -d ~/services/filebrowser/filebrowser.db > ~/services/filebrowser/filebrowser.log 2>&1 &
command -v syncthing >/dev/null 2>&1 && nohup syncthing -no-browser -home=~/.config/syncthing > ~/.config/syncthing/syncthing.log 2>&1 &
if proot-distro list 2>/dev/null | grep -q "ubuntu (installed)"; then
    nohup proot-distro login ubuntu --bind /data/data/com.termux/files/home/storage/shared:/media/storage -- jellyfin > ~/jellyfin.log 2>&1 &
fi

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
LAUNCHER
chmod +x ~/start-services.sh

# ================= FINALIZE =================
echo ""
echo -e "${GREEN}======================================================================${NC}"
echo -e "${BOLD}${WHITE}       [✓] DEPLOYMENT PROTOCOL COMPLETE // ARCHITECTURE ACTIVE        ${NC}"
echo -e "${GREEN}======================================================================${NC}"
echo ""

if prompt_step "Ignite all background server daemons right now?"; then
    bash ~/start-services.sh
else
    echo -e "${YELLOW}You can start the server anytime with: ${CYAN}bash ~/start-services.sh${NC}"
fi
EOF
chmod +x install.sh
bash install.sh
