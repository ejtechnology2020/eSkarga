# SoftHSM2 – emulator PKCS#11 na dev (D12). Produkcja: para sieciowych HSM z PKCS#11 i CC EN 419 221-5.
FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends softhsm2 opensc && rm -rf /var/lib/apt/lists/*
COPY init-token.sh /init-token.sh
RUN chmod +x /init-token.sh
CMD ["/init-token.sh"]
