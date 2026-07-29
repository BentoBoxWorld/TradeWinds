#!/usr/bin/env bash
# CI prerequisite: rebuild the patched MockBukkit the tests depend on.
#
# MockBukkit has no Paper 26.2 release, so the tests use a locally patched jar
# (mockbukkit-v26.1.2:4.113.4-p262) that is published nowhere - which is why a
# clean CI checkout cannot resolve it. This script recreates it on the build
# agent: fetch the upstream base jar from PaperMC, unzip paper-api, then run
# scripts/build_patched_mockbukkit.py (which only rewrites JSON resources inside
# the base jar - no source build). Idempotent; safe to re-run.
#
# Run from the repo root, BEFORE `mvn`:
#   bash scripts/ci_prep.sh
#
# Requirements on the agent: mvn, a JDK (for javap), python3, unzip.
# Versions are read from pom.xml so they never drift.
# Delete this (and build_patched_mockbukkit.py) when MockBukkit ships 26.2.
set -euo pipefail

cd "$(dirname "$0")/.."

version_of() { sed -n "s%.*<$1>\\([^<]*\\)</$1>.*%\\1%p" pom.xml | head -n1; }

PAPER_VERSION="$(version_of paper.version)"
MBK_BASE="$(version_of mock-bukkit.version)"; MBK_BASE="${MBK_BASE%-p262}"
echo "ci_prep: paper-api=$PAPER_VERSION  base mockbukkit=$MBK_BASE"

REPOS="https://repo.papermc.io/repository/maven-public/,https://repo.codemc.org/repository/maven-public/,https://repo1.maven.org/maven2/"

mvn -B -q dependency:get -Dtransitive=false -DremoteRepositories="$REPOS" \
  -Dartifact="io.papermc.paper:paper-api:$PAPER_VERSION"
mvn -B -q dependency:get -Dtransitive=false -DremoteRepositories="$REPOS" \
  -Dartifact="org.mockbukkit.mockbukkit:mockbukkit-v26.1.2:$MBK_BASE"

PAPER_JAR="$HOME/.m2/repository/io/papermc/paper/paper-api/$PAPER_VERSION/paper-api-$PAPER_VERSION.jar"
rm -rf /tmp/paperapi && mkdir -p /tmp/paperapi
( cd /tmp/paperapi && unzip -q "$PAPER_JAR" )

python3 scripts/build_patched_mockbukkit.py
echo "ci_prep: done"
