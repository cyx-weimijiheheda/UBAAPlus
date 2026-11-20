# 2026-11-20 iClass 签到协议调查

使用现有 SSO 登录、隔离内存设置与 Cookie，在用户明确允许的同一排课目标上进行少量重复签到对比。
该目标在测试前已是 `signStatus=1`，不能用于证明请求能将未签到变成已签到。没有测试其他课程。

## 观察

- 旧 `stu_scan_sign.action` 表单方式，在二维码原始时间戳、服务端时间、去掉时间戳、额外表单字段及旧版路径/端口对比中，多次返回 HTTP 200、正文只有 CRLF；空正文不能作为成功或明确拒绝。
- 最初在 HttpSend 中改写请求的对比存在编码/构造干扰，只用于排查，不能据此判断新接口不可用。独立重建完整请求后，8181 的 `stu_auto_sign.action` 返回 `STATUS=0、result.stuSignStatus=1`；原目标已签到，不能证明它仍能完成首次签到。用户亦反馈现版 UBAA 无法实际签到，因此未将此候选接口直接替换进产品。
- 当前官方网页扫码使用 POST，将 `id` 加在 URL 查询中，保留二维码的 `courseSchedId/timestamp`。按此形式独立请求 8346/8347 的 `/app/course/stu_scan_sign.action`，均得到 `STATUS=1、ERRMSG=签到失败！`。没有进一步错误码证明唯一原因。

## 当前官方页面新增的流程

通过已认证的 8346 `?type=jumpMyCenter` 页面及其当前静态脚本，确认「我的」页面包含新版扫码授权：

1. 企业微信 OAuth 返回一次性 `code`。
2. POST `/app/course/get_sign_secret.action?code=...`，取得临时 `taskid/signSecret`。
3. 生成八位随机 `nonce`，以 `signSecret` 为密钥，对 `JYDSIGN + taskid + nonce + stu_scan_sign` 做 HMAC-SHA256，输出十六进制 `signature`。
4. 扫码 POST 的查询参数除排课、时间戳和 `id` 外，加入 `taskid/nonce/signature`；业务 `sessionId` 仍经请求头传送。

依据为当时学校部署的 `app.14f6cb4ce3996c7204c8.js` 和 `18.5d2c04777b2bec9cf687.js`。
通用客户端另含 `/esp` Authorization/刷新逻辑，但不能据此推定它就是本次失败原因。
没有保存脚本内学校运行配置、用户身份、OAuth code、临时签名材料或会话样本。

这条新授权链尚未在 UBAAPlus 接通或用真实的未签到目标验证。OAuth code 依赖企业微信授权，
不能用学号密码、伪造 code/签名或旧接口成功回执替代。当前调查不宣称签到功能已修复。

## 已修正的客户端结果语义

空正文、非成功 HTTP、缺少业务状态以及成功状态缺少签到结果，统一交给写入协调器只读回查。
只有学校对应排课明确显示已签到才展示「已核实当前课程为已签到」；这不表示本次请求促成了签到。
仍未签到、查询失败或无法匹配时保留结果未确认和防重提交标记，不把未知结果转成普通失败后自动重发。
学校明确拒绝继续保留原提示。回归覆盖空回执仍未签到、已签到核实、缺字段、HTTP 异常、明确拒绝与取消。
