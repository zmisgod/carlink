import CarPlay

@MainActor
final class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {
    func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene,
                                  didConnect interfaceController: CPInterfaceController) {
        let definitions: [(String, String, ProbeValue)] = [
            ("测试连接", "ping", .none),
            ("测试空调 24℃", "climate.temperature.set", .temperature(24)),
            ("测试车内灯打开", "lights.cabin.set", .light(true)),
            ("测试车内灯关闭", "lights.cabin.set", .light(false)),
        ]
        let items = definitions.map { title, action, value in
            let item = CPListItem(text: title, detailText: "仅记录和回执，不执行车控")
            item.handler = { _, completion in
                Task { @MainActor in
                    defer { completion() }
                    let result = await ProbeModel.shared.send(action, value: value, source: "carplay")
                    item.setDetailText(result)
                }
            }
            return item
        }
        let template = CPListTemplate(title: "Carlink 通信测试", sections: [CPListSection(items: items)])
        interfaceController.setRootTemplate(template, animated: false, completion: nil)
    }
}
