#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
keystore="$project_root/certs/localhost.p12"
env_file="$project_root/.env"
keytool="${JAVA_HOME:+$JAVA_HOME/bin/keytool}"
if [[ -z "$keytool" || ! -x "$keytool" ]]; then
  keytool="$(command -v keytool || true)"
fi
if [[ -z "$keytool" || ! -x "$keytool" ]]; then
  echo "No se encontró keytool; configura JAVA_HOME con el JDK 17." >&2
  exit 1
fi
if [[ -f "$env_file" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$env_file"
  set +a
fi

mkdir -p "$(dirname "$keystore")"
if [[ -z "${TLS_KEYSTORE_PASSWORD:-}" || "$TLS_KEYSTORE_PASSWORD" == REEMPLAZAR_* ]]; then
  if [[ -e "$keystore" ]]; then
    echo "Ya existe el keystore; restaura TLS_KEYSTORE_PASSWORD en .env." >&2
    exit 1
  fi
  export TLS_KEYSTORE_PASSWORD="$(openssl rand -base64 24)"
fi
export TLS_KEYSTORE="file:./certs/localhost.p12"
if [[ ! -e "$keystore" ]]; then
  "$keytool" -genkeypair -alias localhost -keyalg RSA -keysize 3072 \
    -sigalg SHA256withRSA -validity 365 -storetype PKCS12 \
    -keystore "$keystore" -storepass "$TLS_KEYSTORE_PASSWORD" \
    -keypass "$TLS_KEYSTORE_PASSWORD" \
    -dname "CN=localhost, OU=Development, O=Local, L=Local, ST=Local, C=MX" \
    -ext "SAN=dns:localhost,ip:127.0.0.1"
else
  "$keytool" -list -keystore "$keystore" -storetype PKCS12 \
    -storepass "$TLS_KEYSTORE_PASSWORD" >/dev/null
fi
if [[ -z "${JWT_SECRET_BASE64:-}" || "$JWT_SECRET_BASE64" == REEMPLAZAR_* ]]; then
  export JWT_SECRET_BASE64="$(openssl rand -base64 32)"
fi
if [[ -z "${DEMO_USER_PASSWORD:-}" || "$DEMO_USER_PASSWORD" == REEMPLAZAR_* ]]; then
  export DEMO_USER_PASSWORD="$(openssl rand -base64 24)"
fi
if [[ -z "${DEMO_ADMIN_PASSWORD:-}" || "$DEMO_ADMIN_PASSWORD" == REEMPLAZAR_* ]]; then
  export DEMO_ADMIN_PASSWORD="$(openssl rand -base64 24)"
fi
export JWT_ISSUER="${JWT_ISSUER:-backend-auth-tls}"
export JWT_EXPIRATION_SECONDS="${JWT_EXPIRATION_SECONDS:-900}"
export CORS_ALLOWED_ORIGIN="${CORS_ALLOWED_ORIGIN:-http://localhost:4200}"
umask 077
printf 'TLS_KEYSTORE_PASSWORD=%s\nTLS_KEYSTORE=%s\nJWT_SECRET_BASE64=%s\nJWT_ISSUER=%s\nJWT_EXPIRATION_SECONDS=%s\nDEMO_USER_PASSWORD=%s\nDEMO_ADMIN_PASSWORD=%s\nCORS_ALLOWED_ORIGIN=%s\n' \
  "$TLS_KEYSTORE_PASSWORD" "$TLS_KEYSTORE" "$JWT_SECRET_BASE64" "$JWT_ISSUER" \
  "$JWT_EXPIRATION_SECONDS" "$DEMO_USER_PASSWORD" "$DEMO_ADMIN_PASSWORD" \
  "$CORS_ALLOWED_ORIGIN" > "$env_file"
printf 'Certificado PKCS12 creado en %s\n' "$keystore"
printf 'Secretos locales configurados en variables del shell y en .env (ignorado por Git).\n'
