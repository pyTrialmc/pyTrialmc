import importlib.util
import json
import sys

_emit = None
_module = None
_context = None


def bind(emit):
    global _emit
    _emit = emit


def is_bound():
    return _emit is not None


def current_context():
    return _context


def current_module():
    return _module


def current_entry():
    if _module is None:
        return None
    return getattr(_module, "__file__", None)


def _send(payload):
    if _emit is not None:
        _emit(payload)


def log(message):
    _send({"event": "log", "message": str(message)})


def warn(message):
    _send({"event": "log", "message": "WARN " + str(message)})


def error(message):
    _send({"event": "log", "message": "ERROR " + str(message)})


def register(kind, **options):
    message = {"event": "register", "kind": str(kind)}
    message.update(options)
    _send(message)


def register_block(namespace, path, **options):
    message = {"event": "register_block", "namespace": str(namespace), "path": str(path)}
    message.update(options)
    _send(message)


def register_item(namespace, path, **options):
    message = {"event": "register_item", "namespace": str(namespace), "path": str(path)}
    message.update(options)
    _send(message)


def register_command(name, description="", aliases=None, **options):
    message = {"event": "register_command", "name": str(name), "description": str(description)}
    if aliases:
        message["aliases"] = [str(alias) for alias in aliases]
    message.update(options)
    _send(message)


def register_block_entity(namespace, path, **options):
    _registration("block_entity", namespace, path, options)


def register_entity(namespace, path, **options):
    _registration("entity", namespace, path, options)


def register_menu(namespace, path, **options):
    _registration("menu", namespace, path, options)


def register_creative_tab(namespace, path, **options):
    _registration("creative_tab", namespace, path, options)


def register_sound(namespace, path, **options):
    _registration("sound", namespace, path, options)


def register_particle(namespace, path, **options):
    _registration("particle", namespace, path, options)


def register_recipe(namespace, path, **options):
    _registration("recipe", namespace, path, options)


def register_advancement(namespace, path, **options):
    _registration("advancement", namespace, path, options)


def register_loot_table(namespace, path, **options):
    _registration("loot_table", namespace, path, options)


def register_biome(namespace, path, **options):
    _registration("biome", namespace, path, options)


def register_dimension(namespace, path, **options):
    _registration("dimension", namespace, path, options)


def register_feature(namespace, path, **options):
    _registration("feature", namespace, path, options)


def register_enchantment(namespace, path, **options):
    _registration("enchantment", namespace, path, options)


def register_potion_effect(namespace, path, **options):
    _registration("potion_effect", namespace, path, options)


def register_gui(gui_id, **options):
    message = {"event": "register_gui", "id": str(gui_id)}
    message.update(options)
    _send(message)


def open_gui(gui_id, **options):
    message = {"event": "open_gui", "id": str(gui_id)}
    message.update(options)
    _send(message)


def close_gui():
    _send({"event": "close_gui"})


def _registration(kind, namespace, path, options):
    message = {"event": "register", "kind": kind, "namespace": str(namespace), "path": str(path)}
    message.update(options)
    _send(message)


_handlers = {}


def on(event, handler):
    _handlers.setdefault(str(event), []).append(handler)
    return handler


def off(event, handler):
    handlers = _handlers.get(str(event))
    if handlers and handler in handlers:
        handlers.remove(handler)


def register_procedure(namespace, path, event=None, **options):
    """Bind a module function to the event bus.

    ``register_procedure("mymod", "on_tick", event="tick")`` attaches the module level function
    ``on_tick`` to the ``tick`` event. Without ``event`` the function name is the event id.
    """
    name = str(path)
    module = current_module()
    handler = getattr(module, name, None) if module is not None else None
    if not callable(handler):
        warn("procedure " + name + " 没有同名函数，已跳过")
        return None
    event_id = event or name
    on(event_id, handler)
    log("procedure " + name + " 绑定到事件 " + str(event_id))
    return handler


def _dispatch(event_id, payload):
    for handler in list(_handlers.get(str(event_id), [])):
        try:
            handler(payload)
        except Exception as failure:
            warn("event handler for " + str(event_id) + " failed: " + str(failure))


class Context:

    def __init__(self, data):
        self.mod_id = data.get("id")
        self.version = data.get("version")
        self.minecraft_version = data.get("minecraftVersion")
        self.root = data.get("root")
        self.config_dir = data.get("config")
        self.cache_dir = data.get("cache")

    def log(self, message):
        log("[" + str(self.mod_id) + "] " + str(message))

    def warn(self, message):
        log("[" + str(self.mod_id) + "] WARN " + str(message))

    def error(self, message):
        log("[" + str(self.mod_id) + "] ERROR " + str(message))


def _load(message):
    global _module, _context
    _context = Context(message)
    name = "pytrial_mod_" + str(message.get("id"))
    spec = importlib.util.spec_from_file_location(name, message.get("entry"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    _module = module

    if message.get("phase") == "declare":
        callback = getattr(module, "on_declare", None)
        if callable(callback):
            callback(_context)
        _send({"event": "declared", "id": message.get("id")})
        return

    _activate(message)


def _activate(message):
    if _module is None:
        _send({"event": "error", "id": message.get("id"), "message": "module is not loaded"})
        return
    callback = getattr(_module, "on_load", None)
    if callable(callback):
        callback(_context)
    _send({"event": "loaded", "id": message.get("id")})


def _hook(message):
    if _module is None:
        return
    callback = getattr(_module, "on_hook", None)
    if callable(callback):
        callback(message.get("id"), message.get("args") or [])


def _event(message):
    _dispatch(message.get("id"), message.get("payload"))
    if _module is None:
        return
    callback = getattr(_module, "on_event", None)
    if callable(callback):
        callback(message.get("id"), message.get("payload"))


def _unload():
    global _module, _context
    if _module is not None:
        callback = getattr(_module, "on_unload", None)
        if callable(callback):
            callback()
    _module = None
    _context = None
    _reset_sources()
    _send({"event": "unloaded"})


def _reset_sources():
    try:
        from . import source
        source.reset()
    except Exception:
        pass


def handle(message):
    kind = message.get("kind")
    try:
        if kind == "load":
            _load(message)
        elif kind == "activate":
            _activate(message)
        elif kind == "hook":
            _hook(message)
        elif kind == "event":
            _event(message)
        elif kind == "unload":
            _unload()
    except Exception as error:
        _send({"event": "error", "id": message.get("id"), "message": str(error)})


def serve():
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            handle(json.loads(line))
        except Exception as error:
            _send({"event": "error", "message": str(error)})


def bootstrap(library_path):
    if library_path:
        sys.path.insert(0, library_path)
    bind(lambda payload: sys.stdout.write(json.dumps(payload, ensure_ascii=False) + "\n") or sys.stdout.flush())
    serve()


# ---------------------------------------------------------------------------
# 其余元素类型的注册函数
#
# 这些函数和上面的手写版本做同一件事：把一次声明发成一条 register_<kind> 消息。
# 内容本身的字段原样透传，所以 mod 想写什么原版字段就写什么。
# ---------------------------------------------------------------------------

_GENERATED_HELPERS = (
    "armor",
    "armor_trim",
    "attribute",
    "banner_pattern",
    "custom_element",
    "damage_type",
    "function",
    "game_rule",
    "item_extension",
    "item_modifier",
    "painting",
    "plant",
    "potion_item",
    "projectile",
    "special_entity",
    "structure",
    "tag",
    "tool",
    "villager_profession",
    "villager_trade",
)


def _make_helper(kind):
    def helper(namespace, path, **options):
        message = {"event": "register_" + kind, "namespace": str(namespace), "path": str(path)}
        message.update(options)
        _send(message)

    helper.__name__ = "register_" + kind
    helper.__qualname__ = "register_" + kind
    helper.__doc__ = "Register one " + kind + " element: " + kind + "(namespace, path, **options)."
    return helper


KIND_HELPERS = tuple("register_" + kind for kind in _GENERATED_HELPERS)

for _kind in _GENERATED_HELPERS:
    globals()["register_" + _kind] = _make_helper(_kind)

del _kind
