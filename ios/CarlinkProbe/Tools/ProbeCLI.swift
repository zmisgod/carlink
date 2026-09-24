import Foundation

/// Compiled with the SAME BridgeClient.swift used by both iOS UIs.
@main
struct ProbeCLI {
    static func main() async {
        guard CommandLine.arguments.count == 3 else {
            print("Usage: probe-cli http://host:port token")
            exit(2)
        }
        let client = BridgeClient()
        do {
            for (name, value) in [("ping", ProbeValue.none), ("climate.temperature.set", .temperature(24)),
                                  ("lights.cabin.set", .light(true)), ("lights.cabin.set", .light(false))] {
                let action = ProbeAction(action: name, value: value, source: "cli")
                let receipt = try await client.send(action, endpoint: CommandLine.arguments[1], token: CommandLine.arguments[2])
                print("ACK \(receipt.id) \(receipt.action) dryRun=\(receipt.dryRun) transport=\(receipt.transport)")
            }
        } catch {
            print("FAIL \(error.localizedDescription)")
            exit(1)
        }
    }
}
