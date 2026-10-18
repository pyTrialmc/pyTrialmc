import sys
from pathlib import Path

from . import __version__
from .build import build_package
from .color import BOLD, CYAN, DIM, RED, paint
from .console import console, is_debug, set_debug
from .project import init_project

DEBUG_FLAG = "-debug"


def usage_text():
    lines = [
        paint("pyTrialmc " + __version__, BOLD, CYAN),
        "",
        paint("Usage:", BOLD),
        "  pytrialmc -init                 " + paint("create a mod project here (main.py + src/)", DIM),
        "  pytrialmc -b:<game version>     " + paint("build a loadable jar", DIM),
        "  pytrialmc -debug                " + paint("print debug output", DIM),
        "",
        paint("Examples:", BOLD),
        "  pytrialmc -init",
        "  pytrialmc -b:1.21.8",
        "  pytrialmc -b:1.21.8 -debug",
        "",
        paint("Environment:", BOLD),
        "  PYTRIALMC_DEBUG=1   " + paint("same as -debug", DIM),
        "  PYTRIALMC_COLOR=1   " + paint("force colors, 0 to disable", DIM),
        "  NO_COLOR=1          " + paint("disable colors", DIM),
    ]
    return "\n".join(lines)


def main(argv=None):
    args = list(sys.argv[1:] if argv is None else argv)

    if DEBUG_FLAG in args:
        args.remove(DEBUG_FLAG)
        set_debug(True)

    console.debug("argv: " + repr(args))
    console.debug("python: " + sys.version.split()[0])
    console.debug("cwd: " + str(Path.cwd()))
    console.debug("colors: " + str(paint("", BOLD) != ""))

    if not args or args[0] in ("-h", "--help", "help"):
        print(usage_text())
        return 0

    command = args[0]
    console.debug("command: " + command)

    if command == "-init":
        return init_project(Path.cwd())

    if command.startswith("-b:"):
        return build_package(Path.cwd(), command[len("-b:"):])

    print(paint("Unknown option: " + command, RED))
    print(usage_text())
    return 1


if __name__ == "__main__":
    sys.exit(main())
