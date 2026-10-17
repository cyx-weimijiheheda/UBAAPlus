# CLAUDE.md

## Git 提交约定

**提交信息中不得出现任何 Claude / AI 署名。** 具体地：

- 不要添加 `Co-Authored-By: Claude` 或任何类似的署名 trailer
- 不要在提交信息正文里写"由 Claude 生成"、"Generated with Claude Code"之类的话
- 提交的作者与提交者应当只是仓库配置的本人身份

原因：这是个人项目，提交历史对外表现为本人完成的工作。

## 项目概况

UBAA Plus —— 北京航空航天大学校园客户端（Android）。

最初基于 `BUAASubnet/UBAA`，现已**独立演进**：移除了 iOS / Desktop / Web 平台与
Kotlin Multiplatform 机制，只保留 Android。仓库不再跟踪上游，也没有 `upstream` remote；
如需参考上游历史，可在本地临时添加远端，但不要直接合并。

### 模块结构

```text
core/       纯 Kotlin/JVM 库：DTO、API 契约、上游客户端（与 server 共用）
shared/      Android library：业务逻辑 + 平台实现 + Compose UI
androidApp/  Android 应用入口
server/      Ktor 后端网关（依赖 core）
```

### 抢单任务约定

运动场地（`api/venue/VenueGrabController`）与博雅抢课（`ui/screens/bykc/BykcGrabController`）
共用同一套约束：

- **只能在前台运行**：监听 `AppForegroundMonitor`（基于 `ProcessLifecycleOwner`，旋转屏幕不会误判），
  进入后台立即取消任务并标记为 `BACKGROUND`。
- 任务由 ViewModel 持有，离开页面不取消；注销、切换连接模式时必须显式停止。
- 运动场地只走直连（`cgyy.buaa.edu.cn/venue-server`），不经过服务端中转；没有 SSO 会话时
  用已保存的学号密码自动建立，仍失败则显示页面内登录表单（含 CAS 验证码）。
- 验证码是点击文字类型，必须人工点击；验收时不要调用真实的场地 `order/submit` 接口。

### 构建环境（Fedora）

本机环境已配置，`~/.bashrc` 中有 `# --- Android 开发环境 ---` 标记块：

- `JAVA_HOME=~/.jdks/jdk-21.0.12.1+1`（Temurin 21，Fedora 仓库无 JDK 21）
- `ANDROID_HOME=~/Android/Sdk`（Platform 36 / Build-Tools 36.0.0 / Platform-Tools）

常用命令：

```bash
./gradlew :androidApp:assembleDebug        # 构建 debug APK
./gradlew :androidApp:installDebug         # 安装到已连接设备
./gradlew :core:test                       # core 单元测试
./gradlew :shared:testDebugUnitTest        # shared 单元测试
./gradlew :server:test                     # 后端测试
./gradlew :server:buildFatJar              # 打包后端可执行 jar
./gradlew spotlessCheck                    # 检查格式（CI 会跑）
./gradlew spotlessApply                    # 自动格式化
```

CI：`.github/workflows/android-ci.yml`，push / PR 时跑 format 检查、构建与三套测试。