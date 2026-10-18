import json
import re
from pathlib import Path

from .color import GREEN, YELLOW, paint
from .console import console

METADATA_FILE = "pytrial.mod.json"
ENTRY_FILE = "main.py"
SRC_DIR = "src"
SAMPLE_ELEMENT_FILE = "example_block.py"

SAMPLE_ELEMENT_TEMPLATE = '''"""Implementation of the example block element.

The file name must match the name passed to mod.add.block(...) in main.py.
OPTIONS below are the element defaults; options passed from main.py win.
"""

OPTIONS = {
    "hardness": 3.0,
    "resistance": 6.0,
}


def configure(element):
    """Optional hook, called right before the element is sent to the loader."""
'''


def slugify(name):
    slug = re.sub(r"[^a-z0-9_.-]", "_", name.lower()).strip("_.-")
    return slug or "mod"


def entry_template(mod_id):
    return (
        "from pyTrialmc import *\n"
        "\n"
        "mod = modsrc()\n"
        "\n"
        "# Declare one element per line, every element is implemented by src/<name>.py.\n"
        "# Any kind from ELEMENT_KINDS is available as mod.add.<kind>(name, **options).\n"
        "# mod.add.block(\"example_block\")\n"
        "# mod.add.item(\"example_ingot\", stack_size=64)\n"
        "# mod.add.creative_tab(\"example_tab\", title=\"Example\", icon=\"" + mod_id + ":example_ingot\")\n"
        "\n"
        "mod.main()\n"
        "modmain()\n"
    )


def init_project(root):
    root = Path(root)
    metadata_path = root / METADATA_FILE
    entry_path = root / ENTRY_FILE
    src_dir = root / SRC_DIR
    sample_path = src_dir / SAMPLE_ELEMENT_FILE

    console.debug("init target: " + str(root))

    if metadata_path.exists():
        print(paint(METADATA_FILE + " already exists, nothing changed", YELLOW))
        return 1
    if entry_path.exists():
        print(paint(ENTRY_FILE + " already exists, nothing changed", YELLOW))
        return 1

    mod_id = slugify(root.name)
    metadata = {
        "id": mod_id,
        "name": mod_id,
        "version": "1.0.0",
        "entry": ENTRY_FILE,
        "hooks": [],
    }

    root.mkdir(parents=True, exist_ok=True)
    src_dir.mkdir(parents=True, exist_ok=True)
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    entry_path.write_text(entry_template(mod_id), encoding="utf-8")

    written = [METADATA_FILE, ENTRY_FILE]
    if not sample_path.exists():
        sample_path.write_text(SAMPLE_ELEMENT_TEMPLATE, encoding="utf-8")
        written.append(SRC_DIR + "/" + SAMPLE_ELEMENT_FILE)

    console.debug("mod id: " + mod_id)
    for name in written:
        console.debug("wrote " + str(root / name))

    print(paint("Initialized mod project: " + mod_id, GREEN))
    print("  " + METADATA_FILE)
    print("  " + ENTRY_FILE)
    print("  " + SRC_DIR + "/")
    for name in written[2:]:
        print("  " + name)
    return 0
