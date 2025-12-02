# Spark - Tap, Share, Grow

**CMPT 362 Project: Fall 2025 - Group 40**

Spark is an Android app that lets you instantly exchange professional contact information through
NFC tapping, QR codes. No more messing with business cards or typing details
into your phone, just tap your phone and go!

## What It Does

Connect with people instantly by tapping phones together (NFC) or scanning QR codes as a fallback.
Your contacts are automatically organized and synced to the cloud.

## Key Features

- **NFC Tap-to-Share** - Hold phones back-to-back to exchange profiles
- **QR Code Scanning** - Generate and scan QR codes for your profile
- **Proximity Detection** - Automatically discover nearby users via location
- **Profile Management** - Customizable profiles with social links and bio
- **Connection Dashboard** - View and manage all your connections
- **Map View** - See where you met your connections
- **Smart Reminders** - Get follow-up notifications for recent connections
- **Cloud Sync** - All data backed up to Firebase

## Notes for Testing

Unfortunately, you cannot test all features on emulators. Here are some tips:

- You will need 2 NFC-enabled Android devices to test the app. One device will act as the sender and
  the other as the receiver.
- If the devices do not support NFC, you can still use QR code scanning to share profiles.
- You can use test accounts for Firebase Authentication. Make sure to sign out before switching
  accounts.

## Tech Stack

- **Kotlin** + Jetpack Compose
- **Firebase** (Authentication, Firestore, Cloud Storage)
- **Google Maps API**
- **NFC** (Host Card Emulation + Reader Mode)
- **Material Design 3**

## Build

1. Clone the repository
2. Add `google-services.json` to `app/` from our Firebase project. A template is provided in
   `app/google-services.json.template`.
3. Build and run on Android device with NFC

## Requirements

- Android 8.0+ (API 26+)
- 2 NFC-enabled device (for tap-to-share)
- Location permission (for proximity detection)
- Internet connection (for cloud sync)

## Group Members

- Mekdim Dereje
- Alvyn Kang
- Arnell Kang

