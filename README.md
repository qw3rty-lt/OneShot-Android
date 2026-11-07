# OneShot Android — Phase 1 prototype

Dark purple, Simplified Chinese Android prototype.

## Phase 1 features
- Pick any source file using Android Storage Access Framework.
- Encrypt into `.oneshot` using AES-256-GCM.
- Password-based key derivation with PBKDF2-HMAC-SHA256 (310,000 iterations).
- Verify password and decrypt to the app-private cache.
- No network permission and no external app handoff.

## Build
Open this folder in Android Studio (Giraffe or newer), allow Gradle sync, then build:
`app > Tasks > build > assembleDebug`

Or push to GitHub and run the included Actions workflow. Download the `OneShot-debug` artifact.

## Important limitations of this alpha
- This is an encryption/decryption proof of concept, not a finished secure messenger.
- It does not yet preview decrypted content.
- It does not yet persist a one-time-consumed marker, nor implement the final lifecycle policy.
- The temporary plaintext file is deleted when the Activity is destroyed or the user taps the cleanup button. Flash storage deletion is not guaranteed to be forensic erasure.
- A copied encrypted container can be opened again on another device. Strict cross-device one-time access requires an online key service or trusted hardware.
- Do not use this alpha for irreplaceable or highly sensitive data.
