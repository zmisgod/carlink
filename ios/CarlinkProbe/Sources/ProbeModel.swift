import SwiftUI
import ExternalAccessory

@MainActor
final class ProbeModel: ObservableObject {
    static let shared = ProbeModel()
    @Published var endpoint: String { didSet { UserDefaults.standard.set(endpoint, forKey: "endpoint") } }
    @Published var token: String { didSet { UserDefaults.standard.set(token, forKey: "token") } }
    @Published var lastResult = "尚未发送"
    @Published var receipts: [String] = []
    @Published var accessories: [String] = []
    @Published var busy = false
    private let client = BridgeClient()

    init() {
        endpoint = UserDefaults.standard.string(forKey: "endpoint") ?? "http://"
        token = UserDefaults.standard.string(forKey: "token") ?? ""
    }

    func send(_ action: String, value: ProbeValue, source: String) async -> String {
        guard !busy else { return "正在等待上一条回执" }
        busy = true
        defer { busy = false }
        let command = ProbeAction(action: action, value: value, source: source)
        do {
            let receipt = try await client.send(command, endpoint: endpoint, token: token)
            lastResult = "已收到（未执行车控）\n\(receipt.action)\nID: \(receipt.id)\n通道：局域网"
        } catch {
            lastResult = "发送失败：\(error.localizedDescription)\nID: \(command.id)"
        }
        receipts.insert(lastResult, at: 0)
        receipts = Array(receipts.prefix(30))
        return lastResult
    }

    func inspectAccessories() {
        accessories = EAAccessoryManager.shared().connectedAccessories.map {
            "\($0.name) · \($0.manufacturer)\n公开协议：\($0.protocolStrings.joined(separator: ", "))"
        }
    }
}
