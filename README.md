# My Billing — Invoices & Estimates (Android)

A Vyapar-style invoicing app for Android. It wraps a self-contained offline web app in
a native WebView, so all your invoices, estimates and saved parties are stored **privately
on the device** — no internet or account needed.

## Features
- Create **GST invoices** and **estimates** with line items (item, qty, rate, GST%)
- Auto CGST/SGST vs IGST split based on the customer's GST state code
- **Save a party once by GSTIN** → details auto-fill next time you enter that GSTIN
- Auto invoice/estimate numbering, amount-in-words, print / share
- Business profile (name, GSTIN, address, logo text, currency, default tax, terms)

## Get the APK (no build tools needed)
This repo builds the APK automatically with **GitHub Actions**:
1. Open the **Actions** tab → latest **Build APK** run → download the **MyBilling-APK** artifact, **or**
2. Open **Releases** → **latest** → download `MyBilling.apk`.

Then on your Android phone, open the APK and install (allow "Install unknown apps" if asked).

## Build locally (optional)
Requires Android Studio or the Android SDK:
```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

## Project layout
- `app/src/main/assets/index.html` — the entire app (edit this to change the UI/logic)
- `app/src/main/java/com/mybilling/app/MainActivity.java` — WebView host
- `.github/workflows/build-apk.yml` — cloud APK build
