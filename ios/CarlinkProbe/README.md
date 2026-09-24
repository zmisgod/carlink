# Carlink / CarPlay 通信测试 App

本工程包含两个可打开的 Xcode scheme：

| Scheme | 用途 | 权限边界 |
|---|---|---|
| PhoneProbe | 普通 iPhone 页面发送操作，验证收发和回执 | 不申请 CarPlay entitlement；用自己的开发签名安装 |
| CarPlayProbe | 在 CarPlay 模拟器中验证列表按钮与相同通信客户端 | 工程内有模拟原型所用的 driving-task entitlement 声明；这不是 Apple 授权，也不是车控类别获批证明。正常 iPhone 真机 CarPlay 需要匹配的获批权限与签名 |

**当前实现是 LAN（局域网）通信，不经过 CPC200-CCPA 的 USB 自定义协议转发。**

链路：iPhone / CarPlay 按钮 → iPhone App 的 HTTP 客户端 → Android Carlink 测试接收器 → HTTP JSON 回执。

所有操作 `dryRun=true`，Android 只记录，**不控制空调或灯光**。`received` 表示收到操作，不表示车控执行成功。

## 先测试普通 iPhone 通信

1. 在车机安装本仓库生成的 **sideloadDebug** APK。正式 release 不显示测试入口，也不会启动接收器。
2. 打开 Carlink 设置 → **通信测试** → **开启测试接收**。页面显示可用 IPv4 地址（端口 8765）和本次随机配对码。
3. 先让 iPhone 与 Android 处于网络互通的局域网。服务器绑定车机网络接口；需选择 iPhone 能访问的地址。不是任选一个地址都能通。
4. 用 Xcode 打开 `CarlinkProbe.xcodeproj`，选 **PhoneProbe**。Signing & Capabilities 中选自己的 Team；必要时改 Bundle Identifier 为自己的唯一标识。连接 iPhone 后运行。
5. 在 iPhone 上允许“本地网络”权限，填入完整地址（例如 `http://192.168.1.100:8765`）和配对码。
6. 依次发送 Ping、24℃、开灯、关灯。Carlink 显示对应记录；iPhone 显示同一个请求 ID 和“已收到（未执行车控）”。
7. Android 切回投屏页不会停止接收。点击停止、销毁 MainActivity 或退出进程后停止；重新开启会生成新配对码。

接收器默认关闭，最多保留 50 条展示记录与 256 条去重回执；不落盘。设置页重新创建仍读取当前接收器状态，Activity 被销毁则需要重新开启。

这是仅供测试的明文 HTTP 接收器，配对码随开启重新生成。iOS 测试包放宽 ATS 以访问车机 HTTP 端点；生产版需要重新设计传输保护。

## 再测试 CarPlay UI

1. 选择 **CarPlayProbe** scheme，在 iOS 模拟器构建运行，先在手机页面填写接收端信息。
2. 从 Simulator 的 I/O → External Displays → CarPlay 打开 CarPlay 显示窗口。
3. 若该模拟器版本接受原型 entitlement 配置并显示应用，打开 Carlink 通信测试，点击列表操作。
4. 回执必须匹配请求 ID、action、value、source，Carlink 中 `source=carplay`，列表显示结果。

模拟器能够构建或显示应用，都不能证明权限申请能获批；如当前工具链不允许显示，不要把单独添加 plist 键视为授权。用户的车辆控制用途应真实提交 Apple 确认类别，不能用 driving-task 原型配置代替车厂权限。

**无线 CarPlay 会占用 iPhone 的 Wi-Fi 连接。** 普通局域网测试通过，并不保证开启无线 CarPlay 后仍可访问车机。必须在目标车辆的实际网络结构中重复测试。可以先分开验证 LAN 和 UI，再验证二者并行；有线 CarPlay 如可用，也可以减少无线网络拓扑干扰。

## 适配器诊断与尚未完成的路径

iPhone 页面“读取 iOS 可见的配件协议”调用 ExternalAccessory，列出系统可见的名称、厂家和协议。

- 此功能只读，不注册假协议，也不向适配器写未知命令。
- 清单为空不等于适配器无能力；iOS 可见性、厂商授权与固件实现都可能影响结果。
- 尚未实现/验证：自定义 EASession → CPC200-CCPA → USB → Android。
- 继续该路径需要厂家确认：External Accessory 协议名及 App 授权、会话建立方式、USB 消息类型与封包格式、回执通道。当前工程不能绕过缺失的固件转发功能。

## 测试协议 v1

`POST /v1/actions`，`Content-Type: application/json`，`Authorization: Bearer <配对码>`。

```json
{"version":1,"id":"唯一请求ID","source":"iphone","action":"climate.temperature.set","value":24,"dryRun":true}
```

`id` 必须为 1–64 个 ASCII 字母、数字、连字符。`source` 为 `iphone`、`carplay` 或 `cli`。

| action | value |
|---|---|
| ping | null |
| climate.temperature.set | 整数 16–30 |
| lights.cabin.set | true / false |

回执包含原始 id / source / action / value、`version=1`、`status=received`、`dryRun=true`、`transport=lan`、`receivedAt`（Unix 毫秒）。

同 ID 同内容返回原回执，不重复记录；同 ID 不同内容返回 409。配对码错误 401；业务参数不合法 422；非 JSON 415；体积超限 413。仅接受 Content-Length，暂不支持 chunked 请求体。最大正文 4096 字节，头部 8192 字节。

`GET /v1/health` 也要求配对码，返回服务标识与 dryRun / lan 状态。

## 自动验证

从仓库根目录：

```bash
bash ios/CarlinkProbe/Tools/test-bridge.sh
```

它编译 iOS 工程共用的 `BridgeClient.swift` 为 macOS CLI，通过真实 TCP/HTTP 连接运行在 Robolectric JVM 中的 **同一份 Android ProbeServer 代码**。验证四种指令及对应 ACK；同时检查错误鉴权、参数、去重、体积和关闭连接。它不是 iPhone → 实车的硬件测试。

Xcode 可对 PhoneProbe 执行 Test（⌘U），检查 JSON 类型和回执关联校验。工程同时保留 `project.yml`；更改文件列表后可用 `xcodegen generate --spec ios/CarlinkProbe/project.yml` 重新生成工程。

## 当前验证记录

见 [验证报告](../../documents/integration/carplay-probe-verification.md)。
