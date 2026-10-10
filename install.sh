#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "=========================================="
echo "    Installing Pocket Cloud by Huzef Dev   "
echo "=========================================="

termux-wake-lock
termux-setup-storage
echo "[*] Storage permission initialize ho rahi hai... 3 second wait karo"
sleep 3

pkg update -y && pkg install -y wget curl git tar jq proot ffmpeg proot-distro syncthing

# 1. Setup FileBrowser
echo "[*] Setting up FileBrowser..."
curl -fsSL https://raw.githubusercontent.com/filebrowser/get/master/get.sh | bash
mkdir -p ~/services/filebrowser
filebrowser config init -a 0.0.0.0 -p 8080 -r ~/storage/shared -d ~/services/filebrowser/filebrowser.db
filebrowser users add admin admin --perm.admin=true -d ~/services/filebrowser/filebrowser.db || true

# 2. Setup Syncthing
echo "[*] Setting up Syncthing..."
syncthing generate --home=~/.config/syncthing || true
sed -i 's/<address>127.0.0.1:8384<\/address>/<address>0.0.0.0:8384<\/address>/' ~/.config/syncthing/config.xml || true

# 3. Setup Jellyfin in PRoot
echo "[*] Setting up Jellyfin container..."
if ! proot-distro list | grep -q "ubuntu (installed)"; then
    proot-distro install ubuntu
fi

proot-distro login ubuntu -- env DEBIAN_FRONTEND=noninteractive bash -c '
apt update -y && apt install -y curl wget gnupg lsb-release ffmpeg libicu-dev
mkdir -p /etc/apt/keyrings
curl -fsSL https://repo.jellyfin.org/jellyfin_team.gpg.key | gpg --dearmor --yes -o /etc/apt/keyrings/jellyfin.gpg
echo "deb [signed-by=/etc/apt/keyrings/jellyfin.gpg arch=$(dpkg --print-architecture)] https://repo.jellyfin.org/ubuntu $(lsb_release -cs) main" | tee /etc/apt/sources.list.d/jellyfin.list
apt update -y && apt install -y jellyfin
'

# 4. Create Unified Launcher
cat << 'EOF' > ~/start-services.sh
#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock
mkdir -p ~/services/filebrowser ~/.config/syncthing

nohup filebrowser -d ~/services/filebrowser/filebrowser.db > ~/services/filebrowser/filebrowser.log 2>&1 &
nohup syncthing -no-browser -home=~/.config/syncthing > ~/.config/syncthing/syncthing.log 2>&1 &
nohup proot-distro login ubuntu --bind /data/data/com.termux/files/home/storage/shared:/media/storage -- jellyfin > ~/jellyfin.log 2>&1 &

IP=$(ifconfig wlan0 2>/dev/null | grep 'inet ' | awk '{print $2}')
[ -z "$IP" ] && IP="127.0.0.1"

echo "=========================================="
echo "Pocket Cloud is running!"
echo "FileBrowser: http://${IP}:8080 (admin/admin)"
echo "Syncthing:   http://${IP}:8384"
echo "Jellyfin:    http://${IP}:8096"
echo "=========================================="
EOF
chmod +x ~/start-services.sh

echo "=========================================="
echo " Installation Complete! Run: bash ~/start-services.sh"
echo "=========================================="
