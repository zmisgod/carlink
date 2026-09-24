# CarPlay 测试工程验证记录

日期：2026-09-16。使用 Xcode 27.0、iOS 17.5 Simulator、仓库 Gradle / Android SDK。

## 交付

- `ios/CarlinkProbe/CarlinkProbe.xcodeproj`：PhoneProbe 与 CarPlayProbe 两个 scheme。
- `ios/CarlinkProbe/README.md`：签名、接收端开启、网络、权限边界和适配器协议诊断步骤。
- Android debug 版设置新增“通信测试”；接收器默认关闭，手动开启、会话配对码、dry-run 回执。
- `ios/CarlinkProbe/Tools/test-bridge.sh`：可重复执行的跨语言 TCP 测试。

## 已验证

| 验证项 | 结果 |
|---|---|
| Android `testSideloadDebugUnitTest` | 40 个通过，0 失败，0 跳过；包含此前的方控修复回归 |
| Android `assembleSideloadDebug` | 成功 |
| PhoneProbe 在 iOS 17.5 Simulator 执行 XCTest | 2 个通过；JSON 编码及请求/回执一致性 |
| CarPlayProbe 模拟器目标编译与本地签名 | 成功；不代表 Apple 已授予真机权限 |
| iOS 共用 Swift 客户端 → Android ProbeServer 的 TCP/HTTP 收发 | Ping、温度 24℃、车内灯开、车内灯关，4 次匹配 ACK |
| 错误配对码、非法动作/数值、dryRun=false、损坏 JSON | 拒绝且不记录 |
| 重复请求与请求 ID 冲突 | 原回执重用，不同内容返回 409 |
| 正文超限与半连接关闭 | 正确拒绝/关闭，监听可恢复，端口可释放 |

跨语言测试调用实际共享 `BridgeClient.swift`，接收端是运行在 Robolectric JVM 的实际 `ProbeServer.kt`，使用真实 TCP socket，没有模拟 HTTP 响应。iOS XCTest 在模拟器上执行。二者不能合并宣称为 iPhone 到实车端到端测试。

## 尚未验证

- CarPlay 模拟器界面实际显示及点击操作（UI 控制工具未能打开 Simulator 应用；仅完成构建）。
- 真 iPhone 连接真实车机，特别是无线 CarPlay 工作时的网络可达性。
- 正常 iPhone 上获批签名后的 CarPlay App 运行。
- 自定义 EASession → CPC200-CCPA → USB → Android 的适配器转发路径。
- 真实空调、灯光控制：本工程有意仅接收/回执，没有调用车辆控制接口。

当前适配器诊断只枚举 iOS 可见的配件协议，不发送未定义的固件命令。要完成用户原先提出的适配器路径，仍需拿到/实现厂家提供的协议与 USB 转发能力。

## 实车最小验收

1. 开启 Carlink 的 debug 测试接收器，记录局域网地址和配对码。
2. PhoneProbe 在 iPhone 上发送四个测试操作，两端显示匹配 ID 与相同 action/value。
3. 停止接收后发送应失败；重新开启须使用新配对码。
4. 满足权限条件后，用 CarPlayProbe 重复测试；接收日志应为 `source=carplay`。
5. 在目标无线 CarPlay 拓扑下重复第 4 步；若超时，先排查网络与本地网络权限，不把失败归为 USB 协议问题。

测试包为 debug 签名；若车机已装的版本采用不同签名，不能直接覆盖。不要为了安装测试包自动卸载并清空用户数据，应使用相同开发签名构建或另行安排测试设备。
