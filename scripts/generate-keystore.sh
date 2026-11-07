#!/usr/bin/env bash
#
# Generate a release keystore and keystore.properties for signing the APK.
#
# Usage:
#   scripts/generate-keystore.sh [options]
#
# Options:
#   -f, --force            overwrite an existing keystore / keystore.properties
#   -k, --keystore PATH    keystore file (default: release.jks)
#   -a, --alias NAME       key alias (default: jellymusic)
#   -d, --dname NAME       X.500 distinguished name
#   -s, --size BITS        RSA key size (default: 4096)
#   -v, --validity DAYS    certificate validity in days (default: 10000)
#   -g, --generate         generate a random password and print it once
#   -h, --help             show this help
#
# Environment:
#   KEYSTORE_PASSWORD      password for non-interactive use (CI)
#   KEYSTORE_PROPERTIES    properties output path (default: keystore.properties)
#
# The password is never passed on the command line; keytool reads it from an
# environment variable. keystore.properties and the keystore are gitignored.
set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

KEYSTORE_PATH="${KEYSTORE_PATH:-release.jks}"
ALIAS="${ALIAS:-jellymusic}"
KEY_SIZE="${KEY_SIZE:-4096}"
VALIDITY_DAYS="${VALIDITY_DAYS:-10000}"
PROPERTIES="${KEYSTORE_PROPERTIES:-keystore.properties}"
FORCE=0
GENERATE_PASSWORD=0
DNAME="${DNAME:-}"

usage() {
    sed -n '2,24p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -f|--force) FORCE=1; shift ;;
        -k|--keystore) KEYSTORE_PATH="$2"; shift 2 ;;
        -a|--alias) ALIAS="$2"; shift 2 ;;
        -d|--dname) DNAME="$2"; shift 2 ;;
        -s|--size) KEY_SIZE="$2"; shift 2 ;;
        -v|--validity) VALIDITY_DAYS="$2"; shift 2 ;;
        -g|--generate) GENERATE_PASSWORD=1; shift ;;
        -h|--help) usage 0 ;;
        *) echo "Unknown option: $1" >&2; usage 1 ;;
    esac
done

if ! command -v keytool >/dev/null 2>&1; then
    echo "error: keytool not found. Install a JDK 17 first." >&2
    exit 1
fi

if [[ -e "$KEYSTORE_PATH" && $FORCE -ne 1 ]]; then
    echo "error: $KEYSTORE_PATH already exists (use --force to overwrite)." >&2
    exit 1
fi
if [[ -e "$PROPERTIES" && $FORCE -ne 1 ]]; then
    echo "error: $PROPERTIES already exists (use --force to overwrite)." >&2
    exit 1
fi

if [[ -z "${KEYSTORE_PASSWORD:-}" ]]; then
    if [[ $GENERATE_PASSWORD -eq 1 ]]; then
        KEYSTORE_PASSWORD="$(dd if=/dev/urandom bs=64 count=1 2>/dev/null | base64 | tr -dc 'A-Za-z0-9' | cut -c1-32)"
        echo
        echo "Generated password (save it now, it will not be shown again):"
        echo
        echo "    $KEYSTORE_PASSWORD"
        echo
    else
        read -r -s -p "Keystore password (min 6 chars): " KEYSTORE_PASSWORD
        echo
        read -r -s -p "Repeat password: " PASSWORD_CONFIRM
        echo
        if [[ "$KEYSTORE_PASSWORD" != "$PASSWORD_CONFIRM" ]]; then
            echo "error: passwords do not match." >&2
            exit 1
        fi
        unset PASSWORD_CONFIRM
    fi
fi

if [[ ${#KEYSTORE_PASSWORD} -lt 6 ]]; then
    echo "error: password must be at least 6 characters." >&2
    exit 1
fi

if [[ -z "$DNAME" ]]; then
    CN="$(git config user.name 2>/dev/null || true)"
    CN="${CN:-Jellyfin Music}"
    CN="${CN//,/ }"
    DNAME="CN=$CN, O=Jellyfin Music, C=CN"
fi

echo "==> Generating keystore: $KEYSTORE_PATH"
echo "    alias: $ALIAS, RSA $KEY_SIZE, validity $VALIDITY_DAYS days"
echo "    DN: $DNAME"

KEYPASS="$KEYSTORE_PASSWORD" keytool -genkeypair \
    -keystore "$KEYSTORE_PATH" \
    -storetype PKCS12 \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize "$KEY_SIZE" \
    -validity "$VALIDITY_DAYS" \
    -dname "$DNAME" \
    -storepass:env KEYPASS \
    -keypass:env KEYPASS

echo "==> Writing $PROPERTIES"
cat > "$PROPERTIES" <<EOF
storeFile=$KEYSTORE_PATH
storePassword=$KEYSTORE_PASSWORD
keyAlias=$ALIAS
keyPassword=$KEYSTORE_PASSWORD
EOF
chmod 600 "$PROPERTIES" "$KEYSTORE_PATH"

echo "==> Verifying"
KEYPASS="$KEYSTORE_PASSWORD" keytool -list \
    -keystore "$KEYSTORE_PATH" \
    -storepass:env KEYPASS \
    -alias "$ALIAS" >/dev/null

echo
echo "Done."
echo "  keystore:    $KEYSTORE_PATH"
echo "  properties:  $PROPERTIES"
echo
echo "Next:"
echo "  1. Back up $KEYSTORE_PATH and the password in a safe place."
echo "     Losing them means you cannot update already-installed builds."
echo "  2. Build a signed APK:"
echo "       /opt/gradle-8.7/bin/gradle assembleRelease"
echo "     Output: app/build/outputs/apk/release/app-release.apk"
echo "  3. Verify:"
echo "       ~/sdk/build-tools/34.0.0/apksigner verify --print-certs \\"
echo "         app/build/outputs/apk/release/app-release.apk"
echo
echo "Note: $KEYSTORE_PATH and $PROPERTIES are gitignored. Never commit them."
