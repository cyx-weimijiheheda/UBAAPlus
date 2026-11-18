# 控件规范审查

范围：本仓库 Compose 界面。此文为下一轮改造建议，不代表表格中控件已全部更新。

本轮已完成：并列单选/多选使用同一栏磨砂分段选择器，保留多选、禁用及取消选择的行为；长选项和大字号自动横向滚动，单选选中项滚入可见区域；已有下拉框不变。

## 建议优先级

| 优先级 | 控件 | 现状举例 | 建议规范 |
|---|---|---|---|
| 高 | 图标按钮 | `SpocAssignmentDetailScreen`、`JudgeAssignmentDetailScreen` 的复制/日历按钮、`FeatureOrderScreen` 排序按钮仍指定 32dp 布局 | 图形 18–24dp，布局槽位至少 48×48dp，同排居中。Compose 可能扩展触控范围，不能因此判定 32dp 图形不可点击，但布局仍应为相邻控件留出空间 |
| 高 | 文本输入与搜索 | `ClassroomQueryScreen` 搜索框为 12dp 圆角，宿舍默认值等输入框为 14dp；字段错误提示未统一 | 共用输入组件，统一 14dp；搜索用搜索图标、清空动作和一致高度。明确输入项的 label、placeholder、keyboardType、IME 动作；校验错误在字段下方显示，网络错误使用页面提示 |
| 高 | 操作层级与等待反馈 | 各页直接组合 Button、OutlinedButton、TextButton；部分操作仅以 Toast 反馈 | 每个操作区一个主要动作；次要操作使用弱化样式，破坏性动作用错误语义色。提交中按钮显示进度并锁定重复点击；禁用原因就近说明 |
| 中 | 弹窗与操作区 | 课程详情、图书馆地图、同伴管理和验证码分别采用 AlertDialog、自定义 Dialog/Surface | 简单确认沿用 AlertDialog；长表单、图片等保留适合的专用容器，共用标题、关闭按钮、滚动区与底部动作规范。确认/取消顺序一致，长文本及大字体不遮住按钮 |
| 中 | 加载、空状态、失败状态 | 已有 `UbaaLoadingState` / `UbaaEmptyState` / `UbaaErrorState`，但成绩、博雅统计、希冀详情等仍直接显示 Text；加载圈也有多种尺寸 | 首次加载、下拉刷新、按钮提交分开处理；共用空状态与错误组件，文案说明下一步，按场景提供刷新/重试。后台刷新时保留已有内容 |
| 中 | 状态标签 | 首页 `TodoChip`、博雅状态及课表颜色各自定义 | 提取 `UbaaStatusBadge`，统一高度、字重、内边距与成功/警告/错误/中性颜色；文字说明状态，不单靠颜色；覆盖深浅模式 |
| 中 | 开关与复选行 | 设置中的开关、场地中的复选项存在不同的行结构 | 标签、说明与控件统一对齐；整行只有一个切换回调和无障碍节点，避免父子重复触发。需要免责声明的开关仍先确认，不能绕过原逻辑 |

## 约束与验证

- 保留现有主题色、深浅模式、液态玻璃导航及磨砂卡片，功能规则与数据更新逻辑不随控件改造改变。
- 优先扩展 `ui/common/components`，避免每页复制一套样式；逐类迁移，区分非交互图标、动作按钮和状态标签。
- 后续装机检查：窄屏、系统字体放大、长课程名、TalkBack、键盘输入、提交中与失败状态。此轮仅完成源码审查和本地构建测试，未做设备侧观感验收。

参考：[Compose 默认无障碍行为](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)、[Compose 弹窗](https://developer.android.com/develop/ui/compose/components/dialog)、[Material 3 组件](https://developer.android.com/develop/ui/compose/designsystems/material3)。
