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
- **寝室用电**：查询宿舍电表余额、电价与购电记录，确认后跳转校园支付平台；
  校区/楼宇/房间默认值可在设置中选择；查询需要连接校园网，校外访问会明确提示；
- **校园网络 / 校园卡 / 图书借阅**：原生展示流量、网费、校园卡余额与借阅记录，
  账单、套餐、充值等详情通过已登录的浏览器打开；校园网流量需要在校园网环境查询；
- **主题与功能自定义**：设置中提供北航蓝、北大红、清华紫、北林绿、室友粉五套配色，
  支持跟随壁纸与浅色/深色/跟随系统；功能页可切换网格/紧凑列表，
  并在「显示 / 不显示」两层之间用开关和上下按钮调整入口；
- **扫一扫**：主页与功能页右上角支持二维码/条形码扫描，可打开或复制；
- **博雅抢课**：在预告课程的详情页设置开抢时间与重试间隔，到点自动选课。

支持两种连接方式：直连、WebVPN；首次启动时选择，之后可在设置中切换。

> 运动场地与博雅抢课只在应用位于前台时运行。按 Home 键、切换到其它应用或锁屏会立即停止
> 抢单任务；旋转屏幕、在页面之间切换不会中断。真实下单前请自行确认场馆规则。

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

## 许可

[MIT](LICENSE)
