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

## Privacy Policy

**Effective Date:** May 18, 2026

"Third Ear" is an offline-first transcription application designed to respect user privacy.

**Data Collection and Processing**
Third Ear requires access to your device's microphone (`RECORD_AUDIO` permission) strictly for the purpose of transcribing speech to text.

* **100% Local Processing:** All audio processing and speech recognition are performed entirely locally on your device using offline acoustic models.
* **No Data Transmission:** We do not collect, store, upload, or transmit any of your audio recordings, voice data, or generated transcripts to external servers or third parties.
* **Storage:** Transcripts are saved locally in your device's internal app storage for your convenience. You have full control over this data and can delete sessions directly within the app.

By using Third Ear, you maintain complete ownership and privacy over your voice data and conversations.

## Open Source Licenses

Third Ear is built using powerful open-source technologies. We are grateful to the developers and communities who make these tools available.

### Vosk API and Models

This application utilizes the [Vosk Speech Recognition API](https://alphacephei.com/vosk/) and its associated acoustic models for offline speech-to-text capabilities.

* **Copyright:** 2020 Alpha Cephei Inc.
* **License:** Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. You may obtain a copy of the License at:
  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
