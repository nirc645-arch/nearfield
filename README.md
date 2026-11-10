# Nearfield Android app

Nearfield helps an owner write Wi-Fi details to a writable NFC tag in the stand. Customers tap the stand with a compatible phone to connect. The customer does not need this app.

## Download and install the APK

1. Open the [latest Nearfield APK release](https://github.com/nirc645-arch/nearfield/releases/download/android-latest/app-debug.apk) on an Android phone.
2. Download `app-debug.apk`.
3. Open the downloaded file.
4. If Android asks, allow your browser to install apps from this source, then return and tap **Install**.
5. Open Nearfield Setup, enter the Wi-Fi name and password, tap **Write to NFC stand**, and hold the phone against the writable NFC tag.

The app currently asks for the Wi-Fi name and password. Android does not let ordinary apps read saved Wi-Fi passwords automatically. The app writes a WPA2-Personal Wi-Fi record. Test the tag with your own customer phones before using it in the product.

## Build it yourself

Open the `android` folder in Android Studio and choose **Build > Build APK(s)**. The APK appears at `android/app/build/outputs/apk/debug/app-debug.apk`.
