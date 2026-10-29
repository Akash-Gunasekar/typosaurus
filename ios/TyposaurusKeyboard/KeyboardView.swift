import SwiftUI
import TyposaurusCore

/// Toolbar (three lime AI buttons + ⋯) above a QWERTY layout, or the preview while a result streams in.
struct KeyboardView: View {
    @ObservedObject var model: KeyboardModel
    @Environment(\.colorScheme) private var scheme
    private var p: KeyPalette { .of(scheme) }

    private let letters = [Array("qwertyuiop"), Array("asdfghjkl"), Array("zxcvbnm")].map { $0.map(String.init) }
    private let numbers = [Array("1234567890"), Array("-/:;()$&@\""), Array(".,?!'")].map { $0.map(String.init) }

    var body: some View {
        VStack(spacing: 6) {
            topRow.frame(height: 44)
            if model.menuOpen {
                menu
            } else if case .preview = model.phase {
                previewBody
            } else if case .streaming = model.phase {
                previewBody
            } else {
                keys
            }
        }
        .padding(.horizontal, 4).padding(.vertical, 6)
        .background(p.background)
    }

    // MARK: top row

    @ViewBuilder private var topRow: some View {
        switch model.phase {
        case let .streaming(title, _), let .preview(title, _, _):
            HStack {
                Text(title).font(.inter(15, .semibold)).foregroundColor(p.text)
                Spacer()
                if case .streaming = model.phase { ProgressView().tint(p.accent) }
            }.padding(.horizontal, 10)
        case let .failed(message, _):
            HStack {
                Text(message).font(.inter(13)).foregroundColor(.red).lineLimit(2)
                Spacer()
                Button("Dismiss") { model.cancel() }.font(.inter(13, .semibold)).foregroundColor(p.accent)
            }.padding(.horizontal, 10)
        case .applied:
            HStack {
                Text("Applied ✓").font(.inter(15, .semibold)).foregroundColor(p.text)
                Spacer()
                Button("Undo") { model.undo() }.buttonStyle(AccentButtonStyle(color: p.accent, textColor: p.onAccent))
            }.padding(.horizontal, 10)
        case .idle:
            HStack(spacing: 6) {
                ForEach(model.toolbarActions, id: \.id) { action in
                    Button { model.run(action) } label: {
                        Text(action.icon).font(.system(size: 18))
                            .frame(width: 40, height: 40)
                            .background(p.accent)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                }
                Spacer()
                Button { model.menuOpen.toggle() } label: {
                    Image(systemName: "ellipsis").font(.system(size: 18, weight: .semibold))
                        .foregroundColor(p.text).frame(width: 44, height: 40)
                }
            }
        }
    }

    // MARK: preview

    private var previewBody: some View {
        VStack(alignment: .leading, spacing: 10) {
            ScrollView {
                Text(previewText).font(.inter(16)).foregroundColor(p.text)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(maxHeight: .infinity)
            .padding(10).background(p.panel).clipShape(RoundedRectangle(cornerRadius: 12))
            HStack(spacing: 8) {
                if case .preview = model.phase {
                    Button("Accept") { model.accept() }.buttonStyle(AccentButtonStyle(color: p.accent, textColor: p.onAccent))
                    Button("Retry") { model.retry() }.font(.inter(15, .semibold)).foregroundColor(p.text)
                    Spacer()
                    Button { model.copyResult() } label: { Image(systemName: "doc.on.doc") }.foregroundColor(p.text)
                } else {
                    Spacer()
                }
                Button { model.cancel() } label: { Image(systemName: "xmark") }.foregroundColor(p.text).padding(.horizontal, 8)
            }
        }
        .padding(.horizontal, 6)
    }

    private var previewText: String {
        switch model.phase {
        case let .streaming(_, partial): return partial.isEmpty ? "…" : partial
        case let .preview(_, result, truncated): return truncated ? result + "\n\n(cut off by the token limit)" : result
        default: return ""
        }
    }

    // MARK: ⋯ menu

    private var menu: some View {
        ScrollView {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 4), spacing: 6) {
                ForEach(model.allActions, id: \.id) { action in
                    Button { model.run(action) } label: {
                        VStack(spacing: 4) {
                            Text(action.icon).font(.system(size: 22))
                            Text(action.name).font(.inter(12, .medium)).foregroundColor(p.text).lineLimit(1)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(p.panel).clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
            }
        }
    }

    // MARK: keys

    private var keys: some View {
        let rows = model.symbols ? numbers : letters
        return VStack(spacing: 8) {
            ForEach(0..<3, id: \.self) { r in
                HStack(spacing: 5) {
                    if r == 2 {
                        if model.symbols {
                            Spacer(minLength: 0)
                        } else {
                            key(model.capsLock ? "capslock.fill" : (model.shifted ? "shift.fill" : "shift"), system: true, width: 46) { model.toggleShift() }
                        }
                    }
                    ForEach(rows[r], id: \.self) { k in
                        key(model.symbols ? k : (model.shifted || model.capsLock ? k.uppercased() : k)) { model.type(k) }
                    }
                    if r == 2 { key("delete.left", system: true, width: 46) { model.backspace() } }
                }
            }
            HStack(spacing: 5) {
                key(model.symbols ? "ABC" : "123", width: 52) { model.symbols.toggle() }
                if model.showGlobe { key("globe", system: true, width: 44) { model.nextKeyboard() } }
                key("space") { model.space() }
                key("return", width: 76, highlighted: true) { model.newline() }
            }
        }
    }

    /// A key; with [width] it is fixed, otherwise it shares the row evenly with the other flexible keys.
    private func key(_ label: String, system: Bool = false, width: CGFloat? = nil, highlighted: Bool = false,
                     action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Group {
                if system { Image(systemName: label).font(.system(size: 17, weight: .medium)) }
                else { Text(label).font(.inter(label.count > 1 ? 15 : 22, label.count > 1 ? .medium : .regular)) }
            }
            .foregroundColor(highlighted ? p.onAccent : p.text)
            .frame(maxWidth: .infinity, minHeight: 44)
            .background(highlighted ? p.accent : p.key)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .shadow(color: .black.opacity(0.12), radius: 0.5, x: 0, y: 1) // subtle key depth
        }
        .frame(width: width)
        .frame(maxWidth: width == nil ? .infinity : nil)
    }
}
