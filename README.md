# Virtual Lab Admin (Android Studio, Java)

This folder contains a **native Android (Java)** implementation of your admin app that talks to the existing PHP API in `android_api/`.

Because this repo does not include an Android Gradle wrapper (`gradle-wrapper.jar`), the easiest way to run in Android Studio is:

1. **Android Studio → New Project**
   - Template: **Empty Views Activity**
   - Language: **Java**
   - Minimum SDK: **API 24** (Android 7.0) or higher
   - Package name: pick one (example used below): `com.virtuallab.admin`

2. **Copy files from this folder into your new project**
   - Copy everything inside `android_studio_java_admin/app/src/main/` into your project’s `app/src/main/`

3. **Add dependencies**
   In your Android Studio project’s `app/build.gradle`, add:
   - Retrofit + OkHttp
   - Gson
   - Material Components
   - RecyclerView + SwipeRefreshLayout

   Use the snippet in `android_studio_java_admin/gradle/app-build.gradle.snippet`.

   If you see errors like `package androidx.annotation does not exist`, make sure:
   - You pasted the dependencies snippet (it includes `androidx.annotation:annotation`)
   - Your `gradle.properties` has:
     - `android.useAndroidX=true`
     - `android.enableJetifier=true`
   - Then **Sync Gradle** again.

4. **Set API base URL**
   Edit `Config.API_BASE_URL` in:
   - `android_studio_java_admin/app/src/main/java/com/virtuallab/admin/Config.java`

   Examples:
   - Android Emulator + local Apache on port 80: `http://10.0.2.2/android_api/`
   - Android Emulator + `php -S localhost:8000`: `http://10.0.2.2:8000/android_api/`
   - Real phone (same Wi‑Fi as PC): `http://YOUR_PC_LAN_IP:8000/android_api/` (or your live domain `https://www.virtuallabsimulator.com/android_api/`)

5. **Run**
   - Sync Gradle
   - Run on emulator/device

## App update check (manual + auto)

Server endpoint:
- Upload `android_api/app_update.php` to your server (same place as the other `android_api/*.php` files).

In the app:
- Settings → **App updates**
  - **Check now** (manual)
  - **Auto-check in background** (WorkManager; shows a notification when a new GitHub release is available)

GitHub release workflow:
1. Bump `versionCode`/`versionName` in `android_studio_java_admin/app/build.gradle`.
2. Build a signed APK/AAB.
3. Create a GitHub Release with tag like `v1.1.0` and upload an `.apk` asset (the server endpoint prefers an APK asset URL).
