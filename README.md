# Catcher

Catcher is a specialized Android utility designed to intercept, log, and rescue messages and media from popular messaging applications. By utilizing Android's Notification Listener Service and File System Observers, it ensures that you have a local backup of conversations and media, even if the sender deletes them.

## 🚀 Features

*   **Message Logging**: Automatically saves incoming notifications from supported messaging apps.
*   **Deleted Message Detection**: Heuristically detects "message deleted" notifications and flags the corresponding local entry.
*   **Media Rescue**: Monitors system folders to "rescue" incoming images (WhatsApp/WhatsApp Business) by copying them to a secure local directory before they can be deleted.
*   **Thread-Based UI**: A modern, clean interface built with Jetpack Compose to browse conversations by sender.
*   **Privacy Focused**: All data is stored locally on your device using a Room database. No data is sent to external servers.

## 📱 Supported Applications

*   WhatsApp
*   WhatsApp Business
*   Telegram
*   Telegram X (Challegram)

## 🛠️ Tech Stack

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose
*   **Database**: Room (SQLite)
*   **Concurrency**: Kotlin Coroutines & Flow
*   **Architecture**: MVVM
*   **Image Loading**: Coil
*   **System Integration**: `NotificationListenerService`, `FileObserver`, `MediaScannerConnection`

## ⚙️ Requirements & Permissions

To function correctly, Catcher requires the following permissions:

1.  **Notification Access**: Required to read incoming notifications from messaging apps.
2.  **Storage/Media Access**: 
    *   On Android 13+ (API 33): `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO`.
    *   On older versions: `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE`.
    *   Required to monitor and copy incoming media files.

## 🏗️ Build Instructions

1.  Clone the repository.
2.  Ensure you have a `local.properties` file in the root directory with your signing credentials:
    ```properties
    RELEASE_STORE_PASSWORD=your_password
    RELEASE_KEY_ALIAS=your_alias
    RELEASE_KEY_PASSWORD=your_key_password
    ```
3.  Place your release keystore (`my-release-key.p12`) in the `app/` directory.
4.  Build the project using Android Studio or via CLI:
    ```bash
    ./gradlew assembleRelease
    ```

## ⚠️ Disclaimer

This app is intended for personal backup and educational purposes only. Please respect the privacy of others and adhere to the Terms of Service of the messaging platforms you use. The developers are not responsible for any misuse of this application.

---
*Built with ❤️ using Jetpack Compose.*