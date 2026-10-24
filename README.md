# pyTrialmc

Write Minecraft Java Edition mods in **Python**.

`pyTrialmc` is the Python library of the pyTrial loader. Your mod imports it, and
it ships the command line tool that creates, declares and packages mod projects.

| | |
| --- | --- |
| Release | 26 October 07 |
| Version | 2026.10.7 |
| Requires | Python 3.9 or newer |

## Install

```bash
pip install pyTrialmc
```

## Create a project

```bash
cd my_mod
pytrialmc -init
```

`-init` writes the project skeleton in the current directory:

```
pytrial.mod.json     mod metadata read by the loader
main.py              the mod entry file
src/                 one file per element
└── example_block.py
```

`main.py` is the entry file the loader executes:

```python
from pyTrialmc import *

mod = modsrc()

# Declare one element per line, every element is implemented by src/<name>.py.
# Any kind from ELEMENT_KINDS is available as mod.add.<kind>(name, **options).
# mod.add.block("example_block")
# mod.add.item("example_ingot", stack_size=64)

mod.main()
modmain()
```

## Declare elements

`mod.add` carries one method per element kind, named after the kind. The first
argument is the element name, and it also names the implementation file in `src/`.

```python
mod.add.block("ruby_block", hardness=4.0)
mod.add.item("ruby_ingot", stack_size=64)
mod.add.creative_tab("ruby_tab", title="Ruby", icon="ruby_mod:ruby_ingot")
mod.add.command("ruby", description="Ruby command", aliases=["rb"])
```

The catalog covers 37 kinds:

| | Kinds |
| --- | --- |
| | advancement, armor, armor_trim, attribute, banner_pattern, biome, block, command, creative_tab, custom_element, damage_type, dimension, enchantment, feature, fluid, function, gui, game_rule |
| | item, item_extension, key_binding, living_entity, loot_table, overlay, painting, particle, plant, potion_effect, potion_item, procedure, projectile, recipe, special_entity, structure, tool, villager_profession, villager_trade |

Every kind is bridged, in one of four ways:

| Bridge | Kinds | What happens |
| --- | --- | --- |
| data pack | advancement, armor_trim, banner_pattern, biome, damage_type, dimension, enchantment, feature, function, loot_table, painting, recipe, structure, villager_profession, villager_trade | the loader writes `data/<ns>/<folder>/<path>.json` into a generated data pack, so the element is a real recipe, loot table, advancement or worldgen entry |
| registry | armor, attribute, block, command, creative_tab, custom_element, game_rule, item, item_extension, living_entity, particle, plant, potion_effect, potion_item, projectile, special_entity, tool | the loader registers a real object in the vanilla registry |
| python | procedure | the declared name is bound to a module function and attached to the event bus |
| pending | fluid, gui, key_binding, overlay | needs a generated Java class or a client hook; the loader logs the exact reason instead of failing |

Options are passed through untouched: `mod.add.recipe(...)` fields become the
recipe JSON, `mod.add.tool(...)` components become item data components. A kind
that cannot be bridged never fails the load — it logs one line and continues.

### Element files

One element per file, file name equal to the declared name, so
`mod.add.block("ruby_block")` is implemented by `src/ruby_block.py`:

```python
OPTIONS = {"hardness": 2.5, "light": 4}


def configure(element):
    element.options["resistance"] = 9.0
```

Option priority: arguments passed to `mod.add.*` > `configure()` > `OPTIONS`.

## Lifecycle hooks

The loader may also call these, if your entry file defines them:

```python
def on_declare(ctx): ...            # registration phase
def on_load(ctx): ...               # everything is registered
def on_event(event_id, payload): ...# game or loader events
def on_hook(hook_id, args): ...     # bytecode hook callbacks
def on_unload(): ...                # the mod is going away
```

`ctx` carries `mod_id`, `version`, `minecraft_version`, `root`, `config_dir`
and `cache_dir`.

## Package the mod

```bash
pytrialmc -b:1.21.8
```

That writes `dist/<modId>-<modVersion>.pytm`. Copy the package into
`<gameDir>/pytrial/mods/`.

## Command line

```
pytrialmc -init                 create a mod project here (main.py + src/)
pytrialmc -b:<game version>     build a loadable jar
pytrialmc -debug                print debug output
```

Environment: `PYTRIALMC_DEBUG=1`, `PYTRIALMC_COLOR=1`, `NO_COLOR=1`.

## The loader

The Python library only declares content. The loader and the launcher are the
Java side of pyTrial: an independent loader that starts Minecraft itself through a
Java agent, without Forge or Fabric, then runs each mod in a Python process and
registers the declared content into the game.

```bash
java -jar pytrial-bootstrap.jar install   # install into the game directory
java -jar pytrial-bootstrap.jar launch    # start the game with pyTrial
java -jar pytrial-bootstrap.jar mods      # list the discovered Python mods
```

Installing creates `<gameDir>/pytrial/` with `loader/`, `mods/`, `config/`,
`cache/` and launch scripts.

The client installer bundles the loader, so it is the only file a player needs:
run `pytrial-client-installer-27.jar` and pick the game directory. Both the
installer and the loader require a non-obfuscated client, that is Minecraft
26.0 or newer.

## License

Apache License 2.0. See [LICENSE](LICENSE).
