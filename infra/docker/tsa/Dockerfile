FROM python:3.12-alpine
# Deweloperski serwer znaczników czasu RFC 3161 (ADR 0023): OpenSSL `ts` za minimalnym HTTP. Certyfikat TSA samopodpisany,
# generowany przy pierwszym starcie do wolumenu – do dev/preprod; prod: kwalifikowany TSA.
RUN apk add --no-cache openssl
WORKDIR /tsa
COPY tsa.py openssl-tsa.cnf init-tsa.sh ./
RUN chmod +x init-tsa.sh
EXPOSE 3180
CMD ["sh", "-c", "./init-tsa.sh && python3 tsa.py"]
