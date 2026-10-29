import SwiftUI

private func rgb(_ hex: UInt32) -> Color {
    Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
}

/// Typosaurus palette, shared by the app and the keyboard, matching Android. Follows the phone's appearance.
/// Light "minimalist": cream app / off-white keyboard base, white keys, slate-blue action, deep-charcoal text.
/// Dark "stealth": charcoal base, dark-slate keys, neon-teal action, off-white text.
struct Palette {
    /// `background` sits under the keyboard; `appBackground` is the app's own (cream in light mode, like the logo).
    let background, appBackground, surface, key, panel, text, muted, accent, onAccent: Color

    static func of(_ scheme: ColorScheme) -> Palette {
        scheme == .dark
            ? Palette(background: rgb(0x121212), appBackground: rgb(0x121212), surface: rgb(0x1E1E1E), key: rgb(0x2A2E33),
                      panel: rgb(0x1E1E1E), text: rgb(0xE0E0E0), muted: rgb(0xA8AFB8), accent: rgb(0x00E676), onAccent: rgb(0x121212))
            : Palette(background: rgb(0xF5F7FA), appBackground: rgb(0xF1EDE5), surface: .white, key: .white,
                      panel: rgb(0xE9EEF4), text: rgb(0x2C3E50), muted: rgb(0x5D6D7E), accent: rgb(0x4A90E2), onAccent: .white)
    }
}

typealias KeyPalette = Palette

extension Font {
    /// Inter, the typeface of the Typosaurus wordmark (bundled variable font; iOS picks the
    /// Display optical size automatically for large text).
    static func inter(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .custom("Inter", size: size).weight(weight)
    }
}

/// Filled pill button in the accent colour.
struct AccentButtonStyle: ButtonStyle {
    let color: Color
    let textColor: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.inter(16, .semibold))
            .foregroundColor(textColor)
            .padding(.horizontal, 20).padding(.vertical, 12)
            .background(color.opacity(configuration.isPressed ? 0.75 : 1))
            .clipShape(Capsule())
    }
}
