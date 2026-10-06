# Tube

YouTube on a Wear OS watch. Wear OS has no WebView, so Tube runs YouTube's own mobile site (m.youtube.com) and player in a bundled [GeckoView](https://mozilla.github.io/geckoview/). It doesn't extract or download streams.

- **Search by voice:** the 🔍 button.
- **Fullscreen:** YouTube's fullscreen button goes edge to edge.
- **Volume:** the crown is the volume while a video plays and scrolls the page otherwise.
- **Typing:** a page text field opens a native text box (the Wear keyboard can't type into GeckoView directly).
- **Back:** swiping back leaves fullscreen first, then goes back through pages.

Built for the Pixel Watch, so only 32-bit ARM (`armeabi-v7a`) is included.

## Build and install

```
./gradlew assembleDebug
adb install -r --no-streaming build/outputs/apk/debug/Tube-debug.apk
```

`--no-streaming` helps with large installs over Wi-Fi adb.
