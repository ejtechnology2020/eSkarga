#!/bin/sh
# Tworzy token „eskarga” z kluczem do podpisu łańcucha audytu i kluczem nadrzędnym repozytorium (dev).
set -e
if ! softhsm2-util --show-slots | grep -q "Label:\s*eskarga"; then
  softhsm2-util --init-token --free --label eskarga --so-pin "$SOFTHSM_SO_PIN" --pin "$SOFTHSM_USER_PIN"
  pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so --login --pin "$SOFTHSM_USER_PIN" --keypairgen --key-type rsa:3072 --label audit-chain --id 01
  pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so --login --pin "$SOFTHSM_USER_PIN" --keygen --key-type aes:32 --label repo-master --id 02
fi
echo "SoftHSM gotowy"; tail -f /dev/null
