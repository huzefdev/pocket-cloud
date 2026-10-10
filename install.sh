cat << 'EOF' > install.sh
#!/data/data/com.termux/files/usr/bin/bash
set -e

# ================= CYBERPUNK COLOR PALETTE =================
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

# ================= CYBER ARTWORK BANNER =================
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

termux-wake-lock

echo -e "${B}[NODE-INIT]${NC} Authenticating Android Internal Storage mount..."
termux-setup-storage
echo -e "${Y}[ACTION REQUIRED]${NC} Tap 'ALLOW' on your screen if permission popup appears."
sleep 2

echo -e "${C}[SYS-REGISTRY]${NC} Injecting compiler tools, network bridges, and PRoot subsystem..."
pkg update -y && pkg install -y wget curl git tar jq proot proot-distro syncthing

# ================= INTERACTIVE STEP 1: FILEBROWSER =================
echo ""
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -e "${C}${BOLD}[MODULE 01/03] >> FILEBROWSER (ENCRYPTED FILE MANAGEMENT CORE)${NC}"
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -ne "${Y}[?] Do you want to deploy FileBrowser Core [Port :8080]? (Y/n): ${NC}"
read fb_choice </dev/tty || fb_choice="Y"
fb_choice=${fb_choice:-Y}

if [[ "$fb_choice" =~ ^[Yy]$ ]]; then
    echo -e "${G}[*] Pulling latest production binary for ARM64...${NC}"
    curl -fsSL https://raw.githubusercontent.com/filebrowser/get/master/get.sh | bash
    mkdir -p ~/services/filebrowser
    echo -e "${C}[*] Binding SQLite database to internal storage (/sdcard)...${NC}"
    filebrowser config init -a 0.0.0.0 -p 8080 -r ~/storage/shared -d ~/services/filebrowser/filebrowser.db
    filebrowser users add admin admin --perm.admin=true -d ~/services/filebrowser/filebrowser.db || true
    echo -e "${G}[✓] STATUS: FILEBROWSER DEPLOYED SUCCESSFULLY ON :8080${NC}"
else
    echo -e "${R}[-] SKIPPING FILEBROWSER DEPLOYMENT.${NC}"
fi

# ================= INTERACTIVE STEP 2: JELLYFIN =================
echo ""
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -e "${C}${BOLD}[MODULE 02/03] >> JELLYFIN SERVER (UBUNTU PROOT + FULL FFMPEG TRANSCODER)${NC}"
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -ne "${Y}[?] Do you want to deploy Jellyfin Media Server [Port :8096]? (Y/n): ${NC}"
read jf_choice </dev/tty || jf_choice="Y"
jf_choice=${jf_choice:-Y}

if [[ "$jf_choice" =~ ^[Yy]$ ]]; then
    if ! proot-distro list | grep -q "ubuntu (installed)"; then
        echo -e "${Y}[*] Injecting clean Ubuntu container architecture...${NC}"
        proot-distro install ubuntu
    fi

    echo -e "${C}[*] Compiling hardware FFmpeg codecs and installing Jellyfin daemon inside container...${NC}"
    proot-distro login ubuntu -- env DEBIAN_FRONTEND=noninteractive bash -c '
    apt update -y && apt install -y curl wget gnupg lsb-release ffmpeg libicu-dev
    mkdir -p /etc/apt/keyrings
    curl -fsSL https://repo.jellyfin.org/jellyfin_team.gpg.key | gpg --dearmor --yes -o /etc/apt/keyrings/jellyfin.gpg
    echo "deb [signed-by=/etc/apt/keyrings/jellyfin.gpg arch=$(dpkg --print-architecture)] https://repo.jellyfin.org/ubuntu $(lsb_release -cs) main" | tee /etc/apt/sources.list.d/jellyfin.list
    apt update -y && apt install -y jellyfin
    '
    echo -e "${G}[✓] STATUS: JELLYFIN MEDIA DAEMON COMPILED ON :8096${NC}"
else
    echo -e "${R}[-] SKIPPING JELLYFIN DEPLOYMENT.${NC}"
fi

# ================= INTERACTIVE STEP 3: SYNCTHING =================
echo ""
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -e "${C}${BOLD}[MODULE 03/03] >> SYNCTHING (PEER-TO-PEER ZERO-TRUST MESH ENGINE)${NC}"
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -ne "${Y}[?] Do you want to deploy Syncthing Mesh Node [Port :8384]? (Y/n): ${NC}"
read st_choice </dev/tty || st_choice="Y"
st_choice=${st_choice:-Y}

if [[ "$st_choice" =~ ^[Yy]$ ]]; then
    echo -e "${C}[*] Generating TLS certs and binding interface to 0.0.0.0...${NC}"
    syncthing generate --home=~/.config/syncthing || true
    sed -i 's/<address>127.0.0.1:8384<\/address>/<address>0.0.0.0:8384<\/address>/' ~/.config/syncthing/config.xml || true
    echo -e "${G}[✓] STATUS: SYNCTHING P2P ENGINE ACTIVATED ON :8384${NC}"
else
    echo -e "${R}[-] SKIPPING SYNCTHING DEPLOYMENT.${NC}"
fi

# ================= UNIFIED DAEMON LAUNCHER GENERATOR =================
echo ""
echo -e "${C}[SYS-CORE] Building daemon controller script at ~/start-services.sh...${NC}"
cat << 'LAUNCHER' > ~/start-services.sh
#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock
mkdir -p ~/services/filebrowser ~/.config/syncthing

[ -f ~/services/filebrowser/filebrowser.db ] && nohup filebrowser -d ~/services/filebrowser/filebrowser.db > ~/services/filebrowser/filebrowser.log 2>&1 &
command -v syncthing >/dev/null 2>&1 && nohup syncthing -no-browser -home=~/.config/syncthing > ~/.config/syncthing/syncthing.log 2>&1 &
proot-distro list 2>/dev/null | grep -q "ubuntu (installed)" && nohup proot-distro login ubuntu --bind /data/data/com.termux/files/home/storage/shared:/media/storage -- jellyfin > ~/jellyfin.log 2>&1 &

IP=$(ifconfig wlan0 2>/dev/null | grep 'inet ' | awk '{print $2}')
[ -z "$IP" ] && IP="127.0.0.1"

clear
echo -e "\033[0;32m"
echo "======================================================================"
echo "          >>> POCKET CLOUD DAEMONS ONLINE // HUZEF DEV <<<            "
echo "======================================================================"
echo -e "\033[1;37m"
echo -e " [1] FileBrowser Web UI  : \033[0;36mhttp://${IP}:8080\033[1;37m (Login: admin/admin)"
echo -e " [2] Jellyfin Media Hub  : \033[0;36mhttp://${IP}:8096\033[1;37m"
echo -e " [3] Syncthing P2P Panel : \033[0;36mhttp://${IP}:8384\033[1;37m"
echo -e "\033[0;32m"
echo "======================================================================"
echo -e "\033[0m"
LAUNCHER
chmod +x ~/start-services.sh

# ================= FINAL COMPLETION PROMPT =================
echo ""
echo -e "${G}========================================================================================${NC}"
echo -e "${W}${BOLD}              >>> [SYSTEM ONLINE] ALL SELECTED CORES DEPLOYED! <<<                      ${NC}"
echo -e "${G}========================================================================================${NC}"
echo -ne "${Y}[?] Do you want to launch all cloud servers right now? (Y/n): ${NC}"
read launch_choice </dev/tty || launch_choice="Y"
launch_choice=${launch_choice:-Y}

if [[ "$launch_choice" =~ ^[Yy]$ ]]; then
    bash ~/start-services.sh
else
    echo -e "${C}To start the servers anytime, run: ${BOLD}bash ~/start-services.sh${NC}"
fi
EOF
chmod +x install.sh
bash install.sh
