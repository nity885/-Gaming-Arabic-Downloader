# Gaming Arabic Downloader

A one-screen Kotlin + Jetpack Compose Android GUI that builds a search/filter/download
pipeline and hands it to **Termux** to run with your existing `yt-dlp` + `ffmpeg`. The
app itself never downloads anything — it only assembles the correct shell command and
launches it inside Termux, then reads a small status file Termux writes back.

## How it works

1. You pick categories, minimum views, video count, and quality, then tap **بحث وتحميل**.
2. The app builds one bash script (search all selected categories → filter by views →
   dedupe → shuffle → drop IDs already in `downloaded.txt` → pick exactly N → download).
3. The app sends that script to Termux via the `RUN_COMMAND` intent (Termux's official
   way of letting other apps run commands in it).
4. Termux runs `yt-dlp`/`awk`/`shuf` and writes progress to
   `~/storage/downloads/Gaming_Arabic/status.txt`.
5. The app polls that file once a second and shows "جارٍ البحث..." /
   "تم الاختيار: X/Y" / "التحميل: X/Y" / "اكتمل التحميل: X/Y".

Downloads land in `/storage/emulated/0/Download/Gaming_Arabic/`, named
`%(title)s [%(id)s].%(ext)s`, and `downloaded.txt` in that same folder is the
`--download-archive` file, so re-runs never re-download the same video.

## One-time setup (on your phone, in Termux)

You said you already have Termux, yt-dlp, ffmpeg and storage permission — you only
need one extra Termux setting so external apps are allowed to send it commands:

```
echo "allow-external-apps=true" >> ~/.termux/termux.properties
termux-reload-settings
```

Then fully close and reopen Termux once.

## Building the APK — no computer required

Since you're phone-only, build it with **GitHub Actions** (GitHub's free servers do the
compiling; you just push the code and download the finished APK from your browser or
the GitHub app):

1. Create a free GitHub account if you don't have one, and create a new repository
   (e.g. `gaming-arabic-downloader`).
2. Upload every file/folder from this project into that repo. Easiest way on mobile:
   open the repo on github.com in Chrome → "Add file" → "Upload files" → select all
   the files (keep the folder structure: `app/`, `.github/workflows/build.yml`, etc.).
   Alternatively, if you use Termux with `git`, you can `git init`, `git add .`,
   `git commit`, and `git push` from the extracted project folder.
3. Once pushed to the `main` branch, GitHub automatically runs the
   `.github/workflows/build.yml` workflow (or trigger it manually from the repo's
   **Actions** tab → "Build APK" → "Run workflow").
4. When it finishes (a few minutes), open that run in the **Actions** tab and download
   the **GamingArabicDownloader-debug-apk** artifact (a zip containing `app-debug.apk`).
5. On your phone, extract the zip, tap `app-debug.apk` to install (allow "install
   unknown apps" for your browser/file manager when prompted).

## First run on the phone

1. Open the app, select your options, tap **بحث وتحميل**.
2. Android will prompt for "All files access" (to read the status file) and for the
   Termux `RUN_COMMAND` permission — grant both. If the first tap only shows these
   prompts and doesn't start anything, tap **بحث وتحميل** again once they're granted.
3. Watch the status line update as Termux searches and downloads in the background.
   You can also switch to Termux directly to watch `yt-dlp`'s own output if you want.

## Project structure

```
GamingArabicDownloader/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/gad/app/MainActivity.kt   ← entire app logic + UI
│       └── res/values/ (strings.xml, themes.xml)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── .github/workflows/build.yml                ← builds the APK on GitHub's servers
```

## Notes / limits (by design, per spec)

- No login, accounts, thumbnails, database, WebView, YouTube API, recommendations,
  animations, or cloud backend.
- The "requested number" is the **global total** across all selected categories, not
  per category — the script pools every category's search results together, shuffles,
  and only then takes the first N.
- Category → search terms are simple Arabic keyword lists baked into `MainActivity.kt`
  (e.g. Gaming → "العاب فيديو", "تختيم لعبة", "مراجعة لعبة"); edit the `CATEGORIES`
  list there if you want different/more search terms per category.
