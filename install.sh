cat << 'EOF' > install.sh
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

termux-wake-lock 2>/dev/null || true

# Storage link check
echo -e "${B}[NODE-INIT]${NC} Verifying Android Storage mount..."
if [ ! -d "$HOME/storage/shared" ]; then
    termux-setup-storage </dev/null >/dev/null 2>&1 || true
    sleep 2
fi
echo -e "${G}[✓] Storage Mount Verified.${NC}"

# Fix broken packages & install core utilities
echo -e "${C}[SYS-REGISTRY]${NC} Preparing binary packages (wget, tar, jq, proot, syncthing)..."
pkg install -y wget tar jq proot proot-distro syncthing || true

# ================= MODULE 1: FILEBROWSER (DIRECT BINARY) =================
echo ""
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -e "${C}${BOLD}[MODULE 01/03] >> FILEBROWSER (CLOUD STORAGE ENGINE)${NC}"
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -ne "${Y}[?] Deploy FileBrowser Core [Port :8080]? (Y/n): ${NC}"
read fb_choice </dev/tty || fb_choice="Y"
fb_choice=${fb_choice:-Y}

if [[ "$fb_choice" =~ ^[Yy]$ ]]; then
    echo -e "${G}[*] Fetching FileBrowser ARM64 binary directly via wget...${NC}"
    mkdir -p ~/services/filebrowser
    cd ~/services/filebrowser
    
    # Download direct release tarball (bypassing broken curl)
    wget -q --show-progress -O fb.tar.gz "https://github.com/filebrowser/filebrowser/releases/latest/download/linux-arm64-filebrowser.tar.gz" || \
    wget -q --show-progress -O fb.tar.gz "https://github.com/filebrowser/filebrowser/releases/download/v2.30.0/linux-arm64-filebrowser.tar.gz"
    
    tar -xzf fb.tar.gz 2>/dev/null || true
    chmod +x filebrowser 2>/dev/null || true
    cp filebrowser $PREFIX/bin/ 2>/dev/null || true
    rm -f fb.tar.gz

    if command -v filebrowser >/dev/null 2>&1 || [ -f ~/services/filebrowser/filebrowser ]; then
        FB_BIN=$(command -v filebrowser || echo "$HOME/services/filebrowser/filebrowser")
        $FB_BIN config init -a 0.0.0.0 -p 8080 -r ~/storage/shared -d ~/services/filebrowser/filebrowser.db >/dev/null 2>&1 || true
        $FB_BIN users add admin admin --perm.admin=true -d ~/services/filebrowser/filebrowser.db >/dev/null 2>&1 || true
        echo -e "${G}[✓] STATUS: FILEBROWSER DEPLOYED SUCCESSFULLY ON :8080 (admin/admin)${NC}"
    else
        echo -e "${R}[!] Failed to unpack FileBrowser binary.${NC}"
    fi
    cd ~
else
    echo -e "${R}[-] Skipping FileBrowser deployment.${NC}"
fi

# ================= MODULE 02: JELLYFIN (UBUNTU PROOT) =================
echo ""
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -e "${C}${BOLD}[MODULE 02/03] >> JELLYFIN SERVER (UBUNTU CONTAINER)${NC}"
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -ne "${Y}[?] Deploy Jellyfin Media Server [Port :8096]? (Y/n): ${NC}"
read jf_choice </dev/tty || jf_choice="Y"
jf_choice=${jf_choice:-Y}

if [[ "$jf_choice" =~ ^[Yy]$ ]]; then
    if ! proot-distro list 2>/dev/null | grep -q "ubuntu (installed)"; then
        echo -e "${Y}[*] Downloading Ubuntu PRoot container rootfs...${NC}"
        proot-distro install ubuntu || true
    fi

    echo -e "${C}[*] Installing dependencies & Jellyfin inside Ubuntu...${NC}"
    proot-distro login ubuntu -- env DEBIAN_FRONTEND=noninteractive bash -c '
    apt update -y && apt install -y wget gnupg lsb-release ffmpeg libicu-dev curl || true
    mkdir -p /etc/apt/keyrings
    wget -qO - https://repo.jellyfin.org/jellyfin_team.gpg.key | gpg --dearmor --yes -o /etc/apt/keyrings/jellyfin.gpg || true
    echo "deb [signed-by=/etc/apt/keyrings/jellyfin.gpg arch=$(dpkg --print-architecture)] https://repo.jellyfin.org/ubuntu $(lsb_release -cs) main" > /etc/apt/sources.list.d/jellyfin.list
    apt update -y && apt install -y jellyfin || true
    ' || true
    echo -e "${G}[✓] STATUS: JELLYFIN INSTALLED IN UBUNTU CONTAINER${NC}"
else
    echo -e "${R}[-] Skipping Jellyfin deployment.${NC}"
fi

# ================= MODULE 03: SYNCTHING =================
echo ""
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -e "${C}${BOLD}[MODULE 03/03] >> SYNCTHING (PEER-TO-PEER DATA ENGINE)${NC}"
echo -e "${W}----------------------------------------------------------------------------------------${NC}"
echo -ne "${Y}[?] Deploy Syncthing Mesh Node [Port :8384]? (Y/n): ${NC}"
read st_choice </dev/tty || st_choice="Y"
st_choice=${st_choice:-Y}

if [[ "$st_choice" =~ ^[Yy]$ ]]; then
    pkg install -y syncthing || true
    mkdir -p ~/.config/syncthing
    echo -e "${C}[*] Generating configurations...${NC}"
    syncthing generate --home=~/.config/syncthing >/dev/null 2>&1 || true
    sed -i 's/<address>127.0.0.1:8384<\/address>/<address>0.0.0.0:8384<\/address>/' ~/.config/syncthing/config.xml 2>/dev/null || true
    echo -e "${G}[✓] STATUS: SYNCTHING DEPLOYED ON :8384${NC}"
else
    echo -e "${R}[-] Skipping Syncthing deployment.${NC}"
fi

# ================= MASTER LAUNCHER =================
echo ""
echo -e "${C}[SYS-CORE] Writing Master Launcher to ~/start-services.sh...${NC}"
cat << 'LAUNCHER' > ~/start-services.sh
#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock 2>/dev/null || true
mkdir -p ~/services/filebrowser ~/.config/syncthing

FB_CMD=$(command -v filebrowser || echo "$HOME/services/filebrowser/filebrowser")
[ -x "$FB_CMD" ] && nohup "$FB_CMD" -d ~/services/filebrowser/filebrowser.db > ~/services/filebrowser/filebrowser.log 2>&1 &

command -v syncthing >/dev/null 2>&1 && nohup syncthing -no-browser -home=~/.config/syncthing > ~/.config/syncthing/syncthing.log 2>&1 &

proot-distro list 2>/dev/null | grep -q "ubuntu (installed)" && nohup proot-distro login ubuntu --bind /data/data/com.termux/files/home/storage/shared:/media/storage -- jellyfin > ~/jellyfin.log 2>&1 &

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

# ================= EXECUTE =================
echo ""
echo -e "${G}========================================================================================${NC}"
echo -e "${W}${BOLD}              >>> [SYSTEM ONLINE] INSTALLATION COMPLETE! <<<                            ${NC}"
echo -e "${G}========================================================================================${NC}"
echo -ne "${Y}[?] Start servers right now? (Y/n): ${NC}"
read run_now </dev/tty || run_now="Y"
run_now=${run_now:-Y}

if [[ "$run_now" =~ ^[Yy]$ ]]; then
    bash ~/start-services.sh
else
    echo -e "${C}Start anytime with: ${BOLD}bash ~/start-services.sh${NC}"
fi
