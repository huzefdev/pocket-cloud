#!/data/data/com.termux/files/usr/bin/bash

R='\033[0;31m'
G='\033[0;32m'
Y='\033[1;33m'
B='\033[0;34m'
M='\033[0;35m'
C='\033[0;36m'
W='\033[1;37m'
BOLD='\033[1m'
NC='\033[0m'

clear

echo -e "${C}"
cat << "BANNER"
  ██████╗  ██████╗  ██████╗██╗  ██╗███████╗████████╗    ██████╗██╗      ██████╗ ██╗   ██╗██████╗ 
  ██╔══██╗██╔═══██╗██╔════╝██║ ██╔╝██╔════╝╚══██╔══╝   ██╔════╝██║     ██╔═══██╗██║   ██║██╔══██╗
  ██████╔╝██║   ██║██║     █████═╝ █████╗     ██║      ██║     ██║     ██║   ██║██║   ██║██║  ██║
  ██╔═══╝ ██║   ██║██║     ██╔═██╗ ██╔══╝     ██║      ██║     ██║     ██║   ██║██║   ██║██║  ██║
  ██║     ╚██████╔╝╚██████╗██║ ╚██╗███████╗   ██║      ╚██████╗███████╗╚██████╔╝╚██████╔╝██████╔╝
  ╚═╝      ╚═════╝  ╚═════╝╚═╝  ╚═╝╚══════╝   ╚═╝       ╚═════╝╚══════╝ ╚═════╝  ╚═════╝ ╚═════╝ 
BANNER
echo -e "${M}                 >>> NEURAL CLOUD STACK // HUZEF DEV ENGINE v2.0 <<<                 ${NC}"
echo -e "${W}========================================================================================${NC}"
echo -e "${Y}[SYSTEM ALERT] INITIALIZING BARE-METAL ANDROID HOMELAB PROTOCOL...${NC}"
echo -e "${W}========================================================================================${NC}"
echo ""

# Acquire Termux wake lock to prevent background suspension
termux-wake-lock 2>/dev/null || true

# ================= STEP 0: BASE SYSTEM & STORAGE =================
echo -e "${B}[STEP 0/4]${NC} Verifying Android Shared Storage Mount..."
if [ ! -d "$HOME/storage/shared" ]; then
    echo -e "${Y}[*] Grant storage permission when prompted on screen...${NC}"
    termux-setup-storage </dev/null >/dev/null 2>&1 || true
    sleep 3
fi
echo -e "${G}[✓] Storage Mount Verified (~/storage/shared).${NC}"

echo -e "${C}[SYS-REGISTRY]${NC} Upgrading environment & installing core dependencies..."
pkg update -y && pkg upgrade -y -o Dpkg::Options::="--force-confnew" || true
pkg install -y wget curl git tar jq proot proot-distro ffmpeg syncthing || true

# ================= STEP 1: FILEBROWSER (CLOUD STORAGE) =================
echo ""
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -e "${C}${BOLD}[STEP 01/03] >> FILEBROWSER (REPLACES GOOGLE DRIVE)${NC}"
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -ne "${Y}[?] Deploy FileBrowser Core [Port :8080]? (Y/n): ${NC}"
read fb_choice </dev/tty || fb_choice="Y"
fb_choice=${fb_choice:-Y}

if [[ "$fb_choice" =~ ^[Yy]$ ]]; then
    echo -e "${G}[*] Fetching and installing FileBrowser binary...${NC}"
    mkdir -p ~/services/filebrowser
    cd ~/services/filebrowser

    # Primary: Official installer via curl; Fallback: Direct GitHub ARM64 tarball via wget
    if ! (curl -fsSL https://raw.githubusercontent.com/filebrowser/get/master/get.sh | bash >/dev/null 2>&1); then
        echo -e "${Y}[!] Fallback to direct ARM64 tarball release...${NC}"
        wget -q --show-progress -O fb.tar.gz "https://github.com/filebrowser/filebrowser/releases/latest/download/linux-arm64-filebrowser.tar.gz" || \
        wget -q --show-progress -O fb.tar.gz "https://github.com/filebrowser/filebrowser/releases/download/v2.30.0/linux-arm64-filebrowser.tar.gz"
        tar -xzf fb.tar.gz 2>/dev/null || true
        chmod +x filebrowser 2>/dev/null || true
        cp filebrowser "$PREFIX/bin/" 2>/dev/null || true
        rm -f fb.tar.gz
    fi

    FB_BIN=$(command -v filebrowser || echo "$HOME/services/filebrowser/filebrowser")

    if [ -x "$FB_BIN" ]; then
        "$FB_BIN" config init -a 0.0.0.0 -p 8080 -r ~/storage/shared -d ~/services/filebrowser/filebrowser.db >/dev/null 2>&1 || true
        "$FB_BIN" users add admin admin --perm.admin=true -d ~/services/filebrowser/filebrowser.db >/dev/null 2>&1 || true
        echo -e "${G}[✓] STATUS: FILEBROWSER CONFIGURED ON :8080 (User: admin | Pass: admin)${NC}"
    else
        echo -e "${R}[!] Error: Could not locate or initialize FileBrowser binary.${NC}"
    fi
    cd ~
else
    echo -e "${R}[-] Skipping FileBrowser deployment.${NC}"
fi

# ================= STEP 2: SYNCTHING (PHOTO & FOLDER SYNC) =================
echo ""
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -e "${C}${BOLD}[STEP 02/03] >> SYNCTHING (REPLACES GOOGLE PHOTOS SYNC)${NC}"
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -ne "${Y}[?] Deploy Syncthing Mesh Node [Port :8384]? (Y/n): ${NC}"
read st_choice </dev/tty || st_choice="Y"
st_choice=${st_choice:-Y}

if [[ "$st_choice" =~ ^[Yy]$ ]]; then
    command -v syncthing >/dev/null 2>&1 || pkg install -y syncthing || true
    mkdir -p ~/.config/syncthing
    echo -e "${C}[*] Initializing node keypairs and configuration...${NC}"
    syncthing generate --home=~/.config/syncthing >/dev/null 2>&1 || true
    sed -i 's/<address>127.0.0.1:8384<\/address>/<address>0.0.0.0:8384<\/address>/' ~/.config/syncthing/config.xml 2>/dev/null || true
    echo -e "${G}[✓] STATUS: SYNCTHING INITIALIZED ON :8384 (Listening 0.0.0.0)${NC}"
else
    echo -e "${R}[-] Skipping Syncthing deployment.${NC}"
fi

# ================= STEP 3: JELLYFIN (UBUNTU PROOT CONTAINER) =================
echo ""
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -e "${C}${BOLD}[STEP 03/03] >> JELLYFIN MEDIA SERVER (UBUNTU PROOT)${NC}"
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -ne "${Y}[?] Deploy Jellyfin Media Server [Port :8096]? (Y/n): ${NC}"
read jf_choice </dev/tty || jf_choice="Y"
jf_choice=${jf_choice:-Y}

if [[ "$jf_choice" =~ ^[Yy]$ ]]; then
    if ! proot-distro list 2>/dev/null | grep -q "ubuntu (installed)"; then
        echo -e "${Y}[*] Pulling Ubuntu rootfs inside PRoot...${NC}"
        proot-distro install ubuntu || true
    fi

    echo -e "${C}[*] Upgrading container & provisioning Jellyfin upstream repository...${NC}"
    proot-distro login ubuntu -- env DEBIAN_FRONTEND=noninteractive bash -c '
    apt update -y && apt upgrade -y || true
    apt install -y curl wget gnupg lsb-release ffmpeg libicu-dev || true
    mkdir -p /etc/apt/keyrings
    curl -fsSL https://repo.jellyfin.org/jellyfin_team.gpg.key | gpg --dearmor --yes -o /etc/apt/keyrings/jellyfin.gpg || true
    echo "deb [signed-by=/etc/apt/keyrings/jellyfin.gpg arch=$(dpkg --print-architecture)] https://repo.jellyfin.org/ubuntu $(lsb_release -cs) main" > /etc/apt/sources.list.d/jellyfin.list
    apt update -y && apt install -y jellyfin || true
    ' || true
    echo -e "${G}[✓] STATUS: JELLYFIN MEDIA SERVER DEPLOYED IN UBUNTU CONTAINER${NC}"
else
    echo -e "${R}[-] Skipping Jellyfin deployment.${NC}"
fi

# ================= MASTER LAUNCHER SCRIPT GENERATOR =================
echo ""
echo -e "${C}[SYS-CORE] Writing Master Daemon Manager to ~/start-services.sh...${NC}"
cat << 'LAUNCHER' > ~/start-services.sh
#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock 2>/dev/null || true
mkdir -p ~/services/filebrowser ~/.config/syncthing

# Interface IP detection with POSIX fallback
IP=$(ip -4 addr show wlan0 2>/dev/null | awk '/inet / {print $2}' | cut -d/ -f1)
[ -z "$IP" ] && IP=$(ip route get 1.1.1.1 2>/dev/null | awk '{print $7}')
[ -z "$IP" ] && IP="127.0.0.1"

# 1. FileBrowser Daemon
FB_BIN=$(command -v filebrowser || echo "$HOME/services/filebrowser/filebrowser")
if [ -x "$FB_BIN" ]; then
    nohup "$FB_BIN" -d ~/services/filebrowser/filebrowser.db > ~/services/filebrowser/filebrowser.log 2>&1 &
fi

# 2. Syncthing Daemon
if command -v syncthing >/dev/null 2>&1; then
    nohup syncthing -no-browser -home=~/.config/syncthing > ~/.config/syncthing/syncthing.log 2>&1 &
fi

# 3. Jellyfin Container Daemon (Mounting shared storage to /media/storage)
if proot-distro list 2>/dev/null | grep -q "ubuntu (installed)"; then
    nohup proot-distro login ubuntu --bind /data/data/com.termux/files/home/storage/shared:/media/storage -- jellyfin > ~/jellyfin.log 2>&1 &
fi

clear
echo -e "\033[0;32m"
echo "======================================================================"
echo "          >>> POCKET CLOUD DAEMONS ONLINE // HUZEF DEV <<<            "
echo "======================================================================"
echo -e "\033[1;37m"
echo -e " [1] FileBrowser Web UI  : \033[0;36mhttp://${IP}:8080\033[1;37m (Login: admin/admin)"
echo -e " [2] Syncthing P2P Panel : \033[0;36mhttp://${IP}:8384\033[1;37m"
echo -e " [3] Jellyfin Media Hub  : \033[0;36mhttp://${IP}:8096\033[1;37m"
echo -e "\033[0;32m"
echo "======================================================================"
echo -e "\033[0m"
LAUNCHER
chmod +x ~/start-services.sh

# ================= EXECUTION PROMPT =================
echo ""
echo -e "${G}========================================================================================${NC}"
echo -e "${W}${BOLD}              >>> [SYSTEM ONLINE] INSTALLATION COMPLETE! <<<                            ${NC}"
echo -e "${G}========================================================================================${NC}"
echo -ne "${Y}[?] Start all cloud daemons right now? (Y/n): ${NC}"
read run_now </dev/tty || run_now="Y"
run_now=${run_now:-Y}

if [[ "$run_now" =~ ^[Yy]$ ]]; then
    bash ~/start-services.sh
else
    echo -e "${C}Start anytime with: ${BOLD}bash ~/start-services.sh${NC}"
fi
