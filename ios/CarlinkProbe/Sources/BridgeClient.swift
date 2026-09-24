import Foundation

enum ProbeValue: Codable, Equatable {
    case none, temperature(Int), light(Bool)

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() { self = .none }
        else if let value = try? container.decode(Bool.self) { self = .light(value) }
        else { self = .temperature(try container.decode(Int.self)) }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .none: try container.encodeNil()
        case .temperature(let value): try container.encode(value)
        case .light(let value): try container.encode(value)
        }
    }
}

struct ProbeAction: Encodable {
    let version = 1
    let dryRun = true
    let id: String
    let source: String
    let action: String
    let value: ProbeValue

    init(action: String, value: ProbeValue, source: String, id: String = UUID().uuidString) {
        self.id = id; self.action = action; self.value = value; self.source = source
    }
}

struct ProbeReceipt: Decodable {
    let version: Int
    let id: String
    let source: String
    let action: String
    let value: ProbeValue
    let status: String
    let dryRun: Bool
    let transport: String
    let receivedAt: Int64
}

enum ProbeError: LocalizedError {
    case configuration, http(Int), invalidReceipt
    var errorDescription: String? {
        switch self {
        case .configuration: return "请填写 http://车机IP:8765 和本次配对码"
        case .http(let code): return "接收端返回 HTTP \(code)（401 表示配对码不正确）"
        case .invalidReceipt: return "回执不匹配，不能确认操作已收到"
        }
    }
}

final class BridgeClient: NSObject, URLSessionTaskDelegate {
    // Never forward the pairing token to a redirect destination.
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest,
                    completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }

    func send(_ action: ProbeAction, endpoint: String, token: String) async throws -> ProbeReceipt {
        guard let url = URL(string: endpoint.trimmingCharacters(in: .whitespacesAndNewlines)),
              url.scheme == "http", url.host != nil, url.user == nil, url.password == nil,
              url.query == nil, url.fragment == nil, ["", "/"].contains(url.path),
              token.count >= 16, !token.contains("\r"), !token.contains("\n") else {
            throw ProbeError.configuration
        }
        var request = URLRequest(url: url.appendingPathComponent("v1/actions"))
        request.httpMethod = "POST"
        request.httpBody = try JSONEncoder().encode(action)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 5
        config.timeoutIntervalForResource = 8
        let session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        let (data, response) = try await session.data(for: request)
        guard let response = response as? HTTPURLResponse else { throw ProbeError.invalidReceipt }
        guard response.statusCode == 200 else { throw ProbeError.http(response.statusCode) }
        return try Self.validate(data, for: action)
    }

    static func validate(_ data: Data, for action: ProbeAction) throws -> ProbeReceipt {
        let receipt = try JSONDecoder().decode(ProbeReceipt.self, from: data)
        guard receipt.version == 1, receipt.id == action.id, receipt.source == action.source, receipt.action == action.action,
              receipt.value == action.value, receipt.status == "received", receipt.dryRun,
              receipt.transport == "lan" else { throw ProbeError.invalidReceipt }
        return receipt
    }
}
