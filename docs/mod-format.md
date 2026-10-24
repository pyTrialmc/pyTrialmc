# pyTrial mod 格式

## 两种布局

```
<gameDir>/pytrial/mods/
├── example_mod/              # 包形式（推荐）
│   ├── pytrial.mod.json      # 元数据
│   ├── main.py               # 入口
│   └── assets/               # 任意资源
└── hello.py                  # 单文件形式
```

以 `_` 或 `.` 开头、以 `.disabled` 结尾的条目会被跳过。

单文件形式没有元数据，mod id 由文件名推导（`hello.py` → `hello`）。

## pytrial.mod.json

```json
{
  "id": "example_mod",
  "name": "示例 Python Mod",
  "version": "1.0.0",
  "description": "一句话说明",
  "authors": ["你的名字"],
  "entry": "main.py",
  "python": ">=3.9",
  "loadAfter": ["other_mod"],
  "depends": [
    "plain_dependency",
    { "id": "versioned_dependency", "version": ">=1.2.0" },
    { "id": "maybe_installed", "version": "*", "optional": true }
  ],
  "hooks": [
    {
      "id": "example_mod.client_tick",
      "target": "net.minecraft.client.Minecraft",
      "method": "tick",
      "descriptor": "()V",
      "at": "HEAD"
    }
  ]
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 否 | 只允许小写字母、数字、`_`、`.`、`-`；缺省时用目录名 |
| `name` | 否 | 展示名，缺省等于 `id` |
| `version` | 否 | 缺省 `1.0.0`，依赖检查用 |
| `entry` | 否 | 入口文件，缺省 `main.py`；不允许越出 mod 目录 |
| `python` | 否 | 期望的 Python 版本，交给引擎判断 |
| `depends` | 否 | 依赖，元素可为字符串或对象；缺失非可选依赖会阻止**全部** mod 加载 |
| `loadAfter` | 否 | 仅影响加载顺序的软依赖 |
| `hooks` | 否 | 字节码注入声明 |

其余字段会原样保留在 `ctx.metadata.extra` 中。

### hooks 字段

| 键 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 否 | 全局唯一的钩子标识，Python 侧按它分发；缺省为 `<modid>.hook<序号>` |
| `target` | 是 | 目标类，点分名或 `net/minecraft/...` internal name 均可 |
| `method` | 是 | 方法名；暂不支持 `<init>` / `<clinit>` |
| `descriptor` | 否 | 方法描述符，缺省 `*` 表示匹配同名所有重载 |
| `at` | 否 | `HEAD`（默认）/ `RETURN` / `BEFORE_INVOKE` / `AFTER_INVOKE` |
| `invokeTarget` | 条件 | `at` 为 `BEFORE_INVOKE` / `AFTER_INVOKE` 时必填，格式 `完整类名#方法名` |

注入的调用是"净栈效应为 0"的，不会破坏原有方法的表达式栈。

## Python 侧契约

引擎执行入口文件后，按需调用以下函数（全部可选）：

```python
def on_load(ctx): ...                    # 加载完成
def on_unload(): ...                     # 游戏退出前
def on_hook(hook_id, args): ...          # 命中声明的钩子
def on_event(event_id, payload): ...     # 收到加载器事件
```

`ctx` 提供：

| 成员 | 说明 |
| --- | --- |
| `ctx.mod_id` / `ctx.version` | 元数据中的 id / version |
| `ctx.minecraft_version` | 当前游戏版本 |
| `ctx.root` | mod 目录（只读资源） |
| `ctx.config_dir` | 该 mod 的配置目录（可写） |
| `ctx.cache_dir` | 该 mod 的缓存目录（可写、可丢） |
| `ctx.log/warn/error(msg)` | 日志 |

> 引擎尚未接入时，这些函数不会被调用 —— 见 [python-engine.md](python-engine.md)。

## 元素落地到哪里

一次 `mod.add.<kind>(name, **options)` 最终会走到下面四条路径之一，`options` 全程原样透传。

| 路径 | 元素 | 产物 |
| --- | --- | --- |
| 数据包 | advancement / armor_trim / banner_pattern / biome / damage_type / dimension / enchantment / feature / function / loot_table / painting / recipe / structure / villager_profession / villager_trade | `<gameDir>/pytrial/datapacks/pytrial_generated/data/<ns>/<folder>/<name>.json` |
| 注册表 | armor / attribute / block / command / creative_tab / custom_element / game_rule / item / item_extension / living_entity / particle / plant / potion_effect / potion_item / projectile / special_entity / tool | 直接进 `BuiltInRegistries` |
| Python | procedure | 同名函数挂到事件总线 |
| 未接通 | fluid / gui / key_binding / overlay | 需要 Java 侧生成类或客户端钩子，加载时打印具体原因 |

数据包里的目录名：`advancement`、`recipe`、`loot_table`、`function`、`structure`、`worldgen/biome`、`worldgen/configured_feature`、`dimension`、`dimension_type`、`enchantment`、`damage_type`、`banner_pattern`、`trim_pattern`、`trim_material`、`painting_variant`、`villager_trade`、`item_modifier`、`predicate`、`tags/<registry>`。

生成的数据包会写进 `<gameDir>/pytrial/datapacks/pytrial_generated`，并复制到每个已存在的存档的 `datapacks/` 下 —— 数据包是按存档加载的。

```python
# 声明即文件，字段就是原版 JSON
register_recipe("mymod", "ruby_block_from_ingot",
                type="minecraft:crafting_shapeless",
                ingredients=[{"item": "mymod:ruby_ingot"}],
                result={"id": "mymod:ruby_block", "count": 1})
```

```python
# 注册表类走反射，构造参数由 options 决定
register_tool("mymod", "ruby_pickaxe", durability=880,
              components={"max_stack_size": 1})
register_particle("mymod", "ruby_spark")
register_attribute("mymod", "ruby_luck", default=0.0, min=0.0, max=10.0)
```

```python
# procedure 绑定本文件里的同名函数
register_procedure("mymod", "on_ruby_tick", event="tick")

def on_ruby_tick(payload):
    log("tick")
```

任何一条路径失败都只打印一行日志，不会中断 mod 加载。
