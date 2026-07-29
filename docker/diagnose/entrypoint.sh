#!/bin/bash
set -e

# Diagnostico: mesmo Xvfb + noVNC do container principal, mas com Chromium comum
# em vez do Electron do PokeGrid. Serve pra isolar a causa do bloqueio do Cloudflare:
# se o Chromium passar e o Electron nao, o problema e fingerprint de renderizacao
# (WebGL por software, sem GPU); se o Chromium tambem travar, o problema e o IP
# da Hetzner (reputacao de datacenter), e nao tem o que ajustar no container.

VNC_PASS=$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 16)
mkdir -p /tmp/.vnc
x11vnc -storepasswd "$VNC_PASS" /tmp/.vnc/passwd >/dev/null
echo "=== Senha do VNC (diagnostico): $VNC_PASS ==="

Xvfb :99 -screen 0 1600x900x24 &
for i in $(seq 1 20); do xdpyinfo -display :99 >/dev/null 2>&1 && break; sleep 0.5; done

openbox &

x11vnc -display :99 -forever -shared -rfbauth /tmp/.vnc/passwd -bg -o /tmp/x11vnc.log
websockify --web=/usr/share/novnc 6080 localhost:5900 &

exec chromium \
  --no-sandbox \
  --disable-dev-shm-usage \
  --window-size=1600,900 \
  --window-position=0,0 \
  --no-first-run \
  --no-default-browser-check \
  "https://poke.idleworld.online/login"
