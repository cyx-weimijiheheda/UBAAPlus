# UBAA Plus

北京航空航天大学校园服务的 **Android 客户端**。

[![Android CI](https://github.com/cyx-weimijiheheda/UBAAPlus/actions/workflows/android-ci.yml/badge.svg)](https://github.com/cyx-weimijiheheda/UBAAPlus/actions/workflows/android-ci.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

最初基于 [BUAASubnet/UBAA](https://github.com/BUAASubnet/UBAA)，现已独立演进，不再同步上游：

- 只保留 Android，移除 iOS / Desktop / Web
- 去掉 Kotlin Multiplatform 机制，客户端模块改为普通 Android library
- 重新设计主题（北航蓝）、凭据加密存储与 CI 流程

## 功能

统一认证、课表、空教室、智慧教室、图书馆座位、SPOC、博雅、运动场地、阳光打卡、
研讨室预约、希冀作业、自动评教、考试与成绩查询。

此外提供：

- **智慧教室**：今日课程一键签到、本学期考勤查询，标题栏扫码即可签到；
- **运动场地**：查询体育场馆空闲时段、选择同伴，并按指定时间自动预约；
  标题栏可打开每 10 秒刷新的会员预约码；
- **个人中心**：与「主页」并列的底部入口，展示身份信息、账号资料、版本与连接模式；
- **博雅抢课**：在预告课程的详情页设置开抢时间与重试间隔，到点自动选课。

支持三种连接方式：直连、WebVPN、服务器中转（默认）。

> 运动场地与博雅抢课只在应用位于前台时运行。按 Home 键、切换到其它应用或锁屏会立即停止
> 抢单任务；旋转屏幕、在页面之间切换不会中断。真实下单前请自行确认场馆规则。

## 模块结构

| 模块 | 类型 | 职责 |
| --- | --- | --- |
| `core/` | 纯 Kotlin/JVM 库 | DTO、API 契约、上游客户端，`server` 与 `shared` 共用 |
| `shared/` | Android library | 业务逻辑、平台实现、Compose UI |
| `androidApp/` | Android 应用 | 应用入口、清单与资源 |
| `server/` | Ktor 服务 | 后端网关 |

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
