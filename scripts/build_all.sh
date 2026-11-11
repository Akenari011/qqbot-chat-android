#!/usr/bin/env bash
# Full server-side build + test pipeline. Runs on the Chuncheon 24C/144G box.
# No GitHub Actions involved.
set -euo pipefail

REMOTE_HOST="${REMOTE_HOST:-ubuntu@158.180.80.69}"
REMOTE_DIR="${REMOTE_DIR:-~/android-build/QQBotChat}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_ed25519}"
SSHOPT="-o BatchMode=yes -o ConnectTimeout=10 -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"
LOCAL="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> [1/4] sync sources to build box"
ssh $SSHOPT -i "$SSH_KEY" "$REMOTE_HOST" "mkdir -p $REMOTE_DIR"
scp $SSHOPT -i "$SSH_KEY" -r \
  "$LOCAL/settings.gradle.kts" "$LOCAL/build.gradle.kts" "$LOCAL/gradle.properties" \
  "$LOCAL/core" "$LOCAL/app" \
  "$REMOTE_HOST:$REMOTE_DIR/"

echo "==> [2/4] JVM unit tests (:core)"
ssh $SSHOPT -i "$SSH_KEY" "$REMOTE_HOST" 'bash -s' <<'REMOTE'
cd ~/android-build/QQBotChat
export JAVA_HOME=$HOME/android-build/jdk
export ANDROID_HOME=$HOME/android-build/sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export QEMU_LD_PREFIX=$HOME/android-build/x86sysroot
$HOME/android-build/gradle/gradle-8.9/bin/gradle :core:test --no-daemon --rerun-tasks
REMOTE

echo "==> [3/4] assemble debug + release APK (:app)"
ssh $SSHOPT -i "$SSH_KEY" "$REMOTE_HOST" 'bash -s' <<'REMOTE'
cd ~/android-build/QQBotChat
export JAVA_HOME=$HOME/android-build/jdk
export ANDROID_HOME=$HOME/android-build/sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export QEMU_LD_PREFIX=$HOME/android-build/x86sysroot
$HOME/android-build/gradle/gradle-8.9/bin/gradle :app:assembleDebug :app:assembleRelease --no-daemon
REMOTE

echo "==> [4/4] pull APK"
mkdir -p "$LOCAL/deliverables"
scp $SSHOPT -i "$SSH_KEY" \
  "$REMOTE_HOST:$REMOTE_DIR/app/build/outputs/apk/debug/app-debug.apk" \
  "$LOCAL/deliverables/qqbot-chat-debug.apk"
shasum -a 256 "$LOCAL/deliverables/qqbot-chat-debug.apk"
echo "DONE -> $LOCAL/deliverables/qqbot-chat-debug.apk"
