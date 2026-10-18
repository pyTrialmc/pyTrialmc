import io
import json
import zipfile
from datetime import datetime, timezone
from pathlib import Path

from . import __version__
from . import crypto
from .color import GREEN, RED, paint
from .console import console

METADATA_FILE = "pytrial.mod.json"
BUILD_INFO_FILE = "pytrial.build.json"
OUTPUT_DIR = "dist"
PACKAGE_SUFFIX = ".pytm"

EXCLUDED_DIRS = {
    "dist",
    "build",
    "__pycache__",
    ".git",
    ".venv",
    ".idea",
    ".vscode",
    "venv",
    "env",
}
EXCLUDED_SUFFIXES = {".pyc", ".pyo"}


def collect_files(root):
    files = []
    skipped = []
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(root)
        if any(part in EXCLUDED_DIRS for part in relative.parts[:-1]):
            skipped.append(relative.as_posix())
            continue
        if path.suffix in EXCLUDED_SUFFIXES:
            skipped.append(relative.as_posix())
            continue
        if relative.name == BUILD_INFO_FILE:
            skipped.append(relative.as_posix())
            continue
        files.append(path)

    console.debug("included entries: " + str([path.relative_to(root).as_posix() for path in files]))
    if skipped:
        console.debug("skipped entries: " + str(skipped))
    return files


def build_package(root, game_version, output_dir=None):
    game_version = (game_version or "").strip()
    if not game_version:
        print(paint("Missing game version, usage: pytrialmc -b:<game version>", RED))
        return 1

    metadata_path = root / METADATA_FILE
    if not metadata_path.is_file():
        print(paint("No " + METADATA_FILE + " in the current directory, run: pytrialmc -init", RED))
        return 1

    console.debug("build root: " + str(root))
    console.debug("game version: " + game_version)

    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as error:
        print(paint(METADATA_FILE + " is not valid JSON: " + str(error), RED))
        return 1

    mod_id = metadata.get("id") or root.name
    mod_version = metadata.get("version") or "1.0.0"
    console.debug("mod id: " + str(mod_id) + ", mod version: " + str(mod_version))

    target_dir = Path(output_dir) if output_dir else root / OUTPUT_DIR
    target_dir.mkdir(parents=True, exist_ok=True)
    package_path = target_dir / (mod_id + "-" + mod_version + PACKAGE_SUFFIX)

    build_info = {
        "builder": "pyTrialmc",
        "builderVersion": __version__,
        "gameVersion": game_version,
        "modId": mod_id,
        "modVersion": mod_version,
        "builtAt": datetime.now(timezone.utc).isoformat(),
    }

    files = collect_files(root)
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(BUILD_INFO_FILE, json.dumps(build_info, ensure_ascii=False, indent=2) + "\n")
        for path in files:
            archive.write(path, path.relative_to(root).as_posix())
    package_path.write_bytes(crypto.encrypt(buffer.getvalue()))

    console.debug("package written: " + str(package_path))

    print(paint("Built " + str(package_path), GREEN))
    print("  game version: " + game_version)
    print("  entries: " + str(len(files) + 1))
    print("  copy it into <gameDir>/pytrial/mods/")
    return 0
