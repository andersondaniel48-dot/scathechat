# Scathe Chat

A private, end-to-end encrypted group chat for a handful of people (built for four brothers). Native Android app, Kotlin + Jetpack Compose.

- **Encrypted on the phone.** Messages, names, photos, videos and files are encrypted with AES-256-GCM before they leave the phone. The server only ever stores ciphertext.
- **Rich links.** Paste a link and the sender's phone builds a preview (title, description, image) that travels *inside* the encrypted message. Nobody else's phone contacts the site.
- **Media.** Photos (auto-compressed, location data stripped), GIFs, videos and any file up to 100 MB. Each file gets its own random key.
- **Fast.** Live updates over a single streaming connection, local message cache (opens instantly, works offline), tiny inline thumbnails so photos appear before they finish downloading.
- Replies, emoji reactions, read receipts (✓✓ turns blue when everyone has seen it), online status, share-to-Scathe from any app, instant notifications.

## Download

**[Latest APK](https://github.com/andersondaniel48-dot/scathechat/releases/latest/download/scathe-chat.apk)** — open this link on the Android phone, download, tap the file and allow "install unknown apps" for your browser when asked. The app checks for new versions itself and shows a banner when one is out.

## One-time setup (only the person who creates the group)

Scathe Chat uses a free Firebase Realtime Database that **you** own as its relay. No Firebase SDK, no account inside the app — just the database URL.

1. Go to <https://console.firebase.google.com>, sign in with a Google account, **Create a project** (any name; turn Analytics off).
2. In the project: **Build → Realtime Database → Create database** → pick a location → **Start in locked mode**.
3. Open the **Rules** tab, replace everything with the contents of [`firebase-rules.json`](firebase-rules.json) (the app also has a **Copy the rules** button), and tap **Publish**.
4. On the **Data** tab, copy the database URL at the top (`https://<project>-default-rtdb.firebaseio.com` or `…firebasedatabase.app`).
5. In Scathe Chat: **Create the group** → paste the URL → your name → **Create group**. The invite QR appears.

## Joining (everyone else)

Install the APK → **Join with an invite** → **Scan invite QR** on the creator's phone (or paste the invite code) → enter your name → **Join**.

The invite code **is** the key. Anyone who has it can read the chat, so scan in person when you can, and don't post it anywhere public.

## How the encryption works

- The creator's phone generates a random 256-bit group secret. It's only ever stored on your phones (wrapped with the Android Keystore) and inside the invite QR.
- HKDF-SHA256 derives two things from it: a **room id** (the unguessable path in the database where your data lives) and the **message key**.
- Each message is sealed with AES-256-GCM (random nonce, room id as associated data). Profiles (names/colors) are encrypted the same way.
- Each photo/video/file gets a fresh random AES-256 key, is split into 512 KB chunks, and each chunk is sealed separately. The file key rides inside the encrypted message.
- The database rules only allow reading a room if you already know its 64-character id, and messages can't be edited or deleted once written.

What it doesn't do (yet): per-device signatures (any member could in theory impersonate another member — fine for brothers), forward secrecy, or disappearing messages. Message timestamps and sizes are visible to whoever runs the database (you).

## Free-tier limits

Firebase's free Spark plan gives 1 GB of storage and 10 GB of downloads per month. That's thousands of photos, but lots of long videos will eat into it. Photos are compressed to ~300–600 KB before sending.

## Building

Every push to `main` builds a signed release APK in GitHub Actions and publishes it as a release. To build locally: open the folder in Android Studio, or run `./gradlew assembleRelease`.
