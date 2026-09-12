# 吉利 OneOS 按键与 ECARX DIM

移植参考：`music-session-player` 的 VehicleKeyMonitor、PublicArtworkStore、
GeelyEcarxDimInteractionBridge 和导航 Presentation。厂商接口在运行时加载，
不打包厂商 framework，也不增加第二套播放器。车机按键沿用原项目的 AIDL 事务顺序。

## 使用

打开 Settings → Adapter Configuration → Keys：

1. 在现有 Next / Previous / Play-pause / Siri 操作旁点击录入，然后按实体键。
   新接入 OneOS 键码：上一曲 `200088`、下一曲 `200087`、播放/暂停 `200085`、
   微信 `200400`。微信键是输入，可映射到现有操作；CarPlay 协议没有专用微信命令。
   设置显示监听状态、最近事件、键名与键码。Android KeyEvent 继续支持。
2. “音乐与导航发布到 DIM”默认开启。音乐使用手机投影流的标题、歌手、专辑、
   时长、进度和播放状态；USB 音频和蓝牙音频模式均可发布。
3. 点击“公共封面目录：设置写入权限 / 重发”，授予公共目录写入权限。
   Android 11+ 使用系统的所有文件访问设置；Android 10 使用存储权限及 legacy 模式，
   最终是否可写受车机存储策略影响。失败显示原因并允许重试。
4. 导航需同时开启原有 **Cluster Navigation**，手机开始导航后转发现有的
   NavigationState 到 DIM。可另行开启“导航同时显示到 Android 副屏”。没有
   Presentation display 时界面显示不可用；该视图显示 TBT 转向、道路、距离和 ETA。

监听随主 Activity 前台生命周期注册、解绑，不申请 OneOS 按键拦截。
原车是否同时执行自身按键功能由固件决定。DOWN/UP 与短按摘要去重，
录入手势不会执行刚保存的映射；页面离开时释放已发送的 Siri 按住状态。
DIM 发布随 Activity 生命周期保留到销毁，导航结束、USB 清理及关闭开关会清理输出。

## 公共 JPEG 与音乐封面身份

公共路径：`Documents/Carlink/dim-artwork/<musicArtworkUuid>/<fileVersionUuid>.jpg`。
JPEG quality=90，最长边 192px；完整写入、同步、原子重命名、解码校验后提交 file URI。
每个身份目录最多保留 3 个文件版本，这不是“只能显示前三首歌”的限制。

按照后续封面反馈，DIM 身份已从原 A 方案的播放期随机歌曲 ID 改为
**歌名 + 艺人 + 专辑 + 封面内容哈希**的稳定 UUID。仅艺人和封面不能区分同一专辑的歌曲。
同样的歌曲与封面在重播、重启后得到同样的 ID；进度变化不改 ID，新的封面或文字元数据
会产生新的 ID。播放列表和播放信息同时使用该 ID，不使用随机 JPEG 文件名。
上游没有提供手机音乐库的歌曲 UUID，此 ID 是本地生成的内容标识。

CarPlay JPEG 和 Android Auto PNG 都可能先于歌名到达。现在把独立封面保留 2 秒，
允许紧接着的歌名变更消费一次，避免被切歌清空；过期或断连后清除。
这是没有上游关联 ID 时的有界时序兼容，超过窗口或上游根本没发封面仍需按日志定位。
Keys 设置显示封面输入字节数、DIM ID 和实际 JPEG URI；无封面时显示“上游尚未提供”。

## 导航与兼容性

导航来自手机投影的 NaviJSON 累积状态，不混入演示路线。保留剩余秒数，
未提供的航向/车道/经纬度不填模拟值。使用参考项目支持的基础转向类型；
复杂路口简化为对应左/右/环岛指引。Android 副屏提供 TBT 视图，未新增全地图视频链路。
原有 GM/AAOS Cluster 通道保留，DIM 是另一条输出通道。

日志标签：`VehicleKeys`、`GeelyEcarxDim`、`ArtworkProvider`。
“DIM 已调用”表示 API 调用完成，实际绘制仍需在目标车机确认。
缺少 OneOS 服务、ECARX framework 或权限时，设置显示原始失败状态。

## 验证

```sh
./gradlew :app:assembleSideloadDebug :app:testSideloadDebugUnitTest --offline
```

新增验证覆盖 OneOS 按键去重、短按独立上报、生命周期重置、歌曲身份保持、
秒级导航转换、未知字段处理、JPEG 尺寸/格式/保留数量和 A 方案 UUID。
实车验收需检查四个实体键的录入与触发、切歌/暂停/新封面、导航开始/结束、
副屏拔插、断连清理及从权限设置返回后的重发。

本次本地验证：Sideload Debug APK 构建成功，17 项单元测试通过。
完整 Lint 仍有 67 个既有错误，均位于未修改的 MediaSessionManager、
CarlinkMediaBrowserService 和 Theme；本次未增加 Lint 错误。
验证时未连接 ADB 设备，尚未执行实车验收。

音频和麦克风选择、试听与 Siri 排查详见 [音频诊断说明](audio-routing.md)。
