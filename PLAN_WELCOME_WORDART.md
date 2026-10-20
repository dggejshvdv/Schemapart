# WelcomeUI / 文字地图画：版本适配记录

## 1. 统一流程

```
M + P
  └─ WelcomeScreen
       ├─ 左卡片「你好」→ MWMAUIScreen（文字地图画）
       └─ 右卡片 Minecraft 图标 → MAUIScreen（图片浏览器）
              └─ 双击图片 → MMAUIScreen（图片地图画）
```

## 2. 类名

| 新名称 | 旧名称 | 职责 |
|---|---|---|
| `WelcomeScreen` | 无 | 根选择页 |
| `MAUIScreen` | `MainScreen` | 图片浏览器 |
| `MMAUIScreen` | `MapArtScreen` | 图片地图画 |
| `MWMAUIScreen` | 无 | 文字地图画 |
| `AdvancedSettingsScreen` | 无 | 目录与快捷键 |

旧 `MainScreen` / `MapArtScreen` 已从五个模块删除。

## 3. 版本差异

### 1.21.1 / 1.21.4 / 1.21.8

- `GuiGraphics`
- 旧输入签名：
  - `mouseClicked(double,double,int)`
  - `mouseScrolled(double,double,double,double)`
  - `keyPressed(int,int,int)`
  - `charTyped(char,int)`
- 1.21.8 的 `GuiGraphics.pose()` 为 `Matrix3x2fStack`
  - `pushPose` / `popPose` → `pushMatrix` / `popMatrix`
  - `translate(x,y,z)` → `translate(x,y)`
- 1.21.8 的版本 API：
  - `getCurrentVersion().name()`
  - `getCurrentVersion().dataVersion().version()`

### 1.21.11

- `GuiGraphics`
- **不要**在 Screen 的 `render` 中再调用 `renderBackground`，否则
  `Can only blur once per frame`
- 输入：
  - `MouseButtonEvent`
  - `KeyEvent`
  - `CharacterEvent`
- 资源：`Identifier`

### 26.2

- `GuiGraphicsExtractor`
- `extractRenderState` 替代 `render`
- 背景自动提取，不要重复
- 输入：
  - `MouseButtonEvent`
  - `KeyEvent`
  - `CharacterEvent`
- 资源：`Identifier`
- 缩放矩阵使用 `pushMatrix` / `popMatrix`

## 4. 公共设置

`common/ArtSettings.java` 新增：

- `picturesPath`
- `maticPath`
- `schematicsPath`
- `shortcutKey1`
- `shortcutKey2`

`SpPaths`：

- 从设置解析图片 / 输出 / Litematica 目录
- 支持绝对路径和相对游戏目录路径
- `reload()` 重新创建目录

`SchemapartClient`：

- 启动时先加载设置，再初始化路径
- 双键快捷键从设置读取
- 默认 77 (M) + 80 (P)

## 5. 目录

默认：

```
mapart/pictures
mapart/matic
<game>/schematics
```

可在「设置 → 目录/快捷键」修改。

## 6. 生成命名

- MMAUI：开始制作 → 文件名输入框
- MWMAUI：生成 → 文件名输入框
- 如果文字材质重叠且未确认优先级，再弹优先级排序

## 7. 验证状态

| 版本 | 构建 | 游戏内 |
|---|---|---|
| 1.21.1 | 通过 | 实测通过 |
| 1.21.4 | 通过 | 待回归 |
| 1.21.8 | 通过 | 待回归 |
| 1.21.11 | 通过 | 待回归 |
| 26.2 | 通过 | 待回归 |

## 8. 注意

- 1.21.11 / 26.2 使用事件对象，不能直接复制旧输入签名。
- 26.2 使用渲染提取器，不能直接复制 `render`。
- 文字地图画引擎 `wordart/*` 不依赖 Minecraft API，可跨版本复用。
- `LitematicWriter` 已支持空气（`blockIndex < 0`）。
