# Schemapart 响应式 UI 与 DPI 处理

## 1. 坐标系统

所有界面布局都使用 Minecraft 的 **GUI 逻辑像素**：

- `Screen.width` / `Screen.height`
- 鼠标事件传入的 `mouseX` / `mouseY`
- `GuiGraphics` 的绘制坐标

不要使用物理 framebuffer 尺寸或硬编码 DPI 比例。

## 2. 响应式原则

- 面板宽高由 `width` / `height` 计算，并使用 `Math.min` / `Math.max` 夹取
- 紧凑模式阈值按实际可用高度判断，而不是固定分辨率
- 文字宽度使用 `font.width` / `plainSubstrByWidth` 裁剪
- 按钮、列表、滚动条坐标全部基于当前布局变量
- 小窗口下允许滚动，不把固定行数写死

## 3. 各界面

### WelcomeUI

- 两个卡片宽度由可用宽度均分
- 卡片高度使用 `min(height - 96, 300)`
- 标题、图标、底部文字按卡片中心定位
- 小窗口下卡片仍保持最小宽度

### MAUI

- 单栏图片网格
- 列数：`max(1, (w - 16 + 6) / 84)`
- 单元格固定 `78×90`，横向/纵向滚动
- 头部按钮按右对齐排布

### MMAUI

- 左侧预览宽度：
  `max(190, min(width - 160, (width - 16) * 0.64))`
- 右侧参数面板使用 compact / narrow 两档
- `compact = panelH < 265`
- `narrow = rightW < 235`
- 参数行高度 `compact ? 23 : 30`

### MWMAUI

- 全屏画布
- 右侧栏宽度 `180–210` GUI 像素
- 侧栏内容可滚动；背景点击区域先于控件注册
- 尺寸行使用紧凑布局：标签 + 减号 + 数值 + 加号
- 画布缩放范围 `0.03–8.0`
- 地图纹理按整幅画布上传，不做物理像素假设

### BlockSelect

- 动态网格：
  `cell = clamp(min(availW / 9, availH / 6), 24, 48)`
- `pageSize = columns * rows`
- 搜索框在窄屏单独占一行
- 分页和操作按钮分两行，避免重叠

## 4. 叠加层

- 下拉列表、右键菜单、优先级弹窗、命名弹窗在最后绘制
- 点击列表先于底层控件注册，保证覆盖层优先命中
- 弹窗坐标夹取到屏幕内
- 滚动内容使用 `enableScissor` / `disableScissor`

## 5. 文字

- 中文使用系统字体回退
- 所有可裁剪文本都经过 `trim(...)`
- 行高按字体实际高度计算，不假设固定 9px
