import SwiftUI

@main
struct TyposaurusApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(.dark)
                .tint(Brand.lime)
        }
    }
}

/// Setup and settings: enable the keyboard, accept the privacy notice, add a key, try it.
struct ContentView: View {
    @State private var settings = SharedStore.settings
    @State private var keyDraft = ""
    @State private var hasKey = false
    @State private var sample = ""

    private var model: Binding<String> {
        Binding(
            get: { settings.defaultModels[settings.defaultProviderId] ?? "" },
            set: { settings.defaultModels[settings.defaultProviderId] = $0.trimmingCharacters(in: .whitespaces) }
        )
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    card("1. Turn on the keyboard") {
                        Text("Settings › General › Keyboard › Keyboards › Add New Keyboard › Typosaurus, then tap it and turn on **Allow Full Access** so it can reach your AI provider.")
                            .font(.inter(14)).foregroundColor(Brand.muted)
                        Button("Open Settings") {
                            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                        }.buttonStyle(LimeButtonStyle())
                    }
                    card("2. Privacy notice") {
                        Text("Text is sent only when you tap an AI action or finish an inline shortcut, straight from your phone to the provider you choose over HTTPS. There is no Typosaurus server, and nothing is logged.")
                            .font(.inter(14)).foregroundColor(Brand.muted)
                        Toggle("I understand and agree", isOn: $settings.consentGiven).font(.inter(15, .medium))
                    }
                    card("3. AI provider") {
                        Picker("Provider", selection: $settings.defaultProviderId) {
                            ForEach(Providers.all, id: \.id) { Text($0.name).tag($0.id) }
                        }.pickerStyle(.menu)
                        SecureField(hasKey ? "Key saved — paste to replace" : "Paste your API key", text: $keyDraft)
                            .textContentType(.password).font(.inter(15))
                            .padding(12).background(Brand.surfaceHigh).clipShape(RoundedRectangle(cornerRadius: 12))
                        TextField("Model id (e.g. claude-sonnet-5)", text: model)
                            .autocorrectionDisabled().textInputAutocapitalization(.never).font(.inter(15))
                            .padding(12).background(Brand.surfaceHigh).clipShape(RoundedRectangle(cornerRadius: 12))
                        HStack {
                            Button("Save key") {
                                SharedStore.setApiKey(keyDraft, for: settings.defaultProviderId)
                                keyDraft = ""; refreshKey()
                            }.buttonStyle(LimeButtonStyle()).disabled(keyDraft.isEmpty)
                            if hasKey {
                                Button("Delete key", role: .destructive) {
                                    SharedStore.setApiKey(nil, for: settings.defaultProviderId); refreshKey()
                                }
                            }
                        }
                    }
                    card("4. Try it") {
                        Text("Switch to Typosaurus with 🌐, type “teh cat sat”, then tap ✨.")
                            .font(.inter(14)).foregroundColor(Brand.muted)
                        TextField("Type here", text: $sample, axis: .vertical).lineLimit(3...6).font(.inter(16))
                            .padding(12).background(Brand.surfaceHigh).clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
                .padding(16)
            }
            .background(Brand.ink.ignoresSafeArea())
            .navigationTitle("Typosaurus")
        }
        .onChange(of: settings) { SharedStore.settings = $0; refreshKey() }
        .onAppear(perform: refreshKey)
    }

    private var header: some View {
        HStack(spacing: 14) {
            Image("Dino").resizable().frame(width: 56, height: 56).clipShape(Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text("Typosaurus").font(.inter(26, .heavy)).foregroundColor(Brand.ink)
                Text("THE AI KEYBOARD THAT EATS TYPOS").font(.inter(10, .semibold)).kerning(1.6).foregroundColor(Brand.ink.opacity(0.75))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .background(LinearGradient(colors: [Brand.lime, Brand.lime.opacity(0.8)], startPoint: .topLeading, endPoint: .bottomTrailing))
        .clipShape(RoundedRectangle(cornerRadius: 24))
    }

    private func card<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.inter(18, .bold)).foregroundColor(Brand.text)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Brand.surface)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    private func refreshKey() { hasKey = SharedStore.apiKey(for: settings.defaultProviderId) != nil }
}
