# Kays Forensics (Kay's Chat Forensics) — Android

Free offline Android forensic evidence app that bundles the **actual SecurityRonin/chat4n6 Rust CLI**, compiled for **arm64-v8a Android**, into the APK with GitHub Actions.

Original engine: https://github.com/SecurityRonin/chat4n6, MIT licensed. See `THIRD_PARTY_LICENSE.txt`.

## Capabilities

- Import user-selected files without asking for storage or network permissions.
- Preserve an original private copy of each import, with SHA-256, size and import timestamp.
- Organize compatible WhatsApp `msgstore.db`, `wa.db`, encrypted `.crypt14` and `.crypt15`, WhatsApp `key`, plaintext Signal `signal.sqlite`, and Telegram `cache.db` / `cache4.db` files into the folder paths recognized by chat4n6.
- Run chat4n6 **locally** over the imported database files, generate HTML/JSON reports and view them offline. The Android UI uses `--no-unalloc` because raw unallocated disk images are not imported.
- Search imported text exports (TXT, JSON, CSV, HTML) by keyword for Snapchat, TikTok, Facebook, Instagram and all other imported platforms. Generic keyword search is not equivalent to platform-specific forensic recovery.
- Export a report ZIP via the Android document picker; no paid service.

## Build

Open GitHub Actions > `Build Kays Forensics Android APK` on branch `kays-chat-forensics`. The workflow downloads and builds the upstream Rust engine with the Android NDK, bundles it into the app and uploads the `Kays-Forensics-Android-APK` artifact. Extract the ZIP and install the APK. Rust build adds significant build time compared with the Java-only prototype.

## Installation repair (v0.2.2)

Version 0.2.2 uses a **new Android application ID**, `com.kay.forensics.standalone`. The earlier experimental APKs were individually signed with ephemeral GitHub runner debug certificates, so updates to an already installed older version can fail with `App not installed` because the signing certificates differ. With this new ID, the new version installs separately without removing the earlier app or its evidence. Do **not** uninstall an earlier app before exporting its evidence. If this build still fails to install, record the precise error (ADB `INSTALL_FAILED_*` if available) and check Android CPU architecture and installation source permissions.

Important for **future updates**: GitHub Actions uses a different temporary debug signing key for each run. APKs cannot reliably replace an installed version until a **persistent, securely stored signing key** is configured with GitHub Actions Secrets. Never publicly commit a production or stable private signing key.

## Limitations

- **Android arm64 only** (Pixel 8/9 and most newer devices). Not a universal APK for older 32-bit Android phones.
- The source chat4n6 engine currently knows WhatsApp, Signal and Telegram databases. Snapchat, TikTok, Facebook and Instagram are **import/search only**, not deleted-message recovery.
- Android cannot access the private app data of WhatsApp, Signal, Telegram or other apps without a separately authorized extraction; this APK does **not** do that.
- Encrypted WhatsApp requires the original key; Signal must be plaintext. Deleted data that has been overwritten cannot be recovered.
- Imported .json/.csv/.html files are searchable by lines up to 5MB each and up to 100 matches. Larger text exports remain safely stored but are not keyword-indexed.
- chat4n6 produces investigatory findings with uncertainty; recovered messages should be independently validated.

## Third-party licenses and attribution

The APK includes SecurityRonin/chat4n6 under the MIT License. The **original copyright notice and full unmodified MIT License text** are packaged at `assets/chat4n6-LICENSE.txt` and can be viewed from the app's **THIRD-PARTY LICENSE & COPYRIGHT** button. The GitHub Actions downloadable artifact contains `THIRD_PARTY_LICENSE.txt` alongside the APK. The workflow verifies the embedded license matches the file shipped in the artifact.

The canonical upstream copyright and license are in `forensics/THIRD_PARTY_LICENSE.txt`, mirrored from https://github.com/SecurityRonin/chat4n6/blob/main/LICENSE.

## Security/privacy

No internet permission, automatic scraping, or hidden scanning. User imports remain on-device in private app storage; Android automatic backup is disabled. Reports can be exported only through an explicit action. Uninstalling the app removes stored case files and reports unless separately exported.
