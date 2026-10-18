import os

from . import runtime
from .color import CYAN, GREY, paint

_TRUTHY = {"1", "true", "on", "yes", "always"}

_debug = os.environ.get("PYTRIALMC_DEBUG", "").strip().lower() in _TRUTHY


def set_debug(value):
    global _debug
    _debug = bool(value)
    return _debug


def is_debug():
    return _debug


def _join(args):
    return " ".join(str(arg) for arg in args)


def _write(text, color):
    if runtime.is_bound():
        runtime.log(text)
    else:
        print(paint(text, color))


class _Console:

    def log(self, *args):
        _write(_join(args), CYAN)

    def debug(self, *args):
        if _debug:
            _write("[debug] " + _join(args), GREY)


console = _Console()
