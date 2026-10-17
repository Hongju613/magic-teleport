# MagicTeleport

Magic teleport tunnels for **Paper 1.21+**.

Build a small pavilion around a *core block*, bind two pavilions together with a stick, and
travel between them: right-click the core, stay on it while a purple spiral spins around you,
and you arrive on top of the other core.

```
   right-click core ─► countdown (3s, purple spiral) ─► arrive at the other core
   stick + core     ─► binding mode (two clicks make one tunnel)
```

- **Strict, player-built structures** — a tunnel endpoint must match a recorded 3×3×6 template
  **block for block** (players build them, the server validates them).
- **Two-way teleport** with an instant cancel if you step off the block mid-countdown.
- **Per-tunnel access control** — the owner decides who may use it; denied players are only told
  *after* the countdown, exactly as designed.
- **Fees** — optional items and/or coins (Vault). Items are collected into a chest/barrel the
  owner chooses with a stick left-click. The owner always travels free.
- **Limits & VIP hooks** — per-player tunnel limit, purchasable extra slots, and a stub API for a
  VIP system.
- **Localization** — English and Chinese messages built in; add your own language files.

---

## Requirements

| | |
|---|---|
| Server | Paper **1.21+** (built and tested against **1.21.11**) |
| Java | **21+** |
| Optional | [Vault](https://www.spigotmc.org/resources/vault.34315/) + an economy plugin (coin fees) |

## Installation

1. Drop `magic-teleport-1.0.0.jar` into `plugins/` and start the server.
2. `plugins/MagicTeleport/tunnels.yml` is generated with defaults.
3. Build **one** pavilion (see below), then record it as the template:
   * stand on the core block and run `/magicteleport template set`, or
   * from console: `/magicteleport template set <world> <x> <y> <z>` (the coordinates of the core block).
4. Allow players to bind tunnels in that world: `/magicteleport createallow true <world>`.
5. Build a second pavilion (must be identical), then:
   `/magicteleport add` → right-click the core of the first structure with a **stick** →
   right-click the core of the second one → *Tunnel created*.

Players can then right-click a core block with an empty hand to travel.

## The pavilion

The template is anchored at the **core block = center of the bottom layer**. Everything is
relative to it: `x/z ∈ [-1, 1]`, `y ∈ [0, 5]`.

Reference design (what the screenshots/tests used) — **35 solid blocks + 19 air**:

| Layer | Corners (×4) | Edge middles (×4) | Center |
|---|---|---|---|
| `y+0` | `cracked_deepslate_tiles` | `deepslate_tiles` | **core block** (`reinforced_deepslate`) |
| `y+1` | `cracked_deepslate_tiles` | `polished_blackstone_brick_slab` | air |
| `y+2` | `deepslate_tile_wall` | air | air |
| `y+3` | `deepslate_tile_wall` | air | air |
| `y+4` | `polished_blackstone_brick_slab` | `polished_blackstone_brick_slab` | `crying_obsidian` |
| `y+5` | air | air | `polished_blackstone_brick_slab` |

You are **not** limited to this design: build whatever 3×3×6 shell you like and record it with
`/magicteleport template set`. Every tunnel endpoint is then compared against your template,
cell by cell — one wrong block and it is rejected.

Two details worth knowing:

* The template stores the center-bottom cell as a `CORE` placeholder, so changing the core block
  later (`/magicteleport block <block>`) keeps the same template valid.
* The landing spot is always **the block above the core**. Keep that cell air.

## Commands

### Players

| Command | Description |
|---|---|
| `/magicteleport` | open the 54-slot manager GUI |
| `/magicteleport add` | bind mode: right-click two cores with a stick (60 s) |
| `/magicteleport delete` | open the GUI in delete mode (asks for confirmation) |
| `/magicteleport items [id]` | GUI to set the fee items (max 64 of each) |
| `/magicteleport container [id]` | then left-click a chest/barrel with a stick to collect fees |
| `/magicteleport buy [confirm]` | buy one extra tunnel slot with coins |
| `/magicteleport info` | list your tunnels |
| `/magicteleport setallow <id> <true\|false>` | allow/deny other players |

### Administrators (`magicteleport.admin`)

| Command | Description |
|---|---|
| `/magicteleport amount <n>` | default tunnels per player (default `4`) |
| `/magicteleport time <seconds>` | teleport countdown (default `3`) |
| `/magicteleport sell <true\|false>` | allow buying extra slots (default `false`) |
| `/magicteleport price <coins>` | price of one extra slot (default `0`) |
| `/magicteleport charge <true\|false>` | charge tolls on tunnel use (default `false`) |
| `/magicteleport vipallow <true\|false>` | only VIPs may create tunnels (stub, default `false`) |
| `/magicteleport vipamount <level> <n>` | tunnels per VIP level (stub) |
| `/magicteleport createallow <true\|false> [world]` | allow binding tunnels in a world (default: **denied**) |
| `/magicteleport block <block>` | core block, default `reinforced_deepslate` |
| `/magicteleport template set [world x y z]` | record the 3×3×6 template |
| `/magicteleport admin create <owner> <world> <x1 y1 z1> <x2 y2 z2>` | create a tunnel from console |
| `/magicteleport admin validate <world> <x y z>` | check a structure against the template |
| `/magicteleport admin container <id> <world> <x y z>` | set a fee container from console |
| `/magicteleport reload` | reload `config.yml`, `tunnels.yml` and the language file |

## Permissions

| Node | Default | Purpose |
|---|---|---|
| `magicteleport.use` | everyone | use the GUI, bind and travel |
| `magicteleport.admin` | operators | all admin commands (also bypasses limits, access and fees) |

## Configuration

`plugins/MagicTeleport/config.yml`:

```yaml
lang: en_US      # en_US / zh_CN, files live in plugins/MagicTeleport/lang/
```

`plugins/MagicTeleport/tunnels.yml` (generated; the GUI/commands keep it in sync):

```yaml
配置:                    # (legacy key names are kept for data compatibility)
  amount: 4
  time: 3
  sell: false
  price: 0
  charge: false
  vipallow: false
  vipamount: { '1': 8, '2': 16, '3': 32 }
  core: REINFORCED_DEEPSLATE
  worlds: { maincity: true }
  template: [ ... 6 layers × 3 rows × 3 blocks ... ]
通道:
  '1':
    主人: <uuid>
    名称: somebody's tunnel
    允许他人: true
    A: { 世界: world, 核心: [x, y, z] }
    B: { 世界: world, 核心: [x, y, z] }
    收费物品: { DIAMOND: 8 }
    金币价格: 0.0
    容器: { 世界: world, 位置: [x, y, z] }
玩家:
  <uuid>: { 已购上限: 0 }
下个id: 2
```

> The section names are in Chinese because the plugin grew out of a Chinese server. They are
> stable data keys; renaming them would break existing instalments, so they stay.
> ⚠️ Editing `tunnels.yml` while the server runs is only safe if you run `/magicteleport reload`
> immediately afterwards — any command that writes data will overwrite your hand edits.

## Localization

`plugins/MagicTeleport/lang/en_US.yml` and `zh_CN.yml` ship with the plugin and are extracted on
first start. Copy one, translate it, set `lang: <file name>` in `config.yml`, then
`/magicteleport reload`. Placeholders are `{0}`, `{1}`, … — keep their meaning.

## Building

```bash
mvn package
# → target/magic-teleport-1.0.0.jar
```

Java 21 toolchain, Paper API `1.21.11-R0.1-SNAPSHOT`, Vault API (provided scope).
A GitHub Actions workflow (`.github/workflows/build.yml`) builds every push.

Offline machines without Maven can use the plain `javac` script:

```bash
PAPER_API_JAR=/path/paper-api.jar VAULT_API_JAR=/path/VaultAPI.jar \
BUNGEE_CHAT_JAR=/path/bungeecord-chat.jar python3 tools/build_jar.py
```

## Notes & limitations

* **No GUI automation**: everything is clickable by hand; there is no command-only equivalent for
  the fee-item GUI.
* **Server-side structure validation** is block-type based (not block-state), so e.g. a
  top-half slab in a spot that expects a bottom-half slab still counts as a match.
* If another plugin blocks block interactions in your spawn/city world, whitelist the core block
  for it, e.g.:

  ```java
  // in your interaction lock, before cancelling the event
  if (Bukkit.getPluginManager().isPluginEnabled("MagicTeleport")
          && com.magictunnels.MagicTeleport.isTunnelCore(clickedBlock)) {
      return; // let the tunnel handle it
  }
  ```

* Structures are static: if a player breaks a block of a pavilion, that endpoint stops validating
  but existing tunnels keep working as long as the core block itself exists.
* Landing inside a solid block is not repaired — keep the cell above the core air.

## 中文说明（简）

**一句话**：玩家自己搭 3×3×6 的亭子（核心方块 = 底面中心），用木棍右键两个亭子的核心方块把
它们绑成一条双向通道；右键核心方块开始读秒，读秒中紫色螺旋粒子绕着你转，站住别动就会传送到
对面亭子的核心方块上方。

- 结构必须与**录制的模板逐格一致**：`/magicteleport template set` 把眼前 3×3×6 的 54 格录成模板
- 读秒默认 3 秒（`/magicteleport time`），**读秒中离开方块立刻取消**
- 主人可以设置「是否允许他人」；别人没权限时**读满秒数后**才提示「你没有权限进行隧穿」
- 收费可设物品（每种≤64）+ 金币（Vault），收来的物品进主人用木棍左键选定的箱子/木桶；**主人免费**
- 默认上限 4 条（`/magicteleport amount`），可用金币买（`sell`/`price`），VIP 只留接口
- 界面：`/magicteleport` 54 格，底部 5 个按钮（新增/删除→再确认/物品/容器/购买）
- 新装的世界默认**不允许创建隧道**：`/magicteleport createallow true <世界>`
- 提示语支持中英双语：`config.yml` 的 `lang`（`en_US` / `zh_CN`），改完 `/magicteleport reload`

## License

[GPL-3.0](LICENSE) — because this project links against the Bukkit/Paper API, the same copyleft
spirit as the platform is used.
