#!/usr/bin/env bash
#
# Build TradeWinds and install it into the test server - but never over a
# running one.
#
# Overwriting a plugin jar while the server is up invalidates the open jar
# handle that the plugin classloader reads from. Anything not yet loaded then
# fails with NoClassDefFoundError, and if that happens inside chunk generation
# (which runs off the main thread) Paper treats it as an unrecoverable chunk
# system failure and takes the whole server down. That is exactly how the
# 2026-08-01 crash happened: jar written at 09:30:56, IslandPalette failed to
# load at 09:31:41.
#
set -euo pipefail

ADDONS="/Users/ben/Minecraft/26.2/plugins/BentoBox/addons"

# Match the server jar loosely: it is named paper-26.2-87.jar, not
# paper-26.2.jar, so an exact pattern silently never matched and the guard was
# decorative. Keep this broad.
if pgrep -f "java.*paper-26\.2" > /dev/null; then
    echo "REFUSING TO DEPLOY: the test server is running."
    echo "Stop it first - replacing the jar under a live server crashes it."
    exit 1
fi

# Belt and braces: a log written in the last minute means it is probably up
LOG="/Users/ben/Minecraft/26.2/logs/latest.log"
if [ -f "$LOG" ] && [ -n "$(find "$LOG" -mmin -1 2>/dev/null)" ]; then
    echo "REFUSING TO DEPLOY: $LOG was written within the last minute."
    echo "The server looks live. Stop it first."
    exit 1
fi

mvn -q clean package "$@"

# The jar carries the version, so never hardcode it here: a version bump used
# to break the copy (0.1.1, 2026-08-08). Take whatever package just built.
shopt -s nullglob
built=(target/TradeWinds-*-LOCAL.jar)
if [ ${#built[@]} -ne 1 ]; then
    echo "Expected exactly one target/TradeWinds-*-LOCAL.jar, found ${#built[@]}."
    exit 1
fi
JAR="${built[0]}"

# An older version left behind loads ALONGSIDE the new one - two TradeWinds
# addons over one world. Clear the deck first.
for old in "$ADDONS"/TradeWinds-*.jar; do
    if [ "$(basename "$old")" != "$(basename "$JAR")" ]; then
        echo "Removing stale $(basename "$old")"
        rm -f "$old"
    fi
done

cp "$JAR" "$ADDONS/"
echo "Deployed $(basename "$JAR") to $ADDONS"
