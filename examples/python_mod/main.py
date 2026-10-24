import json
from pathlib import Path

from pyTrialmc import *

MOD_ID = "pymod"
CONFIG_FILE = "example.json"
DEFAULT_SETTINGS = {"greeting": "hello from python", "launches": 0}


def _settings_path(ctx):
    return Path(ctx.config_dir) / CONFIG_FILE


def _load_settings(ctx):
    path = _settings_path(ctx)
    if not path.is_file():
        return dict(DEFAULT_SETTINGS)
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        ctx.warn("settings file is unreadable, falling back to defaults")
        return dict(DEFAULT_SETTINGS)


def _save_settings(ctx, settings):
    _settings_path(ctx).write_text(
        json.dumps(settings, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def on_mod_loaded(payload):
    log("mod_loaded -> " + str(payload))


def on_mods_loaded(payload):
    log("mods_loaded -> " + str(payload))


def on_loader_shutdown(payload):
    log("loader_shutdown -> " + str(payload))


on("mod_loaded", on_mod_loaded)
on("mods_loaded", on_mods_loaded)
on("loader_shutdown", on_loader_shutdown)

console.log("[" + MOD_ID + "] module executed")


def on_declare(ctx):
    ctx.log("declaring content for minecraft " + str(ctx.minecraft_version))

    # --- 注册表类：直接进原版注册表 ---
    # texture 可以是原版贴图（minecraft:block/gold_block）、URL，或 mod 目录里的文件；
    # 不写就用 pyTrial 生成的贴图。
    register_block(MOD_ID, "lucky_block", hardness=3.0, resistance=6.0,
                   texture="minecraft:block/gold_block")
    register_item(MOD_ID, "lucky_ingot", stack_size=64)
    register_tool(MOD_ID, "lucky_pickaxe", durability=880,
                  components={"max_stack_size": 1})
    register_particle(MOD_ID, "lucky_spark")
    register_attribute(MOD_ID, "lucky_luck", default=0.0, min=0.0, max=10.0)
    register_potion_effect(MOD_ID, "lucky_glow", color=0x00C853, category="BENEFICIAL")
    register_game_rule(MOD_ID, "lucky_drops", type="boolean", default=True)
    register_creative_tab(MOD_ID, "lucky_tab", title="Lucky Blocks",
                          icon=MOD_ID + ":lucky_block",
                          items=[MOD_ID + ":lucky_block", MOD_ID + ":lucky_ingot"])
    register_command(MOD_ID, "python mod command", aliases=["pm"])

    # --- 数据包类：写进生成的数据包，字段原样透传 ---
    register_recipe(MOD_ID, "lucky_ingot_from_block",
                    type="minecraft:crafting_shapeless",
                    ingredients=[{"item": MOD_ID + ":lucky_block"}],
                    result={"id": MOD_ID + ":lucky_ingot", "count": 9})
    register_loot_table(MOD_ID, "lucky_block_drops",
                        type="minecraft:block",
                        pools=[{
                            "rolls": 1,
                            "entries": [{"type": "minecraft:item",
                                         "name": MOD_ID + ":lucky_ingot"}],
                        }])
    register_advancement(MOD_ID, "lucky_start",
                         display={"icon": {"id": MOD_ID + ":lucky_block"},
                                  "title": "Lucky Start",
                                  "description": "Craft a lucky block"},
                         criteria={"got_block": {"trigger": "minecraft:inventory_changed"}})
    register_tag(MOD_ID, "lucky_blocks", registry="block",
                 values=[MOD_ID + ":lucky_block"])

    register_fluid(MOD_ID, "lucky_water")
    register_key_binding(MOD_ID, "open_panel", key=71, category="pymod")
    register_gui("lucky_panel", title="Lucky Panel",
                 slots=[MOD_ID + ":lucky_block", MOD_ID + ":lucky_ingot"],
                 buttons=["Refresh", "Close"])

    # --- Python 类：名字对应本文件里的同名函数 ---
    register_procedure(MOD_ID, "on_lucky_tick", event="tick")

    ctx.log("declared 12 elements across the registry, data pack and python bridges")


def on_lucky_tick(payload):
    """procedure：由事件总线调用的普通 Python 函数。"""
    log("lucky tick " + str(payload))


def on_load(ctx):
    console.debug("debug logging is " + ("on" if is_debug() else "off"))

    ctx.log("mod id: " + str(ctx.mod_id) + " " + str(ctx.version))
    ctx.log("mod root: " + str(ctx.root))
    ctx.log("config dir: " + str(ctx.config_dir))
    ctx.log("cache dir: " + str(ctx.cache_dir))

    settings = _load_settings(ctx)
    settings["launches"] = int(settings.get("launches", 0)) + 1
    settings["lastMinecraftVersion"] = ctx.minecraft_version
    _save_settings(ctx, settings)
    ctx.log("settings: " + json.dumps(settings, ensure_ascii=False))

    warn("warn example: this line is a warning")
    error("error example: this line is an error")

    off("mod_loaded", on_mod_loaded)


def on_event(event_id, payload):
    log("event " + str(event_id) + " -> " + str(payload))
    if event_id == "gui_open":
        open_gui("lucky_panel")
        log("opened lucky_panel")
    elif event_id == "gui_close":
        close_gui()
        log("closed lucky_panel")
    elif event_id == "gui_click":
        log("clicked " + str(payload.get("button", "slot")) + " in lucky_panel")


def on_hook(hook_id, args):
    log("hook " + str(hook_id) + " args " + str(args))
    return None


def on_unload():
    console.log("[" + MOD_ID + "] unloading")
