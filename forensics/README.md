# Kay's Chat Forensics — Android MVP

Free, offline evidence-file viewer and case organizer. Inspired by SecurityRonin/chat4n6 (https://github.com/SecurityRonin/chat4n6); **does not yet embed the chat4n6 Rust recovery engine**.

Imports user-selected plaintext CSV, JSON, TXT, HTML, or SQLite files into app-private storage, calculates SHA-256, timestamps each import, and lists saved cases. No automatic extraction from other apps, decryption, or deleted-message recovery is implemented. The platform labels are organizational only; they do not imply parsing support. Use only evidence you are authorized to examine.

GitHub Actions builds a debug APK on the `kays-chat-forensics` branch. Download the artifact from the workflow run. No paid services or API keys required.
