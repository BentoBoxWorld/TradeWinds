#!/usr/bin/env python3
"""Build a MockBukkit jar patched for the Paper 26.2 API and install it to
~/.m2 as version 4.113.4-p262.

MockBukkit has no 26.2 release. Compiling tests against Paper API 26.2 breaks
MockBukkit 4.113.4 (built for 26.1.2) in three data-driven ways, all fixed by
adding/patching JSON resources inside the jar (TagsMock walks its OWN jar
filesystem, so classpath shadowing cannot fix the tag files - hence a patched
jar rather than test-resource shadows):

1. registries/registry_key_class_relation.json - 26.2 added the
   minecraft:sulfur_cube_archetype and minecraft:zombie_nautilus_variant
   registries; RegistryAccessMock aborts on unknown RegistryKey entries.
2. keyed/<registry>.json - Paper API classes with registry-backed constants
   (GameEvent, Sound, ItemType, BlockType) initialise ALL constants; one
   missing key kills the class. New entries are cloned from a template with
   key-derived strings substituted.
3. tags/<registry>/<tag>.json - org.bukkit.Tag fields for 26.2-new tags load
   null, and Paper's MaterialTags/EntityTags static init NPEs on them. Missing
   tags are created empty.

A fourth incompatibility (MockBukkit's Adventure 4.x service providers vs
Paper 26.2's Adventure 5.2.0) is class-level and handled by a class shadow in
src/test/java/org/mockbukkit/mockbukkit/adventure/.

Prerequisite: /tmp/paperapi contains the unzipped paper-api 26.2 jar:
  cd /tmp && rm -rf paperapi && mkdir paperapi && cd paperapi \
      && unzip -q ~/.m2/repository/io/papermc/paper/paper-api/26.2.build.40-alpha/paper-api-26.2.build.40-alpha.jar

Usage: python3 scripts/build_patched_mockbukkit.py
Delete all of this when MockBukkit ships a 26.2 build.
"""
import copy
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

SRC_VERSION = "4.113.4"
OUT_VERSION = SRC_VERSION + "-p262"
ARTIFACT = "mockbukkit-v26.1.2"
MBK_JAR = os.path.expanduser(
    f"~/.m2/repository/org/mockbukkit/mockbukkit/{ARTIFACT}/{SRC_VERSION}/{ARTIFACT}-{SRC_VERSION}.jar")
PAPER_DIR = "/tmp/paperapi"

# keyed registries to diff: registry -> (api class, no_dots, template key or None)
KEYED = {
    "game_event": ("org/bukkit/GameEvent.class", False, None),
    "sound_event": ("org/bukkit/Sound.class", False, None),
    "item": ("org/bukkit/inventory/ItemType.class", True, "stone"),
    "block": ("org/bukkit/block/BlockType.class", True, "stone"),
    # 26.2 added biomes (e.g. minecraft:sulfur_caves); org.bukkit.block.Biome
    # initialises every constant, so one missing key kills the class.
    "worldgen/biome": ("org/bukkit/block/Biome.class", False, "ocean"),
    # 26.2 added damage types (e.g. minecraft:sulfur_cube_hot); same pattern.
    "damage_type": ("org/bukkit/damage/DamageType.class", False, "generic"),
}

NEW_REGISTRIES = {
    "minecraft:sulfur_cube_archetype": "org.bukkit.entity.SulfurCube$Archetype",
    "minecraft:zombie_nautilus_variant": "org.bukkit.entity.ZombieNautilus$Variant",
}

TAG_REGISTRY_DIRS = {"blocks", "items", "fluids", "entity_types", "game_events", "damage_types"}


def javap(class_path):
    return subprocess.run(["javap", "-c", os.path.join(PAPER_DIR, class_path)],
                          capture_output=True, text=True).stdout


def api_keys(class_path, no_dots):
    pattern = r"[a-z0-9_/]+" if no_dots else r"[a-z0-9_./]+"
    keys = set()
    for m in re.finditer(r"// String (\S+)$", javap(class_path), re.M):
        if re.fullmatch(pattern, m.group(1)):
            keys.add(m.group(1))
    return keys


def substitute(obj, old_bare, new_bare):
    if isinstance(obj, str):
        return re.sub(r"\b" + re.escape(old_bare) + r"\b", new_bare, obj)
    if isinstance(obj, list):
        return [substitute(v, old_bare, new_bare) for v in obj]
    if isinstance(obj, dict):
        return {k: substitute(v, old_bare, new_bare) for k, v in obj.items()}
    return obj


def patched_keyed(zf, registry, class_path, no_dots, template_key):
    data = json.loads(zf.read(f"keyed/{registry}.json"))
    values = data["values"]
    existing = {v["key"] for v in values}
    template = next((v for v in values if v["key"] == "minecraft:" + template_key), values[0]) \
        if template_key else values[0]
    template_bare = template["key"].split(":", 1)[1]
    missing = sorted("minecraft:" + k for k in api_keys(class_path, no_dots)
                     if "minecraft:" + k not in existing)
    for key in missing:
        bare = key.split(":", 1)[1]
        entry = substitute(copy.deepcopy(template), template_bare, bare)
        entry["key"] = key
        values.append(entry)
    print(f"  keyed/{registry}.json: +{len(missing)}")
    return json.dumps(data, indent=1)


def tag_pairs():
    """(registry_dir, tag_name) pairs referenced by org.bukkit.Tag (26.2)."""
    pairs = []
    prev = None
    for line in javap("org/bukkit/Tag.class").splitlines():
        m = re.search(r"// String ([a-z0-9_/.]+)$", line)
        if not m:
            continue
        s = m.group(1)
        if s in TAG_REGISTRY_DIRS:
            prev = s
        elif prev is not None:
            pairs.append((prev, s))
            prev = None
    return sorted(set(pairs))


def main():
    if not os.path.isdir(PAPER_DIR):
        sys.exit(f"{PAPER_DIR} missing - unzip the paper-api jar there first (see docstring)")

    out_path = tempfile.mktemp(suffix=".jar")
    with zipfile.ZipFile(MBK_JAR) as zin:
        names = set(zin.namelist())
        replacements = {}
        # 1. registry key class relation
        rel = json.loads(zin.read("registries/registry_key_class_relation.json"))
        rel.update(NEW_REGISTRIES)
        replacements["registries/registry_key_class_relation.json"] = json.dumps(rel, indent=1, sort_keys=True)
        print("  registries/registry_key_class_relation.json: +%d" % len(NEW_REGISTRIES))
        # 2. keyed registries
        for registry, (class_path, no_dots, template) in KEYED.items():
            replacements[f"keyed/{registry}.json"] = patched_keyed(zin, registry, class_path, no_dots, template)
        # 3. missing tags
        additions = {}
        for reg_dir, tag in tag_pairs():
            if "/" in tag:
                # Nested tag dirs (items/sulfur_cube_archetype/*) break
                # TagsMock's directory walk and silently abort ALL tag
                # loading, nulling every org.bukkit.Tag field. Skip them;
                # the corresponding Tag fields stay null, which nothing in
                # MaterialTags/EntityTags reads.
                print(f"  tags: skipping nested {reg_dir}/{tag}")
                continue
            path = f"tags/{reg_dir}/{tag}.json"
            if path not in names:
                additions[path] = json.dumps({"replace": False, "values": []}, indent=1)
        print(f"  tags: +{len(additions)} empty tag files")
        # write output jar
        with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                content = replacements.get(item.filename)
                if content is not None:
                    zout.writestr(item.filename, content)
                else:
                    zout.writestr(item, zin.read(item.filename))
            for path, content in additions.items():
                zout.writestr(path, content)

    # Install with the ORIGINAL pom (version-bumped) so transitive deps
    # (adventure, byte-buddy, etc.) still resolve - a bare install-file pom
    # would drop them and break Mockito on Java 25.
    src_pom = MBK_JAR[:-4] + ".pom"
    pom_text = open(src_pom).read().replace(
        f"<version>{SRC_VERSION}</version>", f"<version>{OUT_VERSION}</version>", 1)
    pom_path = tempfile.mktemp(suffix=".pom")
    open(pom_path, "w").write(pom_text)
    subprocess.run([
        "mvn", "-q", "org.apache.maven.plugins:maven-install-plugin:3.1.1:install-file",
        f"-Dfile={out_path}",
        f"-DpomFile={pom_path}",
    ], check=True)
    os.unlink(out_path)
    os.unlink(pom_path)
    print(f"installed org.mockbukkit.mockbukkit:{ARTIFACT}:{OUT_VERSION}")


if __name__ == "__main__":
    main()
