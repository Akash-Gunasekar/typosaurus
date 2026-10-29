import Foundation
import Security

/// Settings and API keys shared between the app and the keyboard extension.
/// Settings live in the App Group's UserDefaults as the same JSON the Android app uses;
/// keys live in a shared Keychain access group, never in UserDefaults.
enum SharedStore {
    static let appGroup = "group.com.example.typosaurus"
    // Keys go to the first group in keychain-access-groups, which both targets list as the shared one.

    private static var defaults: UserDefaults { UserDefaults(suiteName: appGroup) ?? .standard }

    // MARK: settings

    /// The subset of Android's AppSettings the iOS app edits; the shared core fills in the rest.
    struct Settings: Codable, Equatable {
        var defaultProviderId: String = "anthropic"
        var defaultModels: [String: String] = [:]
        var consentGiven: Bool = false
        var triggersEnabled: Bool = true
        var triggerPrefix: String = "/"
        var streaming: Bool = true
    }

    static var settings: Settings {
        get {
            guard let data = defaults.data(forKey: "app_settings"),
                  let s = try? JSONDecoder().decode(Settings.self, from: data) else { return Settings() }
            return s
        }
        set {
            if let data = try? JSONEncoder().encode(newValue) { defaults.set(data, forKey: "app_settings") }
        }
    }

    /// JSON handed to the Kotlin `Assistant`.
    static var settingsJson: String? {
        defaults.data(forKey: "app_settings").flatMap { String(data: $0, encoding: .utf8) }
    }

    // MARK: API keys (Keychain)

    private static func query(_ provider: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "typosaurus.apikey",
            kSecAttrAccount as String: provider,
        ]
    }

    static func apiKey(for provider: String) -> String? {
        var q = query(provider)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func setApiKey(_ key: String?, for provider: String) {
        SecItemDelete(query(provider) as CFDictionary)
        guard let key, !key.isEmpty, let data = key.data(using: .utf8) else { return }
        var q = query(provider)
        q[kSecValueData as String] = data
        q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(q as CFDictionary, nil)
    }
}

/// Providers the iOS settings screen offers (same ids as Android).
enum Providers {
    static let all: [(id: String, name: String)] = [
        ("anthropic", "Anthropic"), ("openai", "OpenAI"), ("groq", "Groq"),
        ("openrouter", "OpenRouter"), ("ollama_cloud", "Ollama Cloud"),
    ]
}
