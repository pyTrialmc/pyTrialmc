# pyTrial 架构

pyTrial 是一个**独立加载器**：不依赖 Forge / Fabric，自己接管 Minecraft 的启动流程并做字节码注入。

## 模块

```
pyTrial/
├── bootstrap/        命令行启动器（跑在游戏进程之外）
│   ├── launch/       定位 .minecraft、解析 version json、拼 classpath 与启动参数
│   └── install/      建立实例目录布局、生成 launch 脚本
└── loader/           javaagent 加载器（挂载进游戏进程）
    ├── agent/        premain / agentmain 入口
    ├── transform/    ASM 字节码注入管线
    ├── bridge/      注入代码的运行时入口（HookDispatcher）
    ├── discovery/   扫描 mods 目录
    ├── metadata/    解析 pytrial.mod.json
    ├── resolve/     依赖检查与拓扑排序
    ├── lifecycle/   mod 状态、注册表、加载阶段
    ├── event/       事件总线
    ├── engine/      Python 引擎抽象（**实现留空**）
    └── api/         对外门面与 mod 上下文
```

## 启动流程

```
用户 / 启动器
    │  java -jar pytrial-bootstrap.jar launch
    ▼
BootstrapMain
    ├─ MinecraftLocator     找到 .minecraft
    ├─ VersionResolver      读 versions/<id>/<id>.json
    ├─ ClasspathBuilder     解析 libraries + 抽取 natives
    ├─ ArgumentBuilder      展开 ${...} 变量、求值 rules
    └─ GameLauncher         拼出命令行并 spawn 游戏进程
             │
             │  java -javaagent:pytrial-loader.jar=--game-dir=... -cp ... net.minecraft.client.main.Main
             ▼
        PyTrialAgent.premain                                        ← 阶段一
            └─ PyTrialKernel.prepare
                 ├─ 扫描 mods → 解析元数据 → 依赖解析 + 排序
                 ├─ 汇总所有 hook 规则（含加载器自带的 game_start 钩子）
                 ├─ TransformPipeline → Instrumentation.addTransformer
                 └─ HookDispatcher.install(kernel)
             │
             ▼
        Main.main  ← 字节码里被插入了 HookDispatcher.onHead("pytrial:game_start")
            └─ PyTrialKernel.onGameStart                            ← 阶段二
                 ├─ PythonEngineFactory.discover()  （目前返回空）
                 ├─ 有引擎 → 逐个 loadModule / on_load
                 └─ 无引擎 → 全部标记 DISABLED，游戏照常运行
```

**为什么要分两阶段**：字节码转换器必须在目标类被加载**之前**注册，而这只能发生在 `premain`；
但 Python 引擎的初始化要等到 JVM 完整跑起来之后才安全，所以拆成两步。

## 字节码注入

`AsmHookTransformer` 把 hook 规则翻译成对 `HookDispatcher` 的静态调用：

| `at` | 插入位置 | 注入的调用 |
| --- | --- | --- |
| `HEAD` | 方法体开头 | `HookDispatcher.onHead(id)` |
| `RETURN` | 每个 return 之前 | `HookDispatcher.onReturn(id)` |
| `BEFORE_INVOKE` | 指定调用之前 | `HookDispatcher.onBeforeInvoke(id)` |
| `AFTER_INVOKE` | 指定调用之后 | `HookDispatcher.onAfterInvoke(id)` |

插入序列是 `LDC id` + `INVOKESTATIC (String)V`，净栈效应为 0，
因此不会破坏原有表达式栈和校验器帧 —— 写回时只开 `COMPUTE_MAXS`，
避免 `COMPUTE_FRAMES` 触发的类加载。

`HookDispatcher` 必须吞掉所有异常，并且不持有 Minecraft 类的强引用。

## 事件

`EventBus` 是加载器自身的事件总线（`LoaderInitialized`、`ModDiscovered`、
`ModLoading`、`ModLoaded`、`GameStarting`、`LoaderShutdown`）。
游戏内事件不走事件总线，而是走上面的字节码 hook 通道。

## 当前未实现（TODO）

- `PythonEngine` / `PythonRuntime` 的具体实现（见 [python-engine.md](python-engine.md)）
- 缺失的 libraries 自动下载（`pyTrial` 目前假定版本已由官方启动器安装过）
- 账户体系：目前只支持离线模式参数，在线登录需要接认证流程
- 把加载器日志接到游戏日志系统（log4j2）而不是标准输出
- hook 读取/修改局部变量与返回值（当前只做通知，不传参、不拦截）
- mod 热重载、`retransform` 已有类的支持
- mod 的 Jar/zip 打包形式（目前只支持目录与单文件 `.py`）
