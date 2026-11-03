#!/bin/sh
set -e
mkdir -p /tsa/data
cd /tsa/data
if [ ! -f tsa.key ]; then
  openssl req -x509 -newkey rsa:3072 -nodes -days 1825 -keyout tsa.key -out tsa.crt -config /tsa/openssl-tsa.cnf -extensions tsa_ext
  echo 01 > serial
  echo "TSA dev: wygenerowano certyfikat (dodaj /tsa/data/tsa.crt do magazynu zaufania usługi dokumentów)"
fi
