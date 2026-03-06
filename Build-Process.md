### Run the app

```bash
cd ~/Documents/JavaUI

mvn clean compile exec:java -Dexec.mainClass=App
```

### Create JAR

```bash
mvn clean package
```

### Kill Ghost process

```bash
 ps aux | grep java
```
---
## Linux

### Build

```bash
cd JavaUI\build

jpackage \
  --type deb \
  --name javaui \
  --input input \
  --main-jar JavaUI-1.0-SNAPSHOT.jar \
  --main-class App \
  --icon icons/linux/app-512.png \
  --app-version 1.0 \
  --vendor "Harsh Shukla" \
  --linux-shortcut \
  --dest output

```

- Installation

```bash
 cd JavaUI/build/output
 sudo apt install ./javaui_1.0_amd64.deb
```

- Remove

```bash
sudo apt purge javaui
```

---

## 🪟 Windows

### 1) Problem start (what happened)

* `jpackage` run kiya → **error**: WiX tools (`light.exe`, `candle.exe`) missing and installer `.exe` failed.
* PowerShell me multiline `^` use karne ki wajah se initial parse error (caret `^` CMD ke liye hota hai — PowerShell me backtick `` ` `` use karna hota hai).

---

### 2) Why WiX chahiye tha

* Windows **installer (.exe)** banane ke liye `jpackage` WiX toolset ko call karta hai.
* Agar WiX nahi milta → `Error: Invalid or unsupported type: [exe]` ya “Can not find WiX tools” aata hai.

---

### 3) Steps we followed (exact, chronological)

#### A. Quick checks / PowerShell fixes

* Check `jpackage` version:

```powershell
jpackage --version
```

* PowerShell me multiline command karne ke liye backtick use karo:

```powershell
jpackage `
  --type exe `
  --name JavaUI `
  ...
```

* Or single-line CMD style:

```bat
jpackage --type exe --name JavaUI --input input --main-jar JavaUI-1.0-SNAPSHOT.jar ...
```

#### B. Install WiX (v3.14 recommended for jpackage)

* Download & install WiX v3.14 (wix314.exe) from wix3 releases.
* Add WiX bin to PATH, e.g.:

```
C:\Program Files (x86)\WiX Toolset v3.14\bin
```

* Verify:

```powershell
candle -?
light -?
```

#### C. Create an app-image first (safe test)

* Make app-image (bundeled runtime visible & easy to debug):

```powershell
jpackage --type app-image --name JavaUI --input input --main-jar JavaUI-1.0-SNAPSHOT.jar --main-class App --dest output-appimage --verbose
```

* Inspect produced layout:

```
output-appimage\JavaUI
 ├─ JavaUI.exe
 ├─ app\JavaUI-1.0-SNAPSHOT.jar
 └─ runtime\... (bundled JVM)
```

#### D. Verify bundled JVM + run jar directly

* Run bundled JVM to confirm app works:

```powershell
cd D:\Gallery\JavaUI\build\output-appimage\JavaUI
.\runtime\bin\java.exe -jar .\app\JavaUI-1.0-SNAPSHOT.jar
```

* Also test the launcher from CMD to see console output:

```cmd
cd /d D:\Gallery\JavaUI\build\output-appimage\JavaUI
JavaUI.exe
```

(If CMD shows no error → app-image works fine, meaning runtime is good.)

#### E. Build proper installer that includes that runtime

* Repackage installer and explicitly point to the working runtime:

```powershell
jpackage `
  --type exe `
  --name JavaUI `
  --input input `
  --main-jar JavaUI-1.0-SNAPSHOT.jar `
  --main-class App `
  --runtime-image output-appimage\JavaUI\runtime `
  --icon icons\windows\app-512.ico `
  --app-version 1.0.0 `
  --vendor "Harsh Shukla" `
  --win-menu `
  --win-shortcut `
  --dest output `
  --verbose
```

* Produced file to distribute: `build\output\JavaUI-1.0.0.exe` (give **this** to users).

---

### 4) Debug tips we used / keep handy

* If installer shows “Failed to launch JVM”:

  * Run the installed `JavaUI.exe` from **CMD** (not double-click) to see stderr.
* If jar runs with `java -jar` but exe fails → runtime not bundled or installer didn’t register runtime path.
* Use `--win-console` or `--verbose` in jpackage to get more logs while packaging.
* Check `app\JavaUI.cfg` inside app-image for `Main-Class` mismatch. If your main class is inside a package, use **fully qualified** name: `com.example.App`.
* Bitness: ensure bundled runtime (jlink/jpackage used one) matches target Windows (64-bit).
* If native crash: confirm Microsoft Visual C++ Redistributable installed (msvcr/vcruntime DLLs).

---

### 5) Uninstall / cleanup notes (when installer was incomplete)

* If app folder exists but `Programs & Features` has no entry:

  * Manually delete installed folder, Start Menu shortcut, desktop shortcut.
  * (Optional) Clean registry: `regedit` → search `JavaUI` and remove leftover keys.
* Better: build installer again with `--runtime-image` so Windows uninstall entry is created properly.

---

### 6) One-line checklist (for future builds)

1. `jpackage --version` → ensure JDK 14+
2. Install WiX v3.14 + add to PATH
3. `jpackage --type app-image ...` → test app/image & runtime
4. If OK → `jpackage --type exe --runtime-image <that-runtime> ...` → produce installer
5. Test installer on clean Windows VM and verify install/uninstall

---

