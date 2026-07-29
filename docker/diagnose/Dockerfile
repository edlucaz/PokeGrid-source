FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    chromium xvfb x11vnc novnc websockify openbox dbus-x11 \
    fonts-liberation \
    && rm -rf /var/lib/apt/lists/*

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENV DISPLAY=:99
EXPOSE 6080
ENTRYPOINT ["/entrypoint.sh"]
