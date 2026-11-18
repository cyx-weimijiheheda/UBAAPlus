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
- **校园网络 / 校园卡 / 图书借阅**：原生展示流量、网费、校园卡余额与借阅记录，
  账单、套餐、充值等详情通过已登录的浏览器打开；校园网流量需要在校园网环境查询；
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

## 桌面小组件与外观

桌面支持周课表、今日课程与待办、近日课程和独立待办四种小组件，可调整大小。
课表读取已本地化的数据，待办读取首页同步的快照，显示同步时间；点击小组件正文进入应用更新。
今日小组件按课程优先排列，左右箭头翻页可查看所有课程和待办，右侧「待办 / 课表」切换显示范围。
退出登录后清除待办快照，并按账号隔离。过期的待办在渲染时过滤。
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
        IconButton(onClick = onAddToCalendar, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Event, contentDescription = "添加到日程", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = copy, modifier = Modifier.size(32.dp)) {
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