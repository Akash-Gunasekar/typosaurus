# Typosaurus for iOS

iPhones only install apps from the App Store or TestFlight, so there is no file to download here yet.
Until Typosaurus is published there, you can build it yourself on a Mac:

1. Install Xcode 16+ and XcodeGen: `brew install xcodegen`.
2. Clone this repository, then:
   ```bash
   cd ios && xcodegen && open Typosaurus.xcodeproj
   ```
3. Select your Apple ID team under **Signing & Capabilities** for both targets (`Typosaurus` and `TyposaurusKeyboard`), connect your iPhone, and press **Run**.
4. On the iPhone: **Settings › General › Keyboard › Keyboards › Add New Keyboard › Typosaurus**, tap it and turn on **Allow Full Access** (needed to reach your AI provider).
5. Open the Typosaurus app to accept the privacy notice and add your API key.

A free Apple ID lets you run it on your own phone for 7 days at a time; an Apple Developer account ($99/year) removes that limit and allows TestFlight and the App Store.
