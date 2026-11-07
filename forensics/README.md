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

## Limitations

- **Android arm64 only** (Pixel 8/9 and most newer devices). Not a universal APK for older 32-bit Android phones.
- The source chat4n6 engine currently knows WhatsApp, Signal and Telegram databases. Snapchat, TikTok, Facebook and Instagram are **import/search only**, not deleted-message recovery.
- Android cannot access the private app data of WhatsApp, Signal, Telegram or other apps without a separately authorized extraction; this APK does **not** do that.
- Encrypted WhatsApp requires the original key; Signal must be plaintext. Deleted data that has been overwritten cannot be recovered.
- Imported .json/.csv/.html files are searchable by lines up to 5MB each and up to 100 matches. Larger text exports remain safely stored but are not keyword-indexed.
- chat4n6 produces investigatory findings with uncertainty; recovered messages should be independently validated.

## Security/privacy

No internet permission, automatic scraping, or hidden scanning. User imports remain on-device in private app storage; Android automatic backup is disabled. Reports can be exported only through an explicit action. Uninstalling the app removes stored case files and reports unless separately exported.
