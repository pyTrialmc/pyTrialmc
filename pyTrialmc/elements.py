"""Element catalog of the pyTrial workspace.

Every kind listed here is reachable from a mod through ``mod.add.<kind>(name)``.
A kind is *active* when the loader already bridges it into the game; the other
kinds are declared and reported by the loader until their bridge lands.
"""

ELEMENT_KINDS = (
    "advancement",
    "armor",
    "armor_trim",
    "attribute",
    "banner_pattern",
    "biome",
    "block",
    "command",
    "creative_tab",
    "custom_element",
    "damage_type",
    "dimension",
    "enchantment",
    "feature",
    "fluid",
    "function",
    "gui",
    "game_rule",
    "item",
    "item_extension",
    "key_binding",
    "living_entity",
    "loot_table",
    "overlay",
    "painting",
    "particle",
    "plant",
    "potion_effect",
    "potion_item",
    "procedure",
    "projectile",
    "recipe",
    "special_entity",
    "structure",
    "tool",
    "villager_profession",
    "villager_trade",
)

ELEMENT_LABELS = {
    "advancement": "Advancement",
    "armor": "Armor",
    "armor_trim": "Armor trim",
    "attribute": "Attribute",
    "banner_pattern": "Banner pattern",
    "biome": "Biome",
    "block": "Block",
    "command": "Command",
    "creative_tab": "Creative tab",
    "custom_element": "Custom element",
    "damage_type": "Damage type",
    "dimension": "Dimension",
    "enchantment": "Enchantment",
    "feature": "Feature",
    "fluid": "Fluid",
    "function": "Function",
    "gui": "GUI",
    "game_rule": "Game rule",
    "item": "Item",
    "item_extension": "Item extension",
    "key_binding": "Key binding",
    "living_entity": "Living entity",
    "loot_table": "Loot table",
    "overlay": "Overlay",
    "painting": "Painting",
    "particle": "Particle",
    "plant": "Plant",
    "potion_effect": "Potion effect",
    "potion_item": "Potion item",
    "procedure": "Procedure",
    "projectile": "Projectile",
    "recipe": "Recipe",
    "special_entity": "Special entity",
    "structure": "Structure",
    "tool": "Tool",
    "villager_profession": "Villager profession",
    "villager_trade": "Villager trade",
}

# ---------------------------------------------------------------------------
# 每种元素由谁桥接进游戏
#
#   data     —— 生成为一个数据包文件（data/<ns>/<folder>/<path>.json）
#   registry —— 反射注册进原版注册表（BuiltInRegistries）
#   pending  —— 需要 Java 侧生成类或客户端钩子，尚未接通
# ---------------------------------------------------------------------------

DATA_KINDS = (
    "advancement",
    "armor_trim",
    "banner_pattern",
    "biome",
    "damage_type",
    "dimension",
    "enchantment",
    "feature",
    "function",
    "loot_table",
    "painting",
    "recipe",
    "structure",
    "villager_profession",
    "villager_trade",
)

REGISTRY_KINDS = (
    "armor",
    "attribute",
    "block",
    "command",
    "creative_tab",
    "custom_element",
    "game_rule",
    "item",
    "item_extension",
    "living_entity",
    "particle",
    "plant",
    "potion_effect",
    "potion_item",
    "projectile",
    "special_entity",
    "tool",
)

#: 纯 Python 侧的概念，不经过 Java 桥接
PYTHON_KINDS = ("procedure",)

PENDING_KINDS = {
    "fluid": "需要一个 Fluid 子类，等 Java 侧生成类之后接通",
    "gui": "客户端界面，等 Screen 桥接",
    "key_binding": "客户端按键绑定，等输入桥接",
    "overlay": "客户端叠加层，等渲染钩子",
}

ACTIVE_KINDS = DATA_KINDS + REGISTRY_KINDS + PYTHON_KINDS

#: 直接可用的 ``runtime.register_<kind>`` 函数名，供 source.py 派发
SUPPORTED_FUNCTIONS = {
    "advancement": "register_advancement",
    "biome": "register_biome",
    "block": "register_block",
    "creative_tab": "register_creative_tab",
    "dimension": "register_dimension",
    "enchantment": "register_enchantment",
    "feature": "register_feature",
    "item": "register_item",
    "living_entity": "register_entity",
    "loot_table": "register_loot_table",
    "particle": "register_particle",
    "potion_effect": "register_potion_effect",
    "recipe": "register_recipe",
}

RESERVED_OPTIONS = {
    "command": ("description", "aliases"),
}


def label(kind):
    return ELEMENT_LABELS.get(str(kind), str(kind))


def is_kind(kind):
    return str(kind) in ELEMENT_KINDS


def is_active(kind):
    return str(kind) in ACTIVE_KINDS


def options_for(kind, options):
    """Split the loader reserved options from the element options."""
    reserved = {}
    rest = dict(options)
    for key in RESERVED_OPTIONS.get(str(kind), ()):
        if key in rest:
            reserved[key] = rest.pop(key)
    return reserved, rest


def bridge(kind):
    """How a kind reaches the game: ``data``, ``registry``, ``python``, ``pending`` or ``None``."""
    kind = str(kind)
    if kind in DATA_KINDS:
        return "data"
    if kind in REGISTRY_KINDS:
        return "registry"
    if kind in PYTHON_KINDS:
        return "python"
    if kind in PENDING_KINDS:
        return "pending"
    return None


def pending_reason(kind):
    """Why a kind is not bridged yet, or ``None`` when it is."""
    return PENDING_KINDS.get(str(kind))
