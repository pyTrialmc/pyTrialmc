import os
import sys

RESET = "\033[0m"
BOLD = "\033[1m"
DIM = "\033[2m"
RED = "\033[31m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
BLUE = "\033[34m"
MAGENTA = "\033[35m"
CYAN = "\033[36m"
GREY = "\033[90m"

_DISABLED = {"0", "false", "off", "never", "no"}
_ENABLED = {"1", "true", "on", "always", "force", "yes"}


def enabled(stream=None):
    if os.environ.get("NO_COLOR"):
        return False
    override = os.environ.get("PYTRIALMC_COLOR", "").strip().lower()
    if override in _DISABLED:
        return False
    if override in _ENABLED:
        return True
    target = sys.stdout if stream is None else stream
    return bool(getattr(target, "isatty", lambda: False)())


def paint(text, *styles, stream=None):
    if not styles or not enabled(stream):
        return str(text)
    return "".join(styles) + str(text) + RESET
