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
JAR="target/TradeWinds-0.1.0-SNAPSHOT-LOCAL.jar"

if pgrep -f "paper-26.2.jar" > /dev/null; then
    echo "REFUSING TO DEPLOY: the test server is running."
    echo "Stop it first - replacing the jar under a live server crashes it."
    exit 1
fi

mvn -q clean package "$@"
cp "$JAR" "$ADDONS/"
echo "Deployed $(basename "$JAR") to $ADDONS"
