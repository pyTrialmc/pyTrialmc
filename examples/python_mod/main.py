import base64
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
    """把 37 种元素各声明一次，这份文件可以直接当参考手册看。

    四条落地路径：
      注册表 —— 反射注册进 BuiltInRegistries
      数据包 —— 写成 data/<ns>/<folder>/<path>.json（function 是 .mcfunction，structure 是 .nbt）
      python —— 绑定本文件里的同名函数
      界面   —— 运行时生成 Screen
    options 全程原样透传，所以数据包类的字段就是原版 JSON 本身。
    """
    ctx.log("declaring all 37 element kinds for minecraft " + str(ctx.minecraft_version))

    # ==================== 注册表类（19） ====================

    # texture 可以是原版贴图（minecraft:block/gold_block）、URL，或 mod 目录里的文件；
    # 不写就用 pyTrial 生成的贴图。
    register_block(MOD_ID, "lucky_block", hardness=3.0, resistance=6.0,
                   texture="minecraft:block/gold_block")
    register_item(MOD_ID, "lucky_ingot", stack_size=64)
    register_tool(MOD_ID, "lucky_pickaxe", durability=880,
                  components={"max_stack_size": 1})
    register_armor(MOD_ID, "lucky_helmet", durability=275,
                   components={"max_stack_size": 1})
    register_plant(MOD_ID, "lucky_flower")
    register_potion_item(MOD_ID, "lucky_draught")
    # item_extension 派生一个带新组件的物品（原物品保持不变）
    register_item_extension(MOD_ID, "lucky_ingot_big", components={"max_stack_size": 16})
    register_particle(MOD_ID, "lucky_spark", alwaysShow=True)
    register_attribute(MOD_ID, "lucky_luck", default=0.0, min=0.0, max=10.0)
    register_potion_effect(MOD_ID, "lucky_glow", color=0x00C853, category="BENEFICIAL")
    register_game_rule(MOD_ID, "lucky_drops", type="boolean", default=True)
    # fluid 默认复用原版水的两个内部类，自带类就用 sourceClass / flowingClass
    register_fluid(MOD_ID, "lucky_water")
    # key 是 GLFW 键码，71 是 G
    register_key_binding(MOD_ID, "open_panel", key=71, category="pymod")
    # living_entity / projectile / special_entity 默认复用原版实现，可用 entityClass 指定自己的类
    register_entity(MOD_ID, "lucky_golem", health=20.0, speed=0.25)
    register_projectile(MOD_ID, "lucky_arrow")
    register_special_entity(MOD_ID, "lucky_stand")
    # 逃生舱：任意注册表 + 任意无参构造的类
    register_custom_element(MOD_ID, "lucky_flowing", registry="fluid",
                            class_name="net.minecraft.world.level.material.WaterFluid$Flowing")
    register_creative_tab(MOD_ID, "lucky_tab", title="Lucky Blocks",
                          icon=MOD_ID + ":lucky_block",
                          items=[MOD_ID + ":lucky_block", MOD_ID + ":lucky_ingot"])
    register_command(MOD_ID, "python mod command", aliases=["pm"])

    # ==================== 数据包类（15） ====================

    register_recipe(MOD_ID, "lucky_ingot_from_block",
                    type="minecraft:crafting_shapeless",
                    ingredients=[{"item": MOD_ID + ":lucky_block"}],
                    result={"id": MOD_ID + ":lucky_ingot", "count": 9})
    register_loot_table(MOD_ID, "lucky_block_drops",
                        type="minecraft:block",
                        pools=[{"rolls": 1,
                                "entries": [{"type": "minecraft:item",
                                             "name": MOD_ID + ":lucky_ingot"}]}])
    register_advancement(MOD_ID, "lucky_start",
                         display={"icon": {"id": MOD_ID + ":lucky_block"},
                                  "title": "Lucky Start",
                                  "description": "Craft a lucky block"},
                         criteria={"got_block": {"trigger": "minecraft:inventory_changed"}})
    register_function(MOD_ID, "lucky_say",
                      content="say lucky\ntellraw @a {\"text\":\"pyTrial\"}\n")
    register_biome(MOD_ID, "lucky_forest", temperature=0.8, downfall=0.4)
    register_dimension(MOD_ID, "lucky_dimension")
    register_feature(MOD_ID, "lucky_tree")
    register_enchantment(MOD_ID, "lucky_fortune", max_level=3)
    register_damage_type(MOD_ID, "lucky_bite", exhaustion=0.1, message_id="lucky_bite")
    register_banner_pattern(MOD_ID, "lucky_stripe", asset_id="lucky_stripe")
    register_armor_trim(MOD_ID, "lucky_trim", template_item=MOD_ID + ":lucky_ingot")
    register_painting(MOD_ID, "lucky_painting", asset_id="lucky_painting")
    register_villager_trade(MOD_ID, "lucky_trade")
    register_villager_profession(MOD_ID, "lucky_job")
    # 结构是压缩过的 NBT，这里把示例目录里的 .nbt 编码后带过去
    register_structure(MOD_ID, "lucky_hut",
                       nbt=base64.b64encode(
                           (Path(ctx.root) / "structures" / "lucky_hut.nbt").read_bytes()
                       ).decode("ascii"))

    # 标签不是 37 种元素之一，但配方和战利品表经常要用
    register_tag(MOD_ID, "lucky_blocks", registry="block",
                 values=[MOD_ID + ":lucky_block"])

    # ==================== python 类（1） ====================
    register_procedure(MOD_ID, "on_lucky_tick", event="tick")

    # ==================== 界面（1） ====================
    register_gui("lucky_panel", title="Lucky Panel",
                 slots=[MOD_ID + ":lucky_block", MOD_ID + ":lucky_ingot"],
                 buttons=["Refresh", "Close"])

    # ==================== 未接通（1） ====================
    # overlay 在 26.3 还没接通，加载时会打印具体原因，声明本身不会失败
    register_overlay(MOD_ID, "lucky_hud", text="Lucky")

    ctx.log("declared 37 element kinds: 19 registry, 15 data pack, 1 python, 1 screen, 1 pending")


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
