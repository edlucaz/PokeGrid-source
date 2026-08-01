#!/bin/bash
# Roda o diagnostico do Cloudflare: sobe um Chromium comum (sem Electron) no
# mesmo tipo de display virtual que o PokeGrid usaria, acessivel por noVNC.
#
# Uso: execute este script NO SERVIDOR (via SSH a partir do seu PC), dentro
# da pasta do repositorio PokeGrid-source:
#   bash docker/diagnose/run.sh
#
# Depois abra http://SEU-IP-DA-HETZNER:6080/vnc.html no navegador do seu PC
# e entre com a senha que o script vai mostrar.
set -e

cd "$(dirname "$0")"

IMAGE=pokegrid-diagnose
CONTAINER=pokegrid-diagnose

if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "Removendo container anterior..."
  docker rm -f "$CONTAINER" >/dev/null
fi

echo "Buildando imagem de diagnostico..."
docker build -t "$IMAGE" .

echo "Subindo container (limite: 512MB RAM, 1 CPU)..."
docker run -d --name "$CONTAINER" --memory=512m --cpus=1 -p 6080:6080 "$IMAGE" >/dev/null

echo "Aguardando VNC iniciar..."
for i in $(seq 1 20); do
  docker logs "$CONTAINER" 2>&1 | grep -q "Senha do VNC" && break
  sleep 0.5
done

echo
echo "=== Pronto ==="
docker logs "$CONTAINER" 2>&1 | grep "Senha do VNC"
echo "Abra no navegador: http://$(curl -s -4 ifconfig.me 2>/dev/null || echo SEU-IP):6080/vnc.html"
echo
echo "Quando terminar o teste: docker rm -f $CONTAINER"
