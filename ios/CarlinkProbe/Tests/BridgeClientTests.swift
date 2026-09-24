import XCTest
@testable import CarlinkPhoneProbe

final class BridgeClientTests: XCTestCase {
    func testCommandEncodingPreservesTypesAndDryRun() throws {
        for (value, expected) in [(ProbeValue.temperature(24), "24"), (.light(true), "true"), (.none, "null")] {
            let action = ProbeAction(action: "test", value: value, source: "iphone", id: "example")
            let body = String(data: try JSONEncoder().encode(action), encoding: .utf8)!
            XCTAssertTrue(body.contains("\"value\":\(expected)"))
            XCTAssertTrue(body.contains("\"dryRun\":true"))
        }
    }

    func testReceiptMustMatchRequest() throws {
        let action = ProbeAction(action: "ping", value: .none, source: "iphone", id: "example")
        let good = """
        {"version":1,"id":"example","source":"iphone","action":"ping","value":null,"status":"received","dryRun":true,"transport":"lan","receivedAt":1}
        """
        XCTAssertEqual(try BridgeClient.validate(Data(good.utf8), for: action).id, "example")
        for bad in [good.replacingOccurrences(of: "example", with: "other"),
                    good.replacingOccurrences(of: "true", with: "false"),
                    good.replacingOccurrences(of: "lan", with: "usb"),
                    good.replacingOccurrences(of: "received\"", with: "executed\"")] {
            XCTAssertThrowsError(try BridgeClient.validate(Data(bad.utf8), for: action))
        }
    }
}
