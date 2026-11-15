# Note Lock

An Android lock screen for ear training. When the screen turns on it plays a note.
Tap the speaker to hear it again, then pick the right note from six buttons to unlock.
In an emergency, swipe up from the bottom of the screen to skip it.

## Getting the APK

**With no computer (GitHub):**
1. Create a free GitHub account and a new repository.
2. Upload every file in this folder, including the hidden `.github` folder. ("Add file → Upload files" works from a phone browser if you unzip first.)
3. Open the **Actions** tab. The "Build APK" job runs automatically (or press *Run workflow*).
4. When it finishes (about 3–5 min), open the run and download **NoteLock-apk**. Unzip it to get `app-debug.apk`.

**With a computer:** open this folder in Android Studio, wait for it to sync, then choose *Build → Build APK(s)*.

## Installing
Open the APK on your phone and allow "Install unknown apps" when asked.
Open Note Lock, grant the three permissions, then turn on **Enable Note Lock**.

## How it works
A background service watches for the screen turning off and puts the note screen up
over Android's lock screen. Android won't let apps replace the real PIN or fingerprint lock,
so set your system lock to **Swipe** or **None** if you want the note to be the only lock.
If you keep a PIN, Android asks for it after you answer.

Notes come from octaves 3–5. Easy mode uses only C D E F G A B.
Get one wrong and you're shown the right answer, then a new note plays.
