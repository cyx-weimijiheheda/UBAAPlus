# UBAA Plus

北京航空航天大学校园服务的 **Android 客户端**。

[![Android CI](https://github.com/cyx-weimijiheheda/UBAAPlus/actions/workflows/android-ci.yml/badge.svg)](https://github.com/cyx-weimijiheheda/UBAAPlus/actions/workflows/android-ci.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

最初基于 [BUAASubnet/UBAA](https://github.com/BUAASubnet/UBAA)，现已独立演进，不再同步上游：

- 只保留 Android，移除 iOS / Desktop / Web
- 去掉 Kotlin Multiplatform 机制，客户端模块改为普通 Android library
- 重新设计主题（北航蓝）、凭据加密存储与 CI 流程

## 功能

统一认证、课表、空教室、智慧教室、图书馆座位、图书借阅、SPOC、博雅、运动场地、
阳光打卡、研讨室预约、寝室用电、校园网络、校园卡、自动评教、考试与成绩查询、希冀作业。

此外提供：

- **智慧教室**：今日课程一键签到，按「学年学期 → 课程」下拉查询考勤，标题栏扫码即可签到；
- **运动场地**：按「校区 → 运动类型 → 场馆类型」三级下拉筛选，支持预约记录查询；
  查询空闲时段、选择同伴并按指定时间自动预约，标题栏可打开每 10 秒刷新的会员预约码；
- **个人中心**：与「主页」并列的底部入口，展示身份信息、账号资料、版本与连接模式；
- **单入口页面**：图书馆座位、博雅课程、研讨室预约进入即为功能页，
  「我的预约 / 我的课程 / 统计 / 门锁密码」等次级入口位于页面内；
- **图书馆座位**：有效预约通过闸机自动签到或恢复使用，客户端仅提供取消预约、临时离开和完全离开；
  首页及座位页在前台每 30 秒刷新状态。暂离卡片与操作通知显示返回截止时间：优先使用平台数据，
  否则按本次成功暂离时刻估算（普通 30 分钟，用餐时段 2 小时，不超过预约结束时间）。
  后台提醒开启且通知权限允许时，在期限前 10 分钟重新查询，只有确认仍暂离才提醒；已返回或查询失败时不发。
- **寝室用电**：查询宿舍电表余额、电价与购电记录，确认后跳转校园支付平台；
  校区/楼宇/房间默认值可在设置中选择；查询需要连接校园网，校外访问会明确提示；
- **校园网络 / 校园卡 / 图书借阅**：原生展示流量、网费、校园卡余额、按日期查询的消费记录与借阅记录，
  账单、套餐、充值等详情通过已登录的浏览器打开；账户流量支持校园网与 WebVPN 查询，免费剩余包含套餐可用流量；
- **财务查询**：读取学校补助与收入记录，按所属年月筛选，展示应发、扣税和实发；个人资料弹窗补充财务门户的部门、证件、银行卡及联系方式，缓存按账户加密保存；
- **主题与功能自定义**：设置中提供北航蓝、北理绿、北大红、清华紫、室友粉五套配色，
  支持跟随壁纸与浅色/深色/跟随系统；功能页可切换网格/紧凑列表，
  并在「显示 / 不显示」两层之间用开关和上下按钮调整入口；
- **扫一扫**：主页与功能页右上角支持二维码/条形码扫描，可打开或复制；
- **博雅选课提示**：报名及启动抢课前核对已同步的课表、考试和已选博雅；冲突或资料不完整时提示确认。
- **自动任务开关**：设置页最底部的「？！！？」默认关闭，关闭时隐藏并禁用博雅抢课、场地抢单；
  每次开启先确认免责声明，运行中关闭立即停止任务。开启后可在预告课程详情设置定时抢课。
- **个人中心隐私**：头像卡只显示学院和学生类型，点击后查看学校实际返回的个人资料；校园卡与网费余额分卡展示，默认隐藏。
- **阳光打卡提醒**：默认根据已同步的本学期课表是否有体育课启用，用户手动设置优先。
- **本地诊断**：设置中可导出脱敏 JSON，记录版本、功能、路线、操作阶段与错误分类，不包含凭据、学校正文和个人资料。

支持两种连接方式：直连、WebVPN；首次启动时选择，之后可在设置中切换。
WebVPN 图片验证码在本机自动识别，最多尝试三次，失败后使用原有手动输入。
已登录过的账号启动时先显示本机保存的首页内容，后台恢复会话并更新；未恢复认证时，在线操作会引导登录。
今日课程与签到缓存按日期隔离；成绩、考试、作业和已选博雅使用加密业务缓存，刷新失败保留旧内容并标明状态。
主动退出后清除业务缓存和缓存首页入口。公告和版本检查独立于学校登录状态。
写操作结果未知时仅查询核对、不自动重发；希冀默认按校历刷新，明确的往期课程不再自动扫描。
实现与验证范围见 [认证与缓存首页说明](docs/auth-and-offline-home.md)。
OCR 保留自动识别，使用量化模型与压缩原生库打包；首页解析、加密缓存与小组件绘制转到工作线程。
设置提供「缓存 → 清理缓存」，保留登录信息、设置和已本地化课表。详见 [体积与加载优化](docs/ocr-and-home-performance.md)。

> 运动场地与博雅抢课只在应用位于前台时运行。按 Home 键、切换到其它应用或锁屏会立即停止
> 抢单任务；旋转屏幕、在页面之间切换不会中断。真实下单前请自行确认场馆规则。

主页标题旁显示后台更新进度；当天最后一门课程结束满一小时后，课程区切换为明日课表，按实际教学周读取，明日课程的签到按钮禁用。个人中心自动读取校园门户头像，未取得照片时显示姓名头像，不提供本机选图。校园网只保留一份账户流量明细，在线 IP 单独作为补充信息。

校园卡原页面下方提供消费查询：默认今天一天，两个日期选择框以「－」连接，起止日期均包含在范围内，不能选择未来日期或倒置范围。记录按时间从旧到新排列、按日期分组、每笔单独卡片；只读查询，不处理支付。头像和消费缓存均按账户加密保存，清理缓存会删除，普通退出保留。

## 桌面小组件与外观

桌面支持周课表、今日课程与待办、近日课程和独立待办四种小组件，可调整大小。
课表读取已本地化的数据，待办读取首页同步的快照，显示同步时间；点击小组件正文进入应用更新。
今日小组件按课程优先排列，左右箭头翻页可查看所有课程和待办，右侧「待办 / 课表」切换显示范围。
待办快照按账号隔离；普通退出保留，选择退出并清除本地数据时删除。过期的待办在渲染时过滤。
在「设置 → 桌面小组件」独立选择跟随系统 / 浅色 / 深色，以及跟随壁纸 / 应用配色 / 自定义颜色。
壁纸配色需要 Android 12 及以上，旧版本回退到应用配色。
主应用的显示模式位于「设置 → 外观」顶部，提供跟随系统 / 浅色 / 深色。
底部导航使用透亮的玻璃高光与边缘，普通内容卡片使用更稳定的磨砂底色；均为本地绘制效果，不依赖系统背景模糊。

## 模块结构

| 模块 | 类型 | 职责 |
| --- | --- | --- |
| `core/` | 纯 Kotlin/JVM 库 | DTO、API 契约、上游客户端，`server` 与 `shared` 共用 |
| `shared/` | Android library | 业务逻辑、平台实现、Compose UI |
| `androidApp/` | Android 应用 | 应用入口、清单与资源 |
| `server/` | Ktor 服务 | 独立后端；客户端不再提供服务器中转连接模式 |

依赖方向：`androidApp → shared → core`，`server → core`。

> `core` 只能放不依赖 Android API 的代码，也不能放 storage 实现：
> `multiplatform-settings-no-arg` 在纯 JVM 下会解析到 `java.util.prefs`，
> 而 Android 没有该包，运行期会崩溃。

## 开发环境

需要 JDK 21、Android SDK（Platform 36 / Build-Tools 36.0.0 / Platform-Tools），Gradle 由 wrapper 提供。

```bash
export JAVA_HOME="$HOME/.jdks/jdk-21.0.12.1+1"
export ANDROID_HOME="$HOME/Android/Sdk"
```

### 常用命令

| 命令 | 说明 |
| --- | --- |
| `./gradlew :androidApp:assembleDebug` | 构建 debug APK |
| `./gradlew :androidApp:installDebug` | 安装到已连接设备/模拟器 |
| `./gradlew :shared:testDebugUnitTest` | 客户端单元测试 |
| `./gradlew :core:test` | core 单元测试 |
| `./gradlew :server:test` | 后端测试 |
| `./gradlew :server:buildFatJar` | 打包后端可执行 jar |
| `./gradlew spotlessApply` | 代码格式化 |

### 运行

```bash
# 模拟器（需要 /dev/kvm）
emulator -avd <avd_name> -gpu host

# 或真机（开启 USB 调试）
adb devices
./gradlew :androidApp:installDebug
```

在 Fedora 上若模拟器启动即 SIGSEGV，且 `journalctl` 中出现
`avc: denied { execheap } for comm="RenderThread"`，说明 SwiftShader 的 JIT 被 SELinux 拦截，
改用 `-gpu host` 即可。

## 服务端

`server` 读取 `.env`（可从 `.env.sample` 复制），需要 Redis，默认监听 `0.0.0.0:5432`。
`JWT_SECRET` 必填且至少 32 字符，未配置时服务端拒绝启动。

```bash
cp .env.sample .env    # 编辑其中的 JWT_SECRET、REDIS_URI 等
./gradlew :server:run
```

## 客户端配置

API 地址在构建时通过 `.env` 或环境变量 `API_ENDPOINT` 注入，默认 `https://ubaa.mofrp.top`。

## 界面风格约定

整体视觉是**圆角 + 磨砂玻璃**。新增界面或控件请遵循以下规则，避免与既有页面风格割裂。

视觉方向参考 [FlClash](https://github.com/chen08209/FlClash) 的 Material You 层级与紧凑布局，自行实现，不移植其源码或资源；UBAA 仅作功能协议参考。保留主题/壁纸配色、深浅模式、导航液态玻璃和内容磨砂。页标题统一 20sp 半粗体，普通卡片标题 16sp，今日课程名 18sp，正文 14–16sp；字体跟随系统缩放。列表左右边距 16dp，课表与待办同宽；卡片避免重复标题行和装饰性大图标，触控操作仍至少 48dp。背景光晕与描边从轻，优先突出状态、时间与用户操作。


深浅模式的内容表面共用 `balancedSurfaces`：深色采用提升明度的蓝灰层级，浅色采用柔和灰蓝背景；卡片通过适量主题色和明度差区分，不依赖粗描边或纯黑纯白。壁纸配色同样套用表面层级，正文与次要文字保持可读对比度。个人卡片左侧头像与右侧姓名/身份信息整体居中，文本统一左对齐；学院和学生类型使用同一行紧凑的圆角标签，窄屏或大字体允许自然换行。个人资料手机号只保留前两位/末两位，证件号保留前两位/末一位，银行卡保留前四位/末四位，中间使用星号遮挡，短值不得完整显示。

磨砂材质在设置的“外观 → 磨砂材质”中调整：对比度、饱和度、亮度、不透明度和边缘强度均为 0–100。前三项以 50 为中性值。通过 `LocalUbaaGlassMaterial` 统一调整磨砂底色与边缘，不能给文字、图片或整个内容层套滤镜；导航液态玻璃和不透明弹窗独立。设置页预览草稿，✓ 应用后保存，× 关闭不修改，重置图标恢复默认草稿。

### 卡片一律磨砂

`Card` / `ElevatedCard` / `OutlinedCard` / 自定义 Surface 容器统一使用：

```kotlin
Card(
    modifier = Modifier.ubaaGlassSurface().fillMaxWidth(),   // 磨砂层放在链首
    colors = ubaaGlassCardColors(),                          // 容器透明，由磨砂层画底色
) { ... }
```

两个容易踩的坑：

- **`.ubaaGlassSurface()` 必须排在 modifier 链最前面。** Compose 的背景按节点最终尺寸绘制，
  排在 `padding()` 之后只会覆盖内边距以内，看起来像「磨砂比卡片小一圈、居中」。
  只有当 `padding()` 是**外边距**（卡片之间的间距）时才放在磨砂之前，例如
  `Modifier.padding(top = 16.dp).ubaaGlassSurface().fillMaxWidth()`；
  作为内容内边距的 padding 一律放磨砂之后。
- **卡片不要再加 `BorderStroke`**，磨砂层自带描边，叠加会出现双线。
  语义色卡片（错误提示、主色高亮）保留底色，但把 `containerColor` 设为半透明（如 `.copy(alpha = 0.86f)`），
  否则不透明底会把磨砂完全盖住。
  反之，`OutlinedButton` 这类会自绘描边的控件不要把磨砂直接挂上去（描边内缩会让玻璃显得偏大），
  应把磨砂挂在外层容器，控件本身设 `border = null`、`containerColor = Color.Transparent`。

### 选择类控件

其余控件统一使用 `UbaaControls.kt` 中的组件：普通/次要/文字/柔和按钮分别为 `UbaaButton`、`UbaaSecondaryButton`、`UbaaTextButton`、`UbaaTonalButton`；输入框为 `UbaaTextField`，搜索用 `UbaaSearchField`；开关/复选整行用 `UbaaToggleRow` / `UbaaCheckboxRow`。普通控件圆角 14dp、动作点击区域至少 48dp；图标动作使用带说明与长按提示的 `UbaaIconAction`。弹窗使用 `UbaaAlertDialog`，统一 24dp 圆角不透明主题底色（弹窗不使用磨砂）、标题区关闭图标及有界内容区；关闭使用 × 图标；对话框确认使用 `UbaaConfirmAction` 的 ✓ 图标，保留具体操作的无障碍描述、长按提示及原禁用条件；日期选择等原生弹窗同样使用 ✓。复制/日历/刷新/重置等工具动作使用图标；页面内预约、支付、取消预约等业务操作保留文字，明确名为“确认”的按钮使用 ✓。只读状态用 `UbaaStatusBadge`，可点击的次要操作用 `UbaaActionChip`。提交中显示进度并禁止重复点击，危险操作沿用确认流程；字段错误就近展示，首次加载、空状态和失败状态复用已有共用组件。

空教室与研讨室的时段展示使用 `UbaaAvailabilityTrack`：无网格描边，连续同状态合为圆角条，圆形序号与时段位置对齐；空教室表头写“节次”，研讨室保留真实起止时间。名称列固定，窄屏或大字体时只横向滚动时间轴。研讨室必须按真实时间间隔断开连续条，按原时段 ID 选择，并为不可选/可预约/已选提供无障碍说明。空教室默认选择精确名为“主楼”的楼栋，结果区不重复显示楼栋标签。

日期选择可使用 `DatePickerDialog`，沿用 24dp 圆角、不透明主题底色和标题关闭图标，保持日历专用宽度与有界滚动；日期范围在日历内禁用非法值，查询时再次校验。

并列选择统一为一栏磨砂分段选框：单选使用 `UbaaSegmentedTabs`，多选使用 `UbaaSegmentedMultiSelect`，不得将多选改成单选。组件按文字宽度与系统字号自动决定等宽铺满或横向滚动，不截断选项；单选选中项自动滚入可见区域，触控高度至少 48dp。既有下拉框保留；主题配色保留色样，选项说明放在选择栏下方。不要另写分散的 `FilterChip`、`RadioButton` 选项组。

| 场景 | 使用 |
| --- | --- |
| 表单下拉（楼馆、校区、时段、学期等） | `UbaaDropdownSelector`（圆角 14dp + 磨砂） |
| 平级栏目切换（借阅历史、校区切换等） | `UbaaSegmentedTabs` |
| 文本输入 | `OutlinedTextField(shape = RoundedCornerShape(14.dp))` |

不要手写 `OutlinedButton` + `DropdownMenu` 组合，也不要用 Material 默认的方角 `TabRow`。

### 卡片内操作按钮

「复制详情」「添加到日历」这类操作用**图标按钮**，放在卡片标题行右侧与标题齐平
（`UbaaSectionCard` 的 `trailing` 槽位），不要另起一行。

```kotlin
UbaaSectionCard(
    title = "提交信息",
    trailing = {
        UbaaIconButton(onClick = onAddToCalendar, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Event, contentDescription = "添加到日程", modifier = Modifier.size(18.dp))
        }
        UbaaIconButton(onClick = copy, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(18.dp))
        }
    },
) { ... }
```

### 自检

```bash
# 是否还有卡片用 Material 默认实色底（应无输出）
grep -rn "cardColors(containerColor = MaterialTheme.colorScheme.surface" --include=*.kt shared/src/main/kotlin
# 磨砂是否被 padding 排在前面影响（应无输出）
grep -rn "\.ubaaGlassSurface" -B 2 --include=*.kt shared/src/main/kotlin | grep -E "padding\(|\.size\("
```

## 许可

[MIT](LICENSE)