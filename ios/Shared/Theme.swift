import SwiftUI

/// Typosaurus brand palette and type, matching the Android app.
enum Brand {
    static let lime = Color(red: 0xC6 / 255, green: 1, blue: 0x3D / 255)
    static let ink = Color(red: 0x05 / 255, green: 0x05 / 255, blue: 0x05 / 255)
    static let surface = Color(red: 0x12 / 255, green: 0x15 / 255, blue: 0x11 / 255)
    static let surfaceHigh = Color(red: 0x1D / 255, green: 0x23 / 255, blue: 0x1B / 255)
    static let dinoGreen = Color(red: 0x2F / 255, green: 0x4A / 255, blue: 0x22 / 255)
    static let text = Color(red: 0xF2 / 255, green: 0xF5 / 255, blue: 0xEE / 255)
    static let muted = Color(red: 0xB9 / 255, green: 0xC2 / 255, blue: 0xB1 / 255)
}

extension Font {
    /// Inter, the typeface of the Typosaurus wordmark (bundled variable font).
    static func inter(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .custom("Inter", size: size).weight(weight)
    }
}

/// Lime pill button, as on the launch artwork.
struct LimeButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.inter(16, .semibold))
            .foregroundColor(Brand.ink)
            .padding(.horizontal, 20).padding(.vertical, 12)
            .background(Brand.lime.opacity(configuration.isPressed ? 0.75 : 1))
            .clipShape(Capsule())
    }
}
