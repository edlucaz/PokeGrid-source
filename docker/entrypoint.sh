#!/bin/bash
set -e

mkdir -p /data/.vnc

# Senha do VNC: gera uma na primeira vez e reaproveita depois (fica no volume persistente).
# So quem tem essa senha (ou acesso pela rede do Tailscale) consegue ver a tela.
if [ ! -f /data/.vnc/passwd ]; then
  VNC_PASS=$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 16)
  echo "$VNC_PASS" > /data/.vnc/password-plaintext.txt
  chmod 600 /data/.vnc/password-plaintext.txt
  x11vnc -storepasswd "$VNC_PASS" /data/.vnc/passwd >/dev/null
  echo "=== Senha do VNC gerada: $VNC_PASS (tambem salva em /data/.vnc/password-plaintext.txt) ==="
fi

Xvfb :99 -screen 0 1600x900x24 &
XVFB_PID=$!
for i in $(seq 1 20); do xdpyinfo -display :99 >/dev/null 2>&1 && break; sleep 0.5; done

openbox &

x11vnc -display :99 -forever -shared -rfbauth /data/.vnc/passwd -bg -o /data/x11vnc.log
websockify --web=/usr/share/novnc 6080 localhost:5900 &

# Keyring pra criptografia local de credenciais (safeStorage) funcionar mesmo sem
# sessao grafica de desktop de verdade. Sem isso o Electron salva as senhas sem
# criptografia (fallback do proprio app quando nao ha keyring disponivel).
# --start e --unlock sao incompatíveis na mesma chamada: precisa iniciar o daemon
# primeiro (registra o servico de secrets no D-Bus), depois destrancar em separado.
mkdir -p /data/.local/share/keyrings
eval "$(printf '' | gnome-keyring-daemon --start --components=secrets,pkcs11 2>/data/keyring.log)"
export GNOME_KEYRING_CONTROL
printf '\n' | gnome-keyring-daemon --unlock >>/data/keyring.log 2>&1

cd /app
exec node_modules/.bin/electron . --no-sandbox
