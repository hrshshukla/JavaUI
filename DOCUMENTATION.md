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

### Build

```bat
cd JavaUI\build

jpackage ^
  --type exe ^
  --name JavaUI ^
  --input input ^
  --main-jar JavaUI-1.0-SNAPSHOT.jar ^
  --main-class App ^
  --icon icons\windows\app-512.ico ^
  --app-version 1.0.0 ^
  --vendor "Harsh Shukla" ^
  --win-menu ^
  --win-shortcut ^
  --dest output
```

👉 Output:

```
JavaUI\build\output\JavaUI.exe
```


### Installation

```bat
cd JavaUI\build\output
JavaUI.exe
```


### Remove / Uninstall

**Method 1 (Recommended):**

```
Settings → Apps → Installed apps → JavaUI → Uninstall
```
---

## 🍎 macOS

### Build

```bash
cd JavaUI/build

jpackage \
  --type dmg \
  --name JavaUI \
  --input input \
  --main-jar JavaUI-1.0-SNAPSHOT.jar \
  --main-class App \
  --icon icons/mac/app-512.icns \
  --app-version 1.0 \
  --vendor "Harsh Shukla" \
  --dest output
```

👉 Output:

```
JavaUI/build/output/JavaUI.dmg
```


### Installation

```bash
cd JavaUI/build/output
open JavaUI.dmg
```

* DMG open hoga
* **JavaUI.app → Applications** folder me drag karo


### Remove / Uninstall

```bash
rm -rf /Applications/JavaUI.app
```

