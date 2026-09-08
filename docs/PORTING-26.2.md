# NoammAddons 26.2 移植维护手册

> 目的：上游（github.com/Noamm9/NoammAddons）持续在 `26.1.2` 分支更新时，本仓库如何把上游改动正确适配到 Minecraft **26.2**。
> 本文档沉淀了首次移植（26.1.2 → 26.2）的全部经验：基线、流程、依赖版本矩阵、API 差异速查、排查手段与已知遗留。
> 文中示例命令默认在仓库根目录执行；如需网络代理，请按各自环境自行配置（下文不再重复）。

---

## 0. 当前事实基线（先读）

- 上游 remote：`upstream` → https://github.com/Noamm9/NoammAddons
- 本地 remote：`origin` → 本仓库（Unofficial fork）
- 移植基线 commit：`58fa4c158dcc59d99b99188ba66a364c56dfa512`（上游 `26.1.2` 分支头，message：`fix(AhoCorasick) use longest match with word characters checks`）
- 首次移植 commit：`fd269d8` `feat: port NoammAddons to Minecraft 26.2`
- 工作分支：`26.2`（默认分支）
- 构建产物：`build/libs/NoammAddons-<mod_version>-26.2-cheat.jar` / `-legit.jar`
- 上游 License：CC0-1.0（本仓库随源码保留）。注意参考 skyblocker（LGPL-3.0）仅可读 API，**不得复制其代码**。
- 上游存在 `1.21.10-legacy` / `1.21.11-legacy` / `26.2-unsupported` 分支，均属老旧/不可用代码，**禁止参考**。

---

## 1. 上游更新后如何适配到 26.2（总流程）

### 1.1 拉取上游最新
```bash
git fetch upstream 26.1.2
```

### 1.2 查看自基线以来上游改了什么
```bash
git log --oneline 58fa4c1..upstream/26.1.2
git log --stat 58fa4c1..upstream/26.1.2
git diff --stat 58fa4c1 upstream/26.1.2
```

### 1.3 判断冲突范围
- 只列出上游改动涉及的路径，与本仓库已改动路径对比：
  - `git diff --name-only 58fa4c1..upstream/26.1.2`（上游改动）
  - `git diff --name-only <首次移植commit>..<当前头>`（本地移植改动）
  - 两个列表交集 = 需要重点手工合并的文件；上游新增文件 = 需按第 2/3 章规则适配后带入。

### 1.4 回补方式（二选一，推荐 A）
- **方式 A：cherry-pick 逐个挑选**
  ```bash
  git checkout 26.2
  git cherry-pick <上游commit>
  ```
  同文件冲突时手动解决。
- **方式 B：rebase（把本地移植重放到上游新 tip）**
  ```bash
  git checkout 26.2
  git rebase upstream/26.1.2
  ```
  冲突集中在“同一文件本地改过、上游也改过”的位置。rebase 会改写历史，多人协作/已发布时优先方式 A。
- 无论哪种，最终都必须完成第 2、3 章检查和构建/运行验证。

### 1.5 关键提醒
- 上游新提交是在 **26.1.2 API** 下写的：新功能可能仍直接引用 26.1.2 才有的 API（例如 `EntityType.X`、`MultiBufferSource`、`Blocks.XXX_WOOL`、`mc.screen` 等）。**不能当 26.2 代码直接合入**，必须按第 3 章差异表改写。
- 上游若整体重命名/删除某功能，按同样规则处理；不要照抄 skyblocker。
- 每次合入后跑 `build`（见第 5 章），并至少一次 `/na` + 进地牢冒烟。

---

## 2. 版本/依赖矩阵（26.2）

配置文件：`gradle.properties`、`buildSrc/build.gradle.kts`（fabric-loom 依赖）、`build.gradle.kts`（universalcraft 构件名）。

当前值（已验证）：

| 项 | 值 | 说明/升级方法 |
|---|---|---|
| minecraft_version | `26.2` | |
| loader_version | `0.19.3` | 可升到 fabric 最新 |
| loom_version | `1.17.20` | `buildSrc/build.gradle.kts` 里也有一处 fabric-loom 需同步；更晚可用 `1.17-SNAPSHOT` |
| fabric_version | `0.159.0+26.2` | 查 fabric-api 的 maven-metadata 里 `+26.2` 最新 |
| fabric_kotlin_version | `1.13.13+kotlin.2.4.10` | 查 fabric-language-kotlin maven-metadata 最新 |
| modmenu_version | `20.0.1` | 26.2 专用 |
| iris_version | `1.11.2+26.2-fabric` | 查 Modrinth API（game_versions 含 `26.2`、loaders=fabric） |
| ktor_version | `3.5.2` | 非 MC 依赖 |
| universalcraft_version | `516` | 构件名必须是 **`gg.essential:universalcraft-26.2-fabric`**（见 build.gradle.kts）；查 essential 仓库 maven-metadata 下该 artifact |
| JDK | 目标 25 | |

上游新增依赖时：优先确认该库是否发布 `+26.2` 变体；maven.fabricmc.net / api.modrinth.com 是权威来源。

`src/main/resources/fabric.mod.json5` 中 `depends.minecraft` 必须为 `"26.2"`；description 若写版本号需同步。

---

## 3. 26.1.2 → 26.2 API 差异速查（核心）

> 以下每条都是首次移植踩过的坑。凡上游代码用了左侧写法，一律改成右侧。

### 3.1 彩色方块 / 物品 / 染料（26.2 统一为“家族 + 颜色访问器”）
旧：`Blocks.BLACK_STAINED_GLASS`、`Items.LIME_STAINED_GLASS_PANE`、`Items.GRAY_DYE`、`Blocks.RED_TERRACOTTA`、`Blocks.GREEN_WOOL`、`Blocks.BLUE_WOOL`…
新：
```kotlin
Blocks.STAINED_GLASS.black()          // 玻璃家族
Blocks.STAINED_GLASS_PANE.red()       // 玻璃板
Blocks.WOOL.green()                   // 羊毛
Blocks.DYED_TERRACOTTA.blue()         // 染色陶瓦（含旧 RED_TERRACOTTA 等）
Blocks.GLAZED_TERRACOTTA.lime()       // 釉陶
Blocks.CONCRETE.gray()                // 混凝土
Items.STAINED_GLASS_PANE.black()      // Item 版家族
Items.DYE.lime()                      // 染料
Items.DYED_TERRACOTTA.lime()          // 陶瓦 Item 版
```
颜色方法名：`white orange magenta lightBlue yellow lime pink gray lightGray cyan purple blue brown green red black`。
类型：`net.minecraft.world.level.block.ColorCollection<T>`；各家族字段见 `Blocks` / `Items`。

### 3.2 实体类型常量
旧：`EntityType.SHEEP / PLAYER / ENDER_DRAGON / ITEM_FRAME / END_CRYSTAL / FALLING_BLOCK / LIGHTNING_BOLT / EXPERIENCE_ORB`
新：`EntityTypes.SHEEP / ...`（import `net.minecraft.world.entity.EntityTypes`）。
`EntityType` 仍是泛型类型名（用于变量声明），常量都搬进了 `EntityTypes`。

### 3.3 Gui / Hud 拆分（改动最重）
26.2 把 HUD 从 `net.minecraft.client.gui.Gui` 拆出到 `net.minecraft.client.gui.Hud`。
- `Minecraft.gui`（Gui）管理 screen/overlay/chatListener；`Minecraft.gui.hud` 才是 HUD。
- `Gui` 持有：`screen`、`overlay`、`hud`、`setScreen(Screen)`、`extractRenderState(DeltaTracker,boolean,boolean)`。
- **Hud** 持有：血条/护甲/食物/标题/副标题/药水/计分板/快捷栏/玩家标签(TabList) 等全部 `extract*` 方法，字段 `title/subtitle/isHidden/tabList`。
- 所有“对 HUD 的 @Mixin/inject/shadow”，目标类要从 `Gui` 改为 `Hud`（例如本仓库 `MixinGui` 目标就是 `Hud`）。
- 关键访问：
  - `mc.gui.hud.isHidden()`（代替旧 `options.hideGui`）
  - `mc.gui.hud.getTabList().getNameForDisplay(info)`（代替 `mc.gui.tabList...`）
  - `mc.gui.screen()` / `mc.gui.setScreen(s)`（代替 `mc.screen` 读写，**Minecraft 已无 screen 字段/方法**）
  - 换屏注入目标改到 `Gui.setScreen`，取原屏幕值需 shadow `Gui` 的 `private Screen screen` 字段。

### 3.4 渲染器字段/方法改名
- `GameRenderer` 内字段改为私有，用访问器：`gameRenderer.gameRenderState()`、`.featureRenderDispatcher()`、`.lighting()`。
- `LevelRenderer.allChanged()` → `resetLevelRenderData()`。
  - ⚠️ 26.2 中 `resetLevelRenderData()` **只销毁不重建**：把 `viewArea` 置空、dispose 掉 `sectionRenderDispatcher` 后直接返回。若在已进入世界时调用，下一帧 `render() → repositionCamera()` 就会因 `viewArea == null` 直接 NPE（崩溃日志形如 `Cannot invoke ViewArea.repositionCamera(...) because this.viewArea is null`）。
  - 需要"运行时重建区块渲染数据"（改方块剔除/材质等）时改走 `invalidateCompiledGeometry(level, options, gameRenderer.mainCamera(), blockColors)`，它会重建 `ViewArea` 并重定位相机——封装见 `ModCompatibility.refreshLevelRenderer()`（`v0.3` 修复）。`resetLevelRenderData()` 只适合退出/关服等随后必然重建的场景。
- `ItemInHandRenderer`：`renderArmWithItem`→`submitArmWithItem`，`renderHandsWithItems`→`submitHandsWithItems`；签名普遍带 `SubmitNodeCollector`。
- `ScreenEffectRenderer`：旧 `renderFire/renderWater` 移除；新为私有静态 `submitFire(PoseStack,SubmitNodeCollector,TextureAtlasSprite)` 与 `submitWater(Minecraft,PoseStack,SubmitNodeCollector)`；遮罩注入改到这两个方法（见 `MixinScreenEffectRenderer`）。`getViewBlockingState` 仍在但已是 private。
- `BlockPos.center` 没了 → 用 `Vec3.atCenterOf(pos)`。
- `PlayerTabOverlay` 构造改 `(Minecraft, Hud)`。

### 3.5 文字 / ChatFormatting
- 26.2 `ChatFormatting` 不再带 RGB 色值字段与 `isColor/color/char`，仅剩 `code`（char）与 `getByCode`。
- 需把 `TextColor`（RGB）反查成 `§x` 时：内置标准 16 色映射即可（参考 `ChatUtils.legacyColorToFormatChar` 与 `TextColor.toLegacyFormatCode()`）。
- 世界内文字渲染见 3.7。

### 3.6 渲染管线 / 顶点
- `Tesselator`、`MultiBufferSource`（旧即时渲染）在 26.2 **已删除**。
- `RenderPipeline.Builder`：`withVertexFormat(format, VertexFormat.Mode)` 已删 → `withVertexBinding(0, vertexFormat)` + `withPrimitiveTopology(PrimitiveTopology.XXX)`（`VertexFormat.Mode` 概念迁到 `com.mojang.blaze3d.PrimitiveTopology`：`TRIANGLES/TRIANGLE_STRIP/LINES/QUADS/POINTS/TRIANGLE_FAN`）。
- `RenderType` 工厂已私有化：**第三方不能自定义 RenderType**（`RenderType.create` 包私有）。只能用 `net.minecraft.client.renderer.rendertype.RenderTypes` 的公开工厂，如 `lines()/linesTranslucent()/debugFilledBox()/debugQuads()/debugTriangleFan()/text()/textBackground()/textSeeThrough()` 等。

### 3.7 世界内自绘（本仓库自研方案，重点）
26.2 世界渲染改为 **提交节点 + 特性渲染器**，mod 自绘只能：
1. 自定义 `FeatureRendererType`（`FeatureRendererType.create("name")`）。
2. `net.fabricmc.fabric.api.client.rendering.v1.FeatureRendererRegistry.register(type){ Renderer() }`（客户端初始化调用一次）。
3. 渲染器继承 vanilla `RenderTypeFeatureRenderer<Submit>`，只实现 `buildGroup(context, submits)`：
   - `getVertexBuilder(RenderType)` 拿顶点写入器；官方基类已处理上传与执行。
   - 顶点坐标为**相机相对坐标**（本仓库 `Render3D`/batches 存的即相机相对坐标）。
4. 提交节点实现 `SubmitNode`，`featureType()` 返回注册的类型。
5. 在 `LevelRenderEvents.COLLECT_SUBMITS`（本仓库 `EventDispatcher`）收集完画布后：
   ```kotlin
   val c = context.submitNodeCollector() as FabricOrderedSubmitNodeCollector
   c.submitCustom(SubmitRenderPhases.AFTER_TERRAIN /* 或 ALWAYS_ON_TOP */, node)
   ```

本仓库实现位置：
- `src/main/kotlin/com/github/noamm9/utils/render/world/feature/NoammFeatureRenderers.kt`（LINES/FILLS/TEXT 三种 node+renderer+register+submit）
- `RenderBatcher.flush(ctx)`：批次转提交（line/fill/text）。**fill 数据必须按 4 顶点/四边形（QUADS）上传**；circle 每段也要 4 顶点（billboard 圆环已按 quad 重排）。
- 世界文字：用 `Font.prepareText(String, x, y, color, shadow, background)`（String 重载自动解析 `§` 色码）+ `Font.GlyphVisitor` 逐 glyph 上传（可参考 vanilla `NameTagFeatureRenderer` 的结构自研；勿抄 skyblocker）。

### 3.8 其它杂项
- `Options.hideGui` 没了（用 3.3 的 `hud.isHidden()`）。
- 需遍历 16 色时用 `ColorCollection.asList()` / `pick(DyeColor)`。

---

## 4. 常见错误与排查手段

### 4.1 编译期
- 编译错误可重定向到日志查看：`gradlew.bat compileKotlin > log.txt 2>&1`。错误列表是最直接的“适配清单”。
- “Unresolved reference 'Xxx'”：先查第 3 章 Xxx 是否迁移（EntityTypes / ColorCollection / 访问器等）。
- 不确定新符号名：用 genSources + javap（见 4.3）。

### 4.2 运行期 mixin 报错
- 典型文本：`Mixin apply for mod noammaddons failed ... @Shadow field X was not located` / `Critical injection failure ... could not find any targets matching 'methodName'` / `@ModifyArg ... non-method insn`。
- 处理套路：
  1. javap 看目标类成员是否存在/改名。
  2. 字段没了 → 换 shadow 到新宿主类或改访问器（3.3/3.4）。
  3. 方法名没了 → 找 26.2 新方法名（常见 submit/extract 前缀迁移）。
  4. `@ModifyArg` 不能用于 `new` 指令，只能 `@ModifyExpressionValue` / `@Redirect`（本仓库 `MixinSubmitNodeCollection` 是案例）。
- 启动即崩多为 Gui/Hud/Minecraft/FeatureRenderDispatcher 等早加载类；进世界才崩多为实体/世界相关 mixin。

### 4.3 高效查 API
- **生成反编译源码**：`gradlew.bat genSources`。产物在 `<项目>/.gradle/loom-cache/minecraftMaven/net/minecraft/<minecraft-merged-xxxx>/26.2/<...>-sources.jar`，可用 zip 工具抽取任意 `.java` 阅读（vanilla 官方源码，可放心阅读）。
- **javap**：对 fabric-loom 缓存的 26.2 named jar 跑 `javap -p -cp <jar> <类名>`（嵌套类用 `$`）。fabric API 在 gradle modules 缓存里是拆分模块 jar（如 fabric-rendering-v1），javap 时指定对应 jar。
- 判断成员是否存在应看 jar/源码，而非猜测。

---

## 5. 构建与验证

```bash
gradlew.bat build --console=plain   # 产 build/libs/*-cheat.jar / *-legit.jar
```
每次上游合并/大改后至少验证：
1. 能启动进 Hypixel。
2. `/na` 配置界面可用。
3. 地牢冒烟覆盖三套渲染通道：Block Overlay（fill+outline）、Livid tracer（line）、Water Board 文字（text）。
4. 已知遗留见第 6 章。

---

## 6. 已知遗留 / 长期 TODO

- `NameTagTweaks` 的 **Add Name Tag Text Shadow**：26.2 NameTagFeatureRenderer 无阴影参数，保持无效；实现需改造 vanilla 名字文本路径（低性价比）。
- `TrapHelper`：上游整文件处于注释状态（`src/main/kotlin/.../dungeon/TrapHelper.kt` 是 `/*...*/`），恢复需先解除注释再适配 26.2。
- 圆形面已按 QUADS 支持，但环形圈顶点为近似；效果不佳可再精修。
- 上游 26.1.2 仍在推进；每次合并须按第 1 章流程并回归第 3 章差异。

---

## 7. 命令速记（均不含本机环境假设）

```bash
# 拉上游 & 对比
git fetch upstream 26.1.2
git log --oneline 58fa4c1..upstream/26.1.2
git diff --stat 58fa4c1 upstream/26.1.2

# 推送到本仓库（如环境需要代理，自行附加相应 git 配置）
git push origin 26.2

# Release（按需使用 gh / 仓库页面上传）
# 附件：build/libs/NoammAddons-<version>-26.2-cheat.jar、-legit.jar
```
