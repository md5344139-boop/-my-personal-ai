# My Personal AI — Professional Android Starter

এই প্রজেক্টটি Kotlin-ভিত্তিক native Android starter। এটি Android 11 এবং পরের সংস্করণের ফোনে চলার জন্য প্রস্তুত করা হয়েছে (minSdk 23)।

## এই v1-এ যা বাস্তবে কাজ করে
- Native Android application
- Dark chat interface
- Offline / Online mode selector (এখন কেবল UI selection)
- Chat message bubble
- Local conversation persistence (SharedPreferences-এ JSON)
- GitHub Actions cloud workflow দিয়ে debug APK build

## যা এখনো সংযুক্ত নয়
- কোনো AI model/API এখনো যুক্ত নেই; উত্তরগুলো placeholder।
- llama.cpp/GGUF local inference
- Online AI backend/API
- Voice input/output
- Human 3D avatar/video call
- Room database migration, streaming, file/image upload

এগুলো পরবর্তী ধাপে যুক্ত হবে। API key কখনো APK-র মধ্যে hardcode করবে না; Online AI-এর জন্য backend proxy ব্যবহার করতে হবে।

## ফোন থেকে Cloud APK build
1. ZIP extract করো।
2. GitHub-এ একটি নতুন repository তৈরি করো, যেমন `my-personal-ai`।
3. ZIP-এর ভেতরের `MyPersonalAI-Professional` ফোল্ডারের contents repository-র root-এ upload করো। `.github` ফোল্ডারটি অবশ্যই upload করবে।
4. GitHub repository-র Actions tab খোলো।
5. `Build My Personal AI APK` workflow-এ গিয়ে `Run workflow` চাপো।
6. Build সফল হলে workflow run খুলে Artifacts থেকে `My-Personal-AI-v1-debug` download করো।
7. Artifact ZIP extract করে `app-debug.apk` install করো।

## Build configuration
- Kotlin 2.2.20
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- JDK 17
- Compile SDK 36 / Target SDK 35
- Minimum SDK 23

GitHub Actions hosted-runner usage limits এবং account policy প্রযোজ্য।
