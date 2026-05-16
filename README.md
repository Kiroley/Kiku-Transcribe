# Third Ear

**Third Ear** is a powerful, offline-first speech-to-text transcription application for Android. Built with Jetpack Compose and powered by the Vosk voice recognition engine, it provides real-time, privacy-focused transcription that works entirely on-device without requiring an internet connection.

## 🌟 What it's used for

- **Accessibility**: Real-time captions for the hearing impaired.
- **Note-Taking**: Dictate thoughts, meeting minutes, or lectures hands-free.
- **Journaling**: Capture personal reflections quickly through speech.
- **Privacy-Sensitive Tasks**: Transcribe confidential conversations without data leaving the device.

## 🚀 Key Features

### 🎙️ Advanced Transcription
- **Offline Recognition**: High-accuracy transcription using the Vosk engine. No data usage or privacy concerns.
- **Live Feedback**: See "partial" results as you speak, which transition into final text automatically.
- **Magnetic Auto-Scroll**: The view intelligently follows the latest text floor as it's generated, but stays put if you scroll up to read history.

### 📁 Session Management
- **Persistence**: Sessions are automatically saved locally.
- **Auto-Load**: The app remembers your last active session and loads it instantly on launch.
- **Naming & Organization**: Generate timestamped session names or rename them to suit your needs.
- **Quick Deletion**: Easily manage your history with dedicated trash icons and confirmation dialogs.

### 👆 Fluid Navigation
- **Dual Swipe Gestures**: 
  - **Swipe Right**: Instantly open the "Saved Sessions" menu from anywhere on the transcription screen.
  - **Swipe Left**: Quickly access the "Settings" menu.
- **Custom Toolbar**: A unique curved notch design that keeps essential controls within thumb-reach.

### ⚙️ Customization & Export
- **Dynamic Text Scaling**: Change font size on the fly via the settings menu.
- **Legibility**: Proportional line-height ensures text remains readable at any size.
- **Easy Sharing**: Export your transcriptions to other apps (Email, Notes, Messaging) with a single tap.

## 📖 How to Use

1.  **Start Transcribing**: Tap the large microphone button in the center notch. The button will turn **Black** with a **White Stop icon** to indicate active recording.
2.  **Stop/Pause**: Tap the black button again to stop. It will turn **Red**, indicating that the session has content but is not currently recording.
3.  **Create New Session**: Tap the **"+"** icon on the right side of the toolbar. This resets the state and starts a fresh file (button turns **Grey**).
4.  **Manage Sessions**:
    - Tap the **Menu** icon (left) or **Swipe Right** to see your history.
    - Click a session to load it, or tap the **Trash icon** to delete it.
    - Click the session name at the bottom center to **Rename** it.
5.  **Adjust Settings**: Tap the **Gear icon** (left) or **Swipe Left** to adjust the text size.
6.  **Export**: Tap the **Share icon** (right) to send your text to another application.

## 🛠️ Technical Details
- **UI Framework**: Jetpack Compose
- **Engine**: [Vosk Android SDK](https://alphacephei.com/vosk/install)
- **Language**: Kotlin
- **Architecture**: Modern Android architecture with `MutableState` and `LaunchedEffect` for reactive UI updates.

---

*Note: On first launch, the app will request Microphone permissions and may take a moment to unpack the voice model. Ensure you have granted the RECORD_AUDIO permission.*
