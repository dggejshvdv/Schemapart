# Schemapart

在 Minecraft 游戏内把**图片**或**文字**转换成地图画，并直接生成可用 Litematica（投影模组）
加载的 `.litematic` 原理图。方块扫描、颜色匹配、预览、方块白名单、参数调整和导出全部在游戏内完成，

## 功能

### 启动与目录

- 启动时自动创建：
  - `mapart/pictures`：图片输入目录
  - `mapart/matic`：生成的 `.litematic` 输出目录
  - `mapart/blocks.json`：扫描到的全部方块数据
- 目录可在「设置 → 目录/快捷键」中修改：
  - 图片目录
  - 输出目录
  - Litematica 原理图目录
  - 支持绝对路径，或相对游戏目录的路径
  - 留空恢复默认
- 初始化时扫描所有已注册方块（含模组），记录：
  - 地图颜色、是否可挖掘、是否需要正确工具、精准采集启发式
  - 原版 / 模组分类
  - 方块 id、物品 id、贴图引用

### WelcomeUI

`M + P` 默认打开 WelcomeUI：

- 左卡片：像素字「你好」→ **MWMAUI 文字地图画**
- 右卡片：Minecraft 草方块图标 → **MAUI 图片地图画**
- 右上角「设置」

### MAUI：图片浏览器

- 单栏图片列表，只显示图片目录
- 顶部：刷新 / 打开目录 / 设置 / 返回
- 双击图片进入 **MMAUI**
- 右键文件：重命名 / 剪切 / 复制 / 删除
- 右键空白：打开文件夹 / 粘贴 / 刷新 / 设置
- **支持把图片直接拖进游戏窗口导入**
- 空目录显示完整路径

### MMAUI：图片地图画

- 左侧「地图 / 原理图」两种预览
- 右键拖动、滚轮缩放、复位、刷新
- 精度（方块:像素）：`1:0.5`、`1:1`、`1:2`、`1:4`、`1:8`
- 方块能否被挖掘：全部 / 仅可挖掘 / 仅不可挖掘
- 方块来源：全部 / 仅原版 / 仅模组
- 方块启用清单：JEI 风格排序、分页、搜索、全选 / 全不选、灰色 / 高亮
- ESC 自动保存默认清单（可在设置中关闭）
- **生成前弹出文件名输入框**
- 右下角「开始制作」

### MWMAUI：文字地图画

- 全屏地图画布
- 每个文字/符号独立占据 128×128 基准区域
- 左键单击文字：
  - 选中，显示白色矩形边框
  - 打开右侧编辑器
- 左键拖拽文字：实时移动；拖拽时所有文字显示白色边框
- 右键空白：添加文字/符号、画布设置、方块优先级、生成
- 左键空白拖动平移，滚轮缩放
- 右侧编辑器：
  - 文字颜色（16 色）
  - 方块系列：羊毛 / 陶瓦 / 羊毛地毯 / 混凝土 / 混凝土粉末
  - 字体
  - 宽 / 高
  - 阴影
  - 删除
- 所有选项为「点击展开 → 滚轮翻页 → 点击选择」
- 画布为 `x × y` 个 128×128 地图：
  - 最小 `1×1`
  - 最大 `16×16`
  - 长宽均为 128 的整倍数
- 元素尺寸下限：
  - 文字（CJK 等）64×64
  - 小写 32×32
  - 大写 32×64
  - 半角符号 16×32
  - 全角符号 32×32
- 新增文字自动寻找空 128 格；不足时扩展画布
- **允许文字重叠、交错**
- 不同材质重叠时，首次生成前弹出优先级排序：
  羊毛 / 陶瓦 / 羊毛地毯 / 混凝土 / 混凝土粉末
- 同材质时，后添加的元素显示在上
- **生成前弹出文件名输入框**

### 生成与输出

- 写入输出目录 `*.litematic`
- 若安装 Litematica 且开启「复制到投影」，同时复制到配置的 Litematica 目录
- 透明背景使用空气方块（`blockIndex = -1`）

### 设置

设置页包含：

- ESC 自动保存方块清单
- 复制原理图到 Litematica
- 默认开启 Floyd–Steinberg 抖动
- 重新扫描方块
- **目录 / 快捷键**：
  - 修改图片目录
  - 修改输出目录
  - 修改 Litematica 原理图目录
  - 修改打开界面的双键快捷键（默认 `M + P`）
  - 恢复默认

WelcomeUI、MAUI、MWMAUI 都有设置入口。

## 兼容版本

每个 jar 对应一段 Minecraft 版本：

| Jar | 适用 Minecraft | Java | 状态 |
|---|---|---|---|
| `schemapart-1.21.1-*.jar` | 1.21 – 1.21.1 | 21+ | 完整功能，已实测 |
| `schemapart-1.21.4-*.jar` | 1.21.2 – 1.21.4 | 21+ | 已移植并构建 |
| `schemapart-1.21.8-*.jar` | 1.21.5 – 1.21.8 | 21+ | 已移植并构建 |
| `schemapart-1.21.11-*.jar` | 1.21.9 – 1.21.11 | 21+ | 已移植并构建 |
| `schemapart-26.2-*.jar` | 26.2.x | 25+ | 已移植并构建 |

> 1.21.1 已做游戏内实测；其余版本仅完成移植与编译。

## 安装

1. 安装对应版本的 Fabric Loader：
2. 把对应 jar 放入 `.minecraft/mods`。
3. 启动游戏，进入任意世界。
4. 按 `M + P`（可在设置中修改）打开 WelcomeUI。

首次启动会在游戏目录创建默认 `mapart` 目录及 `blocks.json`。

## 使用流程

### 图片地图画
1. WelcomeUI 点击右侧「地图画制作」。
2. 把图片拖进 MAUI 窗口，或右键空白 →「打开文件夹」后拖入,也可以或右键空白 →「粘贴」。
3. 双击图片进入 MMAUI。
4. 调整精度、筛选、方块清单。
5. 点击「开始制作」，填写投影文件名。
6. 在输出目录取得 `.litematic`（若安装了投影模组，则会复制一份于默认的投影原理图目录）。

### 文字地图画

1. WelcomeUI 点击左侧「文字地图画制作」。
2. 单击文字选中，在右侧编辑颜色 / 方块 / 字体 / 尺寸 / 阴影，或删除。
3. 左键拖拽文字调整位置，可让多个文字重叠。
4. 右键空白可添加文字、设置画布、编辑方块优先级或生成。
5. 点击「生成」，填写文件名。
6. 如果不同材质重叠且尚未确认优先级，会先弹出优先级排序。
7. 生成文件位于输出目录。

### 默认快捷键

| 操作 | 按键 |
|---|---|
| 打开 WelcomeUI | `M + P`（可修改） |
| WelcomeUI → 游戏 | `ESC` |
| MAUI → WelcomeUI | `ESC` |
| MMAUI → MAUI | `ESC` |
| MWMAUI → WelcomeUI | `ESC` |
| 方块选择 → MMAUI | `ESC`（按设置决定是否保存为默认清单） |
| 图片预览平移 | 预览区按住鼠标右键拖动 |
| 文字画布平移 | 左键空白拖动 |
| 预览缩放 | 鼠标滚轮 |

## 构建

每个版本文件夹下都有独立 `build.bat`：

```
fabric-1.21.1/build.bat
fabric-1.21.4/build.bat
fabric-1.21.8/build.bat
fabric-1.21.11/build.bat
fabric-26.2/build.bat
```

双击对应脚本，或：

```bat
fabric-1.21.1\build.bat --no-pause
```

脚本会：

- 查找合适的 JDK（1.21.x 用 21+，26.2 用 25+）
- 构建该版本
- 把最终 jar 复制到版本文件夹根目录
- 清理中间 `build/`

产物：

```
fabric-1.21.1/schemapart-1.21.1-1.0.0+mc1.21.1.jar
fabric-1.21.4/schemapart-1.21.4-1.0.0+mc1.21.4.jar
fabric-1.21.8/schemapart-1.21.8-1.0.0+mc1.21.8.jar
fabric-1.21.11/schemapart-1.21.11-1.0.0+mc1.21.11.jar
fabric-26.2/schemapart-26.2-1.0.0+mc26.2.jar
```

## 目录结构

```
common/                 纯 Java 核心
fabric-1.21.1/          1.21 – 1.21.1 mod + build.bat + jar
fabric-1.21.4/          1.21.2 – 1.21.4 mod + build.bat + jar
fabric-1.21.8/          1.21.5 – 1.21.8 mod + build.bat + jar
fabric-1.21.11/         1.21.9 – 1.21.11 mod + build.bat + jar
fabric-26.2/            26.2 mod + build.bat + jar
```

UI 代码按版本分别适配：

- 1.21.1 / 1.21.4 / 1.21.8：`GuiGraphics` + 旧输入回调
- 1.21.11：`GuiGraphics` + `MouseButtonEvent` / `KeyEvent` / `CharacterEvent`
- 26.2：`GuiGraphicsExtractor` / `extractRenderState`

## 原理

### 图片地图画

- 图片按精度缩放到“一个方块 = 一个输出像素”
- 从启用方块中选每种地图颜色的代表方块
- 使用 CIE Lab 色差匹配，可选 Floyd–Steinberg 抖动
- 透明像素合成到背景

### 文字地图画

- AWT 字体渲染到透明画布，关闭抗锯齿，得到像素化文字
- 每个元素独立渲染、缩放、定位
- 重叠像素按用户方块优先级选择方块
- 16 种地图颜色 × 5 种方块系列
- 透明背景用空气表示（`blockIndex = -1`）

### Litematica 写入

自研 NBT / Litematica 写入器：

- 根节点 `Version = 6`、`SubVersion = 1`、`MinecraftDataVersion = 当前版本数据版本`
- `BlockStatePalette` 是 Compound 列表，每项为 `{Name: "minecraft:xxx"}`
- palette 第 0 项固定为 `minecraft:air`
- `BlockStates` 是 LSB-first、连续位打包的 `long[]`，每位宽度为
  `max(2, ceil(log2(paletteSize)))`，long 之间没有额外填充
- 单 region `Schemapart`，包含 `Position`、`Size`、`BlockStatePalette`、
  `BlockStates`、`Entities`、`TileEntities`、待处理 tick 列表
- `Metadata` 包含 `EnclosingSize`、`RegionCount`、`TotalBlocks`、`TotalVolume`、
  `TimeCreated`、`TimeModified`、`Name`、`Author`、`Description`、`Software`
- 内部 `blockIndex < 0` 映射为 palette 0（空气）

已用 `litemapy 0.11.0b0` 加载实际生成文件验证：`256×1×128` 文字画读出
`TotalVolume = 32768`、`TotalBlocks = 4695`，`BlockStates` 长度为 1024 个 long。

## 已知限制

- 文字画布上限 16×16 个 128 地图（2048×2048 像素）
- 文字使用 AWT 系统字体，缺字时自动回退
- 文字元素不支持单独旋转
- 精准采集判断为启发式
- 1.21.4 / 1.21.8 / 1.21.11 / 26.2 完成构建，未做完整游戏内回归
- Litematica 复制逻辑需安装 Litematica 后验证

## 相关文档

- [PROGRESS.md](PROGRESS.md)
- [PLAN_WELCOME_WORDART.md](PLAN_WELCOME_WORDART.md)
- [SOLUTION_DPI_LAYOUT.md](SOLUTION_DPI_LAYOUT.md)

## License

见 [LICENSE](LICENSE)。
