"""The mod source: ``mod = modsrc()`` and the ``mod.add`` element namespace."""

import importlib.util
import re
import sys
from pathlib import Path

from . import elements
from . import runtime
from .console import console
from .project import slugify

OPTIONS_NAME = "OPTIONS"
CONFIGURE_NAME = "configure"
RESOURCE_ID = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")


class Element:
    """One declared element, implemented by ``src/<name>.py`` when it exists."""

    def __init__(self, kind, name, namespace, options=None):
        self.kind = str(kind)
        self.name = str(name)
        self.namespace = str(namespace)
        self.options = dict(options or {})
        self.path = None
        self.module = None

    @property
    def identifier(self):
        return self.namespace + ":" + self.name

    def __repr__(self):
        return "Element(" + self.kind + ", " + self.identifier + ")"


class ElementAdder:
    """``mod.add`` namespace, one method per element kind."""

    def __init__(self, source):
        self._source = source

    def _declare(self, kind, name, options):
        return self._source.declare(kind, name, options)


def _element_method(kind):
    def method(self, name, **options):
        return self._declare(kind, name, options)

    method.__name__ = kind
    method.__qualname__ = "ElementAdder." + kind
    method.__doc__ = "Declare one " + kind + " element: " + kind + "(name, **options)."
    return method


for _kind in elements.ELEMENT_KINDS:
    setattr(ElementAdder, _kind, _element_method(_kind))
del _kind


class ModSource:
    """The variable returned by ``modsrc()``."""

    def __init__(self, mod_id, root):
        self.id = str(mod_id)
        self.root = Path(root) if root else None
        self.elements = []
        self.add = ElementAdder(self)
        self._finalized = False

    @property
    def src_dir(self):
        return self.root / "src" if self.root else None

    def declare(self, kind, name, options):
        kind = str(kind)
        if not elements.is_kind(kind):
            runtime.warn("unknown element kind: " + kind)
            return None
        if self._finalized:
            runtime.warn("element " + str(name) + " declared after main(), ignored")
            return None

        element = Element(kind, slugify(str(name)), self.id, options)
        self._apply_implementation(element)
        self.elements.append(element)
        self._send(element)
        return element

    def main(self):
        if self._finalized:
            return self
        self._finalized = True
        runtime.log("mod " + self.id + ": " + str(len(self.elements)) + " element(s) declared")
        return self

    def _apply_implementation(self, element):
        """Load src/<name>.py so the element file can supply its own options."""
        directory = self.src_dir
        if directory is None:
            return
        path = directory / (element.name + ".py")
        if not path.is_file():
            console.debug("no implementation file for " + element.identifier + ": " + str(path))
            return

        module_name = "pytrial_element_" + self.id + "_" + element.name
        spec = importlib.util.spec_from_file_location(module_name, str(path))
        if spec is None or spec.loader is None:
            runtime.warn("cannot load element file: " + str(path))
            return

        module = importlib.util.module_from_spec(spec)
        sys.modules[module_name] = module
        spec.loader.exec_module(module)
        element.path = path
        element.module = module

        defaults = getattr(module, OPTIONS_NAME, None)
        if isinstance(defaults, dict):
            for key, value in defaults.items():
                element.options.setdefault(key, value)

        configure = getattr(module, CONFIGURE_NAME, None)
        if callable(configure):
            configure(element)

    def _resolve_texture(self, options):
        """Turns a relative texture path into an absolute one; ids and URLs stay untouched."""
        texture = options.get("texture")
        if not isinstance(texture, str) or not texture.strip():
            return
        texture = texture.strip()
        if texture.startswith(("http://", "https://")) or RESOURCE_ID.match(texture):
            options["texture"] = texture
            return
        if self.root:
            options["texture"] = str((self.root / texture).resolve())
        else:
            options["texture"] = texture

    def _send(self, element):
        self._resolve_texture(element.options)
        reserved, options = elements.options_for(element.kind, element.options)
        if element.kind == "command":
            runtime.register_command(
                element.name,
                reserved.get("description", ""),
                reserved.get("aliases"),
                **options
            )
            return

        function_name = elements.SUPPORTED_FUNCTIONS.get(element.kind)
        if function_name is not None:
            getattr(runtime, function_name)(element.namespace, element.name, **options)
            return

        runtime.register(element.kind, namespace=element.namespace, path=element.name, **options)


_sources = {}


def _identity():
    context = runtime.current_context()
    mod_id = getattr(context, "mod_id", None)
    root = getattr(context, "root", None)
    entry = runtime.current_entry()
    if not root and entry:
        root = str(Path(entry).parent)
    if not mod_id:
        mod_id = Path(root).name if root else "mod"
    return str(mod_id), root


def modsrc():
    """Return the source of the running mod, creating it on first use."""
    mod_id, root = _identity()
    source = _sources.get(mod_id)
    if source is None:
        source = ModSource(mod_id, root)
        _sources[mod_id] = source
    return source


def modmain():
    """Finish the declaration phase of the running mod (idempotent)."""
    return modsrc().main()


def declared_elements():
    source = _sources.get(_identity()[0])
    return list(source.elements) if source else []


def reset():
    _sources.clear()
