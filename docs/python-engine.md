# 接入 Python 引擎（当前留空）

pyTrial 的加载器部分是完整的：扫描、元数据、依赖、生命周期、字节码注入都已经实现。
**唯一留空的是"如何在 JVM 里执行 Python 代码"**，也就是
[`net.pytrial.engine.PythonEngine`](../loader/src/main/java/net/pytrial/engine/PythonEngine.java) 的实现。

在内核日志里你会看到：

```
[pyTrial] WARN  未发现可用的 Python 引擎实现（Python 侧技术实现目前是留空的）
[pyTrial] WARN  已解析 1 个 mod，它们会被标记为 DISABLED：[example_mod]
```

这是预期行为：mod 能被正确发现和解析，但不会被执行。

## 三条可选路线

| 路线 | 优点 | 代价 |
| --- | --- | --- |
| **GraalPy**（推荐） | JVM 内运行，与 Java 对象直接互操作，性能好，支持 Python 3 | 需要 GraalVM 或引入 `org.graalvm.polyglot:python-community`，包体较大 |
| **Jython** | 纯 Java 依赖，接入最简单 | 只支持 Python 2.7 语法，生态基本停滞 |
| **外部 CPython + IPC** | 兼容任意 CPython 版本与 C 扩展 | 跨进程调用延迟高，只适合非即时逻辑；需要管理子进程生命周期 |

## 实现步骤

1. 新建一个模块（例如 `engine-graalpy`），依赖 `net.pytrial:loader`。
2. 实现 `PythonEngine` 与 `PythonRuntime`。
3. 在 `META-INF/services/net.pytrial.engine.PythonEngine` 中写上实现类全限定名。
4. 把实现 jar 与 `pytrial-loader.jar` 放在一起（agent 的 classpath 会带上它们）。

## 骨架示例

```java
package net.pytrial.engine.graalpy;

import net.pytrial.PyTrialEnvironment;
import net.pytrial.discovery.ModCandidate;
import net.pytrial.engine.PythonEngine;
import net.pytrial.engine.PythonRuntime;

import java.nio.file.Path;

public final class GraalPyEngine implements PythonEngine {

    @Override
    public String id() {
        return "graalpy";
    }

    @Override
    public String version() {
        // TODO: 返回真实版本
        return "0.0.0";
    }

    @Override
    public boolean isAvailable() {
        // TODO: 探测 GraalPy 是否可用
        return false;
    }

    @Override
    public void initialize(PyTrialEnvironment environment) {
        // TODO: 创建 Context，把 ctx / 日志函数注入 Python 全局命名空间
    }

    @Override
    public PythonRuntime createRuntime(ModCandidate mod, Path configDir) {
        // TODO: 为每个 mod 建独立 Context（或以模块隔离），预置 sys.path = [mod.root()]
        throw new UnsupportedOperationException("尚未实现");
    }

    @Override
    public void close() {
        // TODO: 关闭 Context / 释放资源
    }
}
```

`PythonRuntime` 需要落地的语义（与 [mod-format.md](mod-format.md) 的契约一一对应）：

| 方法 | Python 侧 |
| --- | --- |
| `loadModule()` | 执行入口文件，模块顶层代码运行 |
| `onLoad()` | 调用 `on_load(ctx)` |
| `onUnload()` | 调用 `on_unload()` |
| `callHook(id, args)` | 调用 `on_hook(hook_id, args)` |
| `postEvent(id, payload)` | 调用 `on_event(event_id, payload)` |

## 注意事项

- **不要阻塞游戏主线程**：`callHook` 由注入点在游戏线程上同步调用，
  重量级逻辑应投递到自己的线程池。
- **异常必须被吞掉**：`HookDispatcher` 已经做了兜底，但引擎内部也应当把
  Python 异常转换成日志，而不是向上抛。
- **类加载器**：agent 与 Minecraft 类由同一个 classpath 类加载器加载，
  因此引擎可以正常引用 `net.pytrial.*`；但请避免在 `PythonEngine` 中
  强引用 `net.minecraft.*`，那些类只在游戏启动后才存在。
