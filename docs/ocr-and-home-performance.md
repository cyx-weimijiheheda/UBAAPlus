# OCR 体积与首页加载优化

## OCR

保留本地识别及原手动验证码兜底，不移除 CPU 架构或字符集。

模型继续来源于 UBAA `6c4dba8d`（ddddocr common_old）。该模型的卷积与输出矩阵已经量化，只有迁移为标准 LSTM 的权重仍为浮点数；使用 ONNX Runtime 1.24.1 的离线量化工具，仅将此 LSTM 改成 DynamicQuantizeLSTM。Android/JVM 推理依赖仍为 1.23.2。

- 原始 ONNX：26,188,804 字节；量化后 ONNX：13,657,501 字节。
- gzip 资源：7,938,307 → 7,753,547 字节；模型主要减少解压后的内存与文件大小，单靠 gzip 变化不能大幅缩小 APK。
- 原模型解压 SHA256：`6cecb29a3d5a477444becd1b1477a63d48e7684e12eaa1f733c1e670c9e1b01a`。
- 新 gzip SHA256：`138ab2d7e80b8c21c473a7a3a6eaaa56fc9e89e77ccad2be3200f375c9255ee5`。
- 再现脚本：[optimize_captcha_model.py](../tools/optimize_captcha_model.py)，离线工具环境为 `onnx==1.20.0`、`onnxruntime==1.24.1`。原模型可从上述冻结 UBAA 源码取得；脚本不接受已经量化过的输出再次量化。
- 34 个公开合成样本的亮度、对比度、旋转与噪声变体，原模型与新模型预测完全相同；JVM 1.23.2 实际模型测试还覆盖原样本和亮度变化。这不是学校验证码准确率或 Android 真机性能的证明。

APK 的主要膨胀来自四个架构的未压缩 ONNX Runtime 原生库。因此应用配置 `packaging.jniLibs.useLegacyPackaging=true`，在 APK 中压缩原生库，安装时由系统按设备架构解压。它缩小分发 APK，不表示原生库的逻辑大小减少。

依据：[ONNX Runtime 量化文档](https://onnxruntime.ai/docs/performance/model-optimizations/quantization.html)、[Android 原生库打包选项](https://developer.android.com/reference/tools/gradle-api/9.0/com/android/build/api/dsl/JniLibsPackaging)。

## 与 UBAA 安装包的差异及 Release 裁剪

对照 UBAA `6c4dba8d` 的 `crates/ubaa_core/src/auth/captcha.rs` 和工作区 Cargo 配置，它使用 Rust `tract-onnx`（关闭默认特性）执行同源 ddddocr 模型。Plus 使用 ONNX Runtime Android 通用推理库；两者的推理库、应用框架、资源与发布方式不同，不能将 APK 大小直接归因于验证码模型优劣。此处未取得同版本同架构的 UBAA APK 做逐文件比较。

此前 Plus Release 未开启 R8。现在启用代码优化与资源裁剪，并为 ONNX Runtime 的 JNI 桥保留原始类、构造与字段名称，避免 native 查找失效；不增加全应用保留规则。CI 另行保存每次 Release 的 mapping，供异常堆栈还原。

同一份 1.6.0 构建 19 的源码及模型，裁剪前后实测：

| 构件 | 裁剪前 | 裁剪后 |
|---|---:|---:|
| arm64 Release | 38.97 MiB | 25.98 MiB |
| 通用 Release | 60.91 MiB | 47.91 MiB |
| x86 Release | 40.23 MiB | 27.23 MiB |

arm64 包内压缩后 DEX 从 15.42 MiB 降到 2.70 MiB；模型仍约 7.40 MiB，ORT 原生库仍约 6.68 MiB，图书馆楼层座位图等其他资源约 9.14 MiB。Debug 保持不混淆，通用调试包仍约 71 MiB；日常安装应比较相同 ABI 的 Release 包。上述数字属于 APK 分发体积，不等于安装后的占用。

依据：[Android R8 官方说明](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)。如要进一步缩减 ORT 本身，需要按模型算子定制原生运行库或移植推理引擎；[ORT 官方定制构建](https://onnxruntime.ai/docs/build/custom.html) 需要独立的原生编译与 Android 验证，不以更换一个模型文件替代。

## 首页

代码审计发现的主线程成本及处理：

- 每个业务响应保存缓存时，原实现遍历、解密全部缓存正文计算容量；现在只初始化一次容量索引，增量维护大小与时间。有界内存缓存复用已读 Entry 与解析后的 DTO，包含缺失项缓存；清理、失效和存储切换同步丢弃内存项。
- 学校读取的网络后处理、HTML/JSON 解析、加解密与业务缓存操作放到 IO 调度器。研讨室和场地首页读取也使用工作线程。
- 首页本机快照异步读取与写入，内容未变时不重复写入或触发小组件刷新；体育课默认值只观察课表内容变化，不随每个成绩/作业响应重读整学期。
- 避免日期监听、初始导航和预约轮询重复发起首屏加载。跨日刷新、手动刷新与 30 秒预约状态轮询保留。
- 小组件广播使用 goAsync，工作线程串行绘制，不阻塞前台帧；单次更新复用已解析的本地课表，提交前再次检查账号。

确定性测试验证容量索引不会每次写入重扫、重复读取不再访问存储、解析离开调用线程、清理后的旧结果不能回写，以及缓存账号隔离。尚未连接 Android 真机测量帧时间，不据此声称固定的帧率或启动耗时。

## 缓存清理

设置中的「缓存 → 清理缓存」显示可清理持久化业务缓存正文的约计大小（MB），进入页面及清理后在工作线程计算；不计登录信息、本地课表或 OCR 等应用资源，也不表示系统统计的完整磁盘占用。设置按外观、功能、通知、桌面小组件、寝室用电、连接模式、缓存、问题诊断排列，「？！！？」仍位于最末。清除首页、成绩、考试、作业、余额、待办快照和相关内存缓存；保留账号身份、密码/Token/Cookie、偏好、已手动本地化的课表，以及未决写入保护与座位暂离提醒。

清理会推进缓存代次，旧网络结果和旧首页持久化任务不能重建已清理数据。业务页面使用独立 ViewModelStore，清理后释放旧实例与任务；学校认证 ViewModel 不属于该 Store。再次访问页面再重新读取，不在设置页清理后立即全量联网回填。
