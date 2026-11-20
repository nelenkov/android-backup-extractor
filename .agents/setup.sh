#!/usr/bin/env bash
set -euo pipefail

if ! command -v java >/dev/null 2>&1 || ! command -v javac >/dev/null 2>&1; then
    echo "ERROR: JDK (java and javac) is required to build android-backup-extractor." >&2
    exit 1
fi

# Install a shell helper that configures a Java truststore (when the JVM default
# cacerts is missing) and forwards HTTP_PROXY/HTTPS_PROXY settings to Gradle and Maven.
mkdir -p "${HOME}/.local/share/java"
cat <<'EOF' > "${HOME}/.local/share/java/env.sh"
# Configure Java truststore and HTTP/HTTPS proxy properties for Gradle and Maven.
_configure_java_env() {
    local ca_bundle="/etc/ssl/certs/ca-certificates.crt"
    local truststore="${HOME}/.local/share/java/cacerts"
    local truststore_src="${truststore}.src.pem"
    local java_opts=""
    local gradle_props_block=""

    if [ -r "$ca_bundle" ] && [ ! -r /etc/ssl/certs/java/cacerts ]; then
        if [ ! -r "$truststore" ] || ! cmp -s "$ca_bundle" "$truststore_src" 2>/dev/null; then
            mkdir -p "${HOME}/.local/share/java"
            rm -f "$truststore" "$truststore_src"
            if command -v keytool >/dev/null 2>&1; then
                local tmp_dir idx cert
                tmp_dir="$(mktemp -d)"
                awk -v dir="$tmp_dir" '
                    /-----BEGIN CERTIFICATE-----/ { n++; f = sprintf("%s/cert-%d.pem", dir, n) }
                    f != "" { print > f }
                    /-----END CERTIFICATE-----/ { close(f); f = "" }
                ' "$ca_bundle"
                idx=0
                for cert in "$tmp_dir"/cert-*.pem; do
                    [ -r "$cert" ] || continue
                    idx=$((idx + 1))
                    keytool -importcert -noprompt -trustcacerts \
                        -alias "system-ca-${idx}" \
                        -file "$cert" \
                        -keystore "$truststore" \
                        -storepass changeit \
                        -storetype PKCS12 >/dev/null 2>&1 || true
                done
                rm -rf "$tmp_dir"
                if [ -r "$truststore" ]; then
                    cp "$ca_bundle" "$truststore_src"
                fi
            fi
        fi
        if [ -r "$truststore" ]; then
            java_opts="${java_opts} -Djavax.net.ssl.trustStore=${truststore} -Djavax.net.ssl.trustStorePassword=changeit"
            gradle_props_block="${gradle_props_block}systemProp.javax.net.ssl.trustStore=${truststore}
systemProp.javax.net.ssl.trustStorePassword=changeit
"
        fi
    fi

    local http_p="${HTTP_PROXY:-${http_proxy:-}}"
    local https_p="${HTTPS_PROXY:-${https_proxy:-}}"
    local hp host port

    if [ -n "$http_p" ]; then
        hp="${http_p#*://}"
        hp="${hp##*@}"
        hp="${hp%%/*}"
        host="${hp%%:*}"
        port="${hp##*:}"
        if [ -n "$host" ] && [ -n "$port" ] && [ "$host" != "$port" ]; then
            java_opts="${java_opts} -Dhttp.proxyHost=${host} -Dhttp.proxyPort=${port}"
            gradle_props_block="${gradle_props_block}systemProp.http.proxyHost=${host}
systemProp.http.proxyPort=${port}
"
        fi
    fi

    if [ -n "$https_p" ]; then
        hp="${https_p#*://}"
        hp="${hp##*@}"
        hp="${hp%%/*}"
        host="${hp%%:*}"
        port="${hp##*:}"
        if [ -n "$host" ] && [ -n "$port" ] && [ "$host" != "$port" ]; then
            java_opts="${java_opts} -Dhttps.proxyHost=${host} -Dhttps.proxyPort=${port}"
            gradle_props_block="${gradle_props_block}systemProp.https.proxyHost=${host}
systemProp.https.proxyPort=${port}
"
        fi
    fi

    if [ -n "$java_opts" ]; then
        java_opts="${java_opts# }"
        case " ${GRADLE_OPTS:-} " in
            *"${java_opts}"*) ;;
            *) export GRADLE_OPTS="${GRADLE_OPTS:+${GRADLE_OPTS} }${java_opts}" ;;
        esac
        case " ${MAVEN_OPTS:-} " in
            *"${java_opts}"*) ;;
            *) export MAVEN_OPTS="${MAVEN_OPTS:+${MAVEN_OPTS} }${java_opts}" ;;
        esac
    fi

    local gradle_props="${HOME}/.gradle/gradle.properties"
    if [ -n "$gradle_props_block" ]; then
        local managed="# BEGIN env-setup java-proxy
${gradle_props_block}# END env-setup java-proxy"
        mkdir -p "${HOME}/.gradle"
        if [ ! -f "$gradle_props" ] || ! grep -qF "# BEGIN env-setup java-proxy" "$gradle_props" || [ "$(sed -n '/^# BEGIN env-setup java-proxy$/,/^# END env-setup java-proxy$/p' "$gradle_props")" != "$managed" ]; then
            local tmp_props
            tmp_props="$(mktemp)"
            if [ -f "$gradle_props" ]; then
                sed '/^# BEGIN env-setup java-proxy$/,/^# END env-setup java-proxy$/d' "$gradle_props" > "$tmp_props"
            fi
            printf '%s\n' "$managed" >> "$tmp_props"
            mv "$tmp_props" "$gradle_props"
        fi
    elif [ -f "$gradle_props" ] && grep -qF "# BEGIN env-setup java-proxy" "$gradle_props"; then
        local tmp_props
        tmp_props="$(mktemp)"
        sed '/^# BEGIN env-setup java-proxy$/,/^# END env-setup java-proxy$/d' "$gradle_props" > "$tmp_props"
        mv "$tmp_props" "$gradle_props"
    fi
}
_configure_java_env
unset -f _configure_java_env
EOF

# Persist sourcing in ~/.bashrc for future non-interactive and interactive shells.
touch "${HOME}/.bashrc"
sed -i '/case \$- in/,/esac/d' "${HOME}/.bashrc" 2>/dev/null || true
source_line='[ -f "$HOME/.local/share/java/env.sh" ] && . "$HOME/.local/share/java/env.sh"'
grep -qxF "$source_line" "${HOME}/.bashrc" || echo "$source_line" >> "${HOME}/.bashrc"

# Apply immediately for the builds below.
# shellcheck source=/dev/null
. "${HOME}/.local/share/java/env.sh"

# Warm caches and verify Gradle and Maven builds.
./gradlew --no-daemon shadowJar
if command -v mvn >/dev/null 2>&1; then
    mvn -B package
fi
