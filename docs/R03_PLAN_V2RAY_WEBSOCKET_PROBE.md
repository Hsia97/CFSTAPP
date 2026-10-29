# R03_PLAN: 专属通道 (WebSocket) 全链路穿透校验方案 (定稿版)

## 一、 背景与业务目标 (Evidence-Based)

在实际使用中，用户使用客户端节点套 Cloudflare CDN 加速。
原测速逻辑筛选出了部分特定网段（如部分亚太 IP），虽然 TCPing 延迟极低，但在实际建立 WebSocket 协议通道时却无法连接。
根本原因在于：部分边缘专有节点对外部域名的 WebSocket 代理权限受限，或在骨干网处遭受特定干扰。
为解决此痛点，实现 **“专属通道 WebSocket 全链路穿透校验”**，只有真正穿透到源站后端并拿到协议指纹的 IP 才能通过。

---

## 二、 核心技术原理与金标指纹

### 1. WebSocket 协议升级握手指纹
构造带真实业务路径的 HTTP/1.1 WebSocket 升级请求：
```http
GET {wsPath} HTTP/1.1
Host: {domain}
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Version: 13
```
- **真通判定标准**：
  当全链路（客户端 -> CF 边缘节点 -> TLS 握手 -> CF 反代回源 -> 源站反代 -> 业务后端）完好时，后端返回 `101 Switching Protocols` 或由于缺少认证载荷返回 `400 Bad Request` 且带 `Sec-WebSocket-Version: 13`；
- **淘汰标准**：
  任何网络断开、超时、RST、CF 边缘 Error 1000/1001/1006/1020、或源站宕机（521/502）均无法返回该特征，直接淘汰！

### 2. 耗时保护（2 秒极速短熔断）
- 超时设置为 `2000ms`（连接 2s，读取 2s）；
- 并发度受 `Semaphore(maxConcurrentPings)` 保护；
- 失败秒断，成功半秒内完成，整批候选 IP 校验在 2~3 秒内平滑完成。
