# OneShot Android

[简体中文](README_CN.md) | **English**

## Features

- **Local File Encryption**: Select files and encrypt them into `.oneshot` files.
- **Password Protection**: Protect files with password-based encryption and decryption.
- **Text Preview**: View UTF-8 text directly within the app.
- **Image Preview**: View images in the app with automatic downscaling to reduce memory usage.
- **File Expiration**: Supports 1 hour, 24 hours, 7 days, and no expiration.
- **One-Time Use**: Records a file fingerprint after successful decryption to prevent the same file from being opened again on this device.
- **Automatic Cleanup**: Removes temporary plaintext files when leaving the preview, when the app goes to the background, and on the next launch.
- **Privacy Protection**: Uses Android `FLAG_SECURE` to block screenshots and protect the app preview in the recent-apps screen.
- **Offline Operation**: No account or network connection required.

## Encryption Scheme

| Item | Scheme |
|---|---|
| Symmetric Encryption | AES-256-GCM |
| Key Derivation | PBKDF2-HMAC-SHA256 |
| Iterations | 310,000 |
| Key Length | 256 bits |
| Salt | Randomly generated, 16 bytes |
| IV | Randomly generated, 12 bytes |
| GCM Authentication Tag | 128 bits |

The `ONESHOT2` file format authenticates header metadata, including expiration information, using GCM AAD.

Decryption compatibility with the older `ONESHOT1` format is retained.

## File Expiration & One-Time Use

- The expiration period starts when file encryption is completed.
- Files are checked for expiration before decryption.
- A local usage record is saved only after successful decryption.
- SHA-256 fingerprints identify the same file even after it has been renamed or moved.

## Compatibility

- Minimum Android version: Android 8.0 (API 26).
- Text preview limit: 2 MB.
- Images are automatically downscaled for preview.
- Any file type can be encrypted, but not all file types can be previewed within the app.
