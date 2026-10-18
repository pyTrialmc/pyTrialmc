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

ACTIVE_KINDS = (
    "block",
    "item",
    "creative_tab",
    "command",
)

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
