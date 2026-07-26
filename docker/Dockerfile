FROM node:22-bookworm

RUN apt-get update && apt-get install -y --no-install-recommends \
    xvfb openbox x11vnc novnc websockify dbus-x11 gnome-keyring libsecret-1-0 \
    libnss3 libatk1.0-0 libatk-bridge2.0-0 libcups2 libdrm2 libxkbcommon0 \
    libxcomposite1 libxdamage1 libxfixes3 libxrandr2 libgbm1 libasound2 \
    libpango-1.0-0 libcairo2 libgtk-3-0 fonts-liberation xdg-utils libu2f-udev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

COPY main.js preload.js index.html tray.png ./
COPY presets ./presets
COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENV DISPLAY=:99
ENV HOME=/data
RUN mkdir -p /data && chmod 700 /data
VOLUME ["/data"]

EXPOSE 6080
ENTRYPOINT ["/entrypoint.sh"]
