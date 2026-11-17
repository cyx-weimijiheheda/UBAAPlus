# UBAA 认证与缓存首页移植

参考仓库：BUAASubnet/UBAA，提交 `6c4dba8d136da1c18ed01ebd4daf213a81ea3155`，仅作只读对照。

## WebVPN 图片验证

对照 `crates/ubaa_core/src/auth/automatic.rs`、`interactive.rs`、`captcha.rs` 和 `captcha_image.rs`：

- 并非取消学校验证码，而是在同一 WebVPN Cookie 作用域中本地识别图片。
- 只处理已支持的 image 类型、六位字母数字；最多自动尝试三次，失败交给原人工输入界面。
- 图像答案、图片 ID 和 execution 必须来自同一个准备页；提交前不重新 GET 登录页。
- POST 参数保持 `username/password/captcha/captchaResponse/execution/_eventId/submit/type`。
- 重试只使用已识别的验证码拒绝响应及其下一张挑战；不把超时或其它错误当作识别失败重提。
- Direct 保持人工输入；会话、Cookie、execution 不跨连接模式使用。取消或切换模式后的旧结果不能恢复界面登录态。

复用 UBAA 随仓库的 `common.onnx.gz` 和 `charset.json`，来源为 ddddocr 1.6.1 的 common_old 模型，经 UBAA 转为标准 ONNX LSTM。
Android 使用 ONNX Runtime 1.23.2 CPU 推理，五像素白边、170×64 灰度归一化输入、CTC 去重解码。
缩放使用本地双线性采样。模型和字符集不含用户验证码，识别完全在本机完成。
模型声明及 MIT 许可证见 [ddddocr-MIT.txt](licenses/ddddocr-MIT.txt)，参考工程许可证见 [UBAA-MIT.txt](licenses/UBAA-MIT.txt)。
ONNX Runtime 的 Java 接口依据：[官方文档](https://onnxruntime.ai/docs/get-started/with-java.html)。

## 会话未恢复时显示本机内容

对照 `packages/ubaa_app/lib/src/controller/recovery/startup.dart`、`packages/ubaa_ui/lib/src/features/home/shell_helpers.dart` 和 `packages/ubaa_host/lib/src/app_service.dart`：

- 首屏先按已确认账号显示已保存课表、待办、考勤和座位摘要，后台恢复会话，再增量刷新。今日课程与签到缓存只用于同一天，避免跨天显示昨日课程。
- 升级迁移只接受与本机课表账号匹配的已认证会话身份，不凭已保存的用户名密码推定身份。
- 缓存入口不等于在线认证。网络业务和学校写操作等待有效会话；离线课表可以读取，本地设置和关于页可访问。
- 部分来源更新失败时保留该来源缓存；成功返回空结果时移除旧记录。账号不匹配不读取、不覆盖缓存。
- 主动退出撤销缓存首页入口；不因保留密码而自动恢复该入口。
- 功能页面组件、布局、样式沿用 UBAAPlus，改动集中在启动、数据来源和操作回调。

## 公共更新和公告

与学校认证/连接方式独立。在同意应用声明后启动，并在回到前台时重新检查。
专用公共客户端不携带学校 Token/Cookie，也不会因公共接口 401 而刷新或删除学校会话。
保留成功获取的公共提示供断网时读取；更新缓存按当前客户端版本隔离，成功确认无公告时清除旧公告。

## 验证边界

Fixture/Mock 覆盖登录挑战配对、三次上限和转人工、缓存账号隔离、失败来源合并和公共客户端认证隔离。
JVM 用实际模型识别 UBAA 的公开合成样本 `a2b3c4`。这些检查不等同于真实账号或 Android 设备识别率验收。
未使用真实账号，也未执行任何学校业务写操作。
