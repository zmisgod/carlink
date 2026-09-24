# CarPlay 方控接挂电话与独立车控 App

## 方控修复（2026-09-16）

反馈环境：适配器音频。用户现场日志尚未提供，以下为代码确认的缺口，不能当作实车已复现结论。

- `CarlinkManager.processMediaMetadata` 已收到 subtype 100 的 `CallStatus`，原先仅记录日志，方控不使用它。
- MediaSession 的上一曲 / 下一曲回调原先直接发送切歌命令，绕过组合按键的接挂判断。
- 原先将 `ALERT_STOP` 等音频事件直接清除来电标志；铃声结束和电话结束并非同一事件。
- 导航通道（audioType=2）的 `PHONECALL_STOP` 也会误清除主通道电话状态、停止麦克风。
- 旧日志把 `CallStatus=4` 标为 disconnecting；参考资料已纠正为 active。

修复后普通按键绑定、日志绑定、MediaSession 均经过同一个 CallKeyRouter：

| 状态 | 下一曲 / 接听电话 | 上一曲 / 挂断电话 |
|---|---|---|
| 空闲 | NEXT 204 | PREV 205 |
| 来电 | PHONE_ANSWER 300 | PHONE_HANG_UP 301 |
| 拨号、接通中、通话中 | NEXT 204（维持原行为） | PHONE_HANG_UP 301 |

`CallStatus`：0=结束，1=拨出，2=来电，3=接通中，4=通话中。已知的通话以 CallStatus=0 结束，避免音频通道清理覆盖它。音频开始事件提供提前信号；未收到通话元数据的设备继续使用主音频通道作为回退。未知状态不清除已有状态；断开、停止、重连清理会重置。

新增独立接听 / 挂断绑定，直接发送 300 / 301，不依赖来电检测。按键释放不重复发送。它们也可用于判断“状态识别失败”与“适配器收到了电话指令但没执行”的区别。

限制：若设备既无 CallStatus，也无可靠电话音频事件，组合按键不能准确判断来电；需要独立绑定。若设备只发送部分通话元数据却漏掉结束状态，需要实车日志进一步适配。App 不能凭铃声静音推断电话已结束。

### 验证

- 自动测试：CallKeyRouterTest 覆盖元数据来电、状态 4、拨出、音频事件交错、导航通道清理、拒接/漏接、断开重置、未知状态、独立绑定与释放。
- 构建命令：`./gradlew :app:testSideloadDebugUnitTest :app:assembleSideloadDebug`。
- 本次结果：构建成功；34 个单元测试全部通过，其中新增通话按键测试 14 个。未连接实车验证。
- 待实车验证：播放音乐时来电，下一曲接听、上一曲挂断；来电时直接上一曲拒接；对方取消；通话同时导航播报；结束后恢复切歌；普通绑定与日志绑定分别验证。
- 诊断日志：`[CALL_STATUS]`、`[CALL_STATE]`、`[INPUT_KEY]`、`[SEND]`。USB 写成功只代表命令发出，不代表 iPhone 已执行。

## 独立 CarPlay 车控 App 的可行性

用户目标：23 款吉利博越 L、银河 OS，在 iPhone 投射的 CarPlay 内打开独立 App、操作设置并将消息回传到 Android；原车灯光/空调接口不在本次调查范围。

**Apple 支持这种架构，但当前 Android 工程和已知适配器接口不足以交付它。**

需要的链路：

1. iPhone 上安装具有相应 CarPlay entitlement 的车控 App，提供界面与操作事件。
2. 车控 App 与 CarPlay 接收端声明匹配的 External Accessory 协议名。接收端支持 iAP2 外部协议会话及 CarPlay 标记。
3. 在本项目中，接收端是 CPC200-CCPA 适配器。需要适配器固件将该自定义会话的数据转发给 Android，并提供回复通道；不能假定音视频 USB 通道自动转发自定义业务消息。
4. Android 收到明确的业务消息后再交给车型控制接口，并回传执行结果。

本仓库只有 Android App，没有 iPhone App、对应签名权限配置，也没有已实现的自定义 External Accessory 会话注册、收发和固件桥接。现有 `REQUEST_HOST_UI=3` 是车标返回本机界面的通知，当前打开本应用设置页；不包含灯光、温度等字段。`CARPLAY_CONTROL=0x0B`、Dashboard 信息等命名也不能视为可用的自定义车控收发协议。

因此本次没有添加伪造的 CarPlay App、假消息接口或跳回原车机的替代页面。若要继续该路线，前置条件是 iOS App/CarPlay 权限，以及适配器供应方提供或实现外部协议到 USB 的双向桥接。即使只做回传、不接实际车辆控制，这些条件仍然存在。

### 依据

- [Apple：CarPlay / Automaker apps](https://developer.apple.com/carplay/)：车厂 App 可在 CarPlay 内控制车辆功能。
- [Apple：Requesting CarPlay Entitlements](https://developer.apple.com/documentation/carplay/requesting-carplay-entitlements)：权限申请、审核与签名配置。
- [Apple WWDC16：Developing CarPlay Systems, Part 1](https://developer.apple.com/videos/play/wwdc2016/722/)：车厂 App 需要匹配协议名称、iAP2 外部协议会话以及 ExternalAccessoryProtocolCarPlay；参见 automaker apps 段落。
- [适配器研究：USB protocol / CallStatus](https://github.com/lvalen91/CPC200-CCPA_resources/blob/main/documentation/02_Protocol_Reference/usb_protocol.md)：subtype 100、状态枚举和音频事件关系。
- [适配器研究：audio protocol](https://github.com/lvalen91/CPC200-CCPA_resources/blob/main/documentation/02_Protocol_Reference/audio_protocol.md)：主/导航通道和来电事件顺序。
- [适配器研究：command details](https://github.com/lvalen91/CPC200-CCPA_resources/blob/main/documentation/02_Protocol_Reference/command_details.md)：REQUEST_HOST_UI 与 PhoneAnswer/PhoneHungUp。
