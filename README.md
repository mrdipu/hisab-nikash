# হিসাবনিকাশ (HisabNikash)

**Powered by DZ Ai-STUDIO**

An offline-first, bilingual (বাংলা & English) personal finance manager for Bangladesh (BDT ৳) with multi-account ledgers, transfers, monthly budget threshold alerts, earmarked savings goals, recurring bill rules, UTF-8 CSV & Bangla PDF reports, biometric app lock, and AES-256-GCM encrypted local & optional Google Drive backups.

---

## Key Architecture & Guarantees
- **Offline-First**: Core accounting, transactions, transfers, budgets, savings goals, recurring schedules, analytics, CSV/PDF reports, and encrypted `.hnkb` backups run 100% locally with zero login wall, zero ads, and zero paid APIs.
- **Exact Money Math**: All amounts are stored as signed `Long` **poisha** (`100 poisha = ৳1`) parsed via `BigDecimal` (`MoneyParser`) and formatted with South Asian grouping (`MoneyFormatter`).
- **Ledger Integrity**: Account balances are SQL-derived from `openingBalance + income - expenses + incomingTransfers - outgoingTransfers + balanceAdjustments` excluding soft-deleted tombstones (`deletedAt IS NULL`).
- **No False Savings Accounting**: Goal allocations earmark funds already held in an account, reducing only *Available (Unreserved)* balance while leaving real ledger balance and cash flow untouched.
- **Portable Encrypted Backups (`.hnkb`)**: `HNKB` v1 binary envelope using `PBKDF2WithHmacSHA256` (120,000 iterations), fresh 16-byte CSPRNG salt, fresh 12-byte GCM nonce, GZIP compression, and `AES-256-GCM` with authenticated header AAD.

---

## Optional Google Drive Backup (`Hisab Nikash Backups`) — Developer OAuth Setup Guide
Google Drive backup is optional and isolated in `cloud/drive/`. Because Google Play Services OAuth requires your own Google Cloud Project and Android signing SHA-1 certificate, follow these developer-only steps before testing Drive backup on a physical device:

1. **Create a Google Cloud Project** in [Google Cloud Console](https://console.cloud.google.com/).
2. **Enable the Google Drive API**:
   - Navigate to **APIs & Services → Library** → search **Google Drive API** → click **Enable**.
3. **Configure Google Auth Platform / OAuth Consent Screen**:
   - Go to **Google Auth Platform → Branding / Audience**.
   - Add app name **হিসাবনিকাশ** and support email.
   - Under **Data Access / Scopes**, add `https://www.googleapis.com/auth/drive.file`.
   - While in **Testing** publishing status, add your Google account(s) under **Test users** (unverified testing tokens expire after 7 days; publish to **Production** and complete Google verification for general distribution).
4. **Create Android OAuth 2.0 Client ID** (fixes `8: [8] unknown error [status=UNREGISTERED_ON_API_CONSOLE]`):
   - Go to **Google Auth Platform → Clients → Create Client → Android**.
   - Package name: `com.dzaistudio.hisabnikash`
   - Debug SHA-1 certificate fingerprint (`debug.keystore`): `CC:E2:B6:19:C0:F7:1F:EC:30:2B:15:BE:23:D2:93:79:EF:CA:84:BE` (or run `keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android`; for release builds use your release / Play App Signing SHA-1).
5. **Test on a Physical Android Device with Google Play Services**:
   - Open **Settings → Google Account & Drive Backup → Authorize Google Drive**.
   - Encrypted `.hnkb` backups are stored in a visible **`Hisab Nikash Backups`** folder in the user's root Google Drive so users can also download them directly from Google Drive. Keep manual `.hnkb` exports as an independent offline recovery path.

---

## Exporting ZIP, Building in Android Studio & Installing on Android 10 (e.g. Samsung Galaxy M20)
1. **In AI Studio**:
   - **WebUSB ("Install on Device")**: Use **Google Chrome** or **Microsoft Edge** (Firefox does not support WebUSB), enable **Developer Options → USB Debugging** on your Android 10 phone, accept the RSA prompt, and trigger installation.
   - **Android Studio ZIP Export**: Use the AI Studio export menu to download the project ZIP.
2. **In Android Studio**:
   - Extract the ZIP and choose **File → Open** on the extracted folder.
   - Wait for Gradle sync, then run `./gradlew assembleDebug` (or `gradle assembleDebug` in terminal).
   - Output APK location: `app/build/outputs/apk/debug/app-debug.apk`.
3. **Sideloading on Android 10**:
   - Transfer `app-debug.apk` to the phone or install via `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
