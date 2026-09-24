import SwiftUI

@main
struct ProbeApp: App {
    var body: some Scene {
        WindowGroup { ProbeView() }
    }
}

struct ProbeView: View {
    @StateObject private var model = ProbeModel.shared
    var body: some View {
        NavigationStack {
            Form {
                Section("连接 Carlink 测试接收端") {
                    TextField("http://车机IP:8765", text: $model.endpoint)
                        .textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                        .accessibilityIdentifier("endpoint")
                    TextField("本次配对码", text: $model.token)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                        .accessibilityIdentifier("token")
                    Text("先在 Carlink 设置 → 通信测试中开启接收。两端需网络互通。所有操作仅记录，不执行车辆控制。")
                }
                Section("发送测试操作") {
                    operation("发送 Ping", "ping", .none)
                    operation("测试温度 24℃", "climate.temperature.set", .temperature(24))
                    operation("测试车内灯打开", "lights.cabin.set", .light(true))
                    operation("测试车内灯关闭", "lights.cabin.set", .light(false))
                }.disabled(model.busy)
                Section("最新结果") { Text(model.lastResult).textSelection(.enabled) }
                Section("适配器能力诊断") {
                    Button("读取 iOS 可见的配件协议") { model.inspectAccessories() }
                    Text("这里只读取配件信息，不发送自定义 USB 数据。清单为空不能证明适配器不支持；需要厂家确认开放协议与转发方式。")
                    ForEach(model.accessories, id: \.self) { Text($0).textSelection(.enabled) }
                }
                Section("记录") {
                    ForEach(Array(model.receipts.enumerated()), id: \.offset) { _, receipt in Text(receipt).font(.caption) }
                }
            }.navigationTitle("Carlink 通信测试")
        }
    }

    private func operation(_ title: String, _ action: String, _ value: ProbeValue) -> some View {
        Button(title) { Task { await model.send(action, value: value, source: "iphone") } }
    }
}
