import SwiftUI
import TyposaurusCore
import UIKit

/// Connects the iOS text proxy to the shared Kotlin `Assistant` (same prompts, routing and limits as Android).
final class KeyboardModel: NSObject, ObservableObject, AssistantListener {
    enum Phase: Equatable {
        case idle
        case streaming(title: String, partial: String)
        case preview(title: String, result: String, truncated: Bool)
        case failed(message: String, openSettings: Bool)
        case applied
    }

    @Published var phase: Phase = .idle
    @Published var shifted = true
    @Published var capsLock = false
    @Published var symbols = false
    @Published var menuOpen = false
    @Published var showGlobe = false

    weak var controller: KeyboardViewController?

    private lazy var assistant: Assistant = IosEntryKt.createAssistant(
        settingsJson: SharedStore.settingsJson, actionsJson: nil,
        keyLookup: { SharedStore.apiKey(for: $0) }
    )
    /// What was sent, and whether it came from a selection (selection replace) or the text before the cursor.
    private var captured: (text: String, fromSelection: Bool)?
    private var lastApplied: (original: String, result: String)?
    private var currentTitle = ""

    private var proxy: UITextDocumentProxy? { controller?.textDocumentProxy }

    var toolbarActions: [Action] { Array(assistant.toolbarActions.prefix(3)) }
    var allActions: [Action] { assistant.toolbarActions }

    func reload() { assistant.update(settingsJson: SharedStore.settingsJson, actionsJson: nil) }

    // MARK: typing

    func type(_ s: String) {
        proxy?.insertText(shifted || capsLock ? s.uppercased() : s)
        if shifted && !capsLock { shifted = false }
        if phase == .applied { phase = .idle }
    }

    func space() {
        proxy?.insertText(" ")
        checkInlineTrigger()
    }

    func backspace() { proxy?.deleteBackward() }
    func newline() { proxy?.insertText("\n") }
    func nextKeyboard() { controller?.advanceToNextInputMode() }

    func toggleShift() {
        if capsLock { capsLock = false; shifted = false } else if shifted { capsLock = true } else { shifted = true }
    }

    func updateAutoShift() {
        guard !capsLock, !symbols else { return }
        let before = proxy?.documentContextBeforeInput ?? ""
        let trimmed = before.trimmingCharacters(in: .whitespaces)
        shifted = trimmed.isEmpty || trimmed.hasSuffix(".") || trimmed.hasSuffix("!") || trimmed.hasSuffix("?")
    }

    // MARK: AI actions

    func run(_ action: Action) {
        menuOpen = false
        guard let proxy else { return }
        if let selected = proxy.selectedText, !selected.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            start(action, text: selected, fromSelection: true, arg: nil)
        } else if let before = proxy.documentContextBeforeInput, !before.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            start(action, text: before, fromSelection: false, arg: nil)
        } else {
            phase = .failed(message: "No text to process", openSettings: false)
        }
    }

    /// "text /fix␣": delete the trigger token, then run on the text before it.
    private func checkInlineTrigger() {
        guard phase == .idle || phase == .applied,
              let before = proxy?.documentContextBeforeInput,
              let match = assistant.matchTrigger(beforeCursor: before),
              let action = assistant.actionForKeyword(keyword: match.keyword) else { return }
        for _ in 0..<Int(match.tokenLength) { proxy?.deleteBackward() }
        start(action, text: match.body, fromSelection: false, arg: match.arg)
    }

    private func start(_ action: Action, text: String, fromSelection: Bool, arg: String?) {
        guard controller?.hasFullAccess == true else {
            phase = .failed(message: "Turn on Allow Full Access for Typosaurus in Settings › Keyboards", openSettings: true)
            return
        }
        captured = (text, fromSelection)
        currentTitle = "\(action.icon) \(action.name)"
        phase = .streaming(title: currentTitle, partial: "")
        assistant.run(action: action, text: text, arg: arg, listener: self)
    }

    func accept() {
        guard case let .preview(_, result, _) = phase, let cap = captured, let proxy else { return }
        if cap.fromSelection {
            proxy.insertText(result) // replaces the selection
        } else if proxy.documentContextBeforeInput?.hasSuffix(cap.text) == true {
            for _ in 0..<cap.text.count { proxy.deleteBackward() }
            proxy.insertText(result)
        } else {
            UIPasteboard.general.string = result
            phase = .failed(message: "Text changed — result copied to the clipboard", openSettings: false)
            return
        }
        lastApplied = (cap.text, result)
        phase = .applied
    }

    func undo() {
        guard let applied = lastApplied, let proxy else { phase = .idle; return }
        if proxy.documentContextBeforeInput?.hasSuffix(applied.result) == true {
            for _ in 0..<applied.result.count { proxy.deleteBackward() }
            proxy.insertText(applied.original)
        }
        lastApplied = nil
        phase = .idle
    }

    func copyResult() {
        if case let .preview(_, result, _) = phase { UIPasteboard.general.string = result }
    }

    func retry() {
        phase = .streaming(title: currentTitle, partial: "")
        assistant.retry()
    }

    func cancel() {
        assistant.cancel()
        phase = .idle
    }

    // MARK: AssistantListener (main queue)

    func onPartial(text: String) {
        if case .streaming = phase { phase = .streaming(title: currentTitle, partial: text) }
    }

    func onResult(text: String, truncated: Bool) {
        phase = .preview(title: currentTitle, result: text, truncated: truncated)
    }

    func onError(message: String, openSettings: Bool) {
        phase = .failed(message: message, openSettings: openSettings)
    }
}
