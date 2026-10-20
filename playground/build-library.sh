#!/bin/sh
set -eu
# Normal Docker Desktop builds need no proxy or certificate configuration.
# Optional support allows testing the same build in the managed cloud sandbox.
settings=/tmp/playground-maven-settings.xml
trust=/tmp/playground-cacerts
trap 'rm -f "$settings" "$trust"' EXIT
printf '<settings/>' > "$settings"
proxy_url="${HTTPS_PROXY:-${https_proxy:-}}"
if [ -n "$proxy_url" ]; then
  proxy_scheme="${proxy_url%%://*}"
  proxy_address="${proxy_url#*://}"
  proxy_address="${proxy_address%/}"
  case "$proxy_address" in *@*) echo 'Authenticated proxies need a custom Maven settings file.' >&2; exit 1;; esac
  proxy_host="${proxy_address%%:*}"
  proxy_port="${proxy_address##*:}"
  if [ "$proxy_port" = "$proxy_host" ]; then proxy_port=80; fi
  cat > "$settings" <<SETTINGS
<settings><proxies><proxy><id>build-proxy</id><active>true</active><protocol>$proxy_scheme</protocol><host>$proxy_host</host><port>$proxy_port</port></proxy></proxies></settings>
SETTINGS
fi
if [ -f /run/secrets/proxy_ca ]; then
  cp "$JAVA_HOME/lib/security/cacerts" "$trust"
  keytool -importcert -noprompt -alias playground-build-proxy \
    -file /run/secrets/proxy_ca -keystore "$trust" -storepass changeit >/dev/null
  export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStore=$trust"
fi
# This compiles the bundled source, not a release or a full test run.
mvn -s "$settings" -B -ntp \
  -Dbasepom.git-id.fail-no-git=false \
  -Dbasepom.build.phase-toolchain=none \
  -Dbasepom.check.skip-all=true \
  -Dbasepom.check.skip-dependency=false \
  -Dbasepom.check.skip-prettier=true \
  -Dbasepom.check.skip-checkstyle=true \
  -DincludeScope=runtime -DoutputDirectory=target/runtime-deps \
  compile dependency:copy-dependencies
