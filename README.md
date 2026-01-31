# JavaUI
A minimalistic code editor built using Java swing, flatlaf and RSyntaxTextArea. 
This project is meant for learning and experimental purposes, by no means this is a production-ready code editor, but rather a fun attempt to learn and create my own code editor


# Features
* Syntax highlighting
* Auto save
* adding, deleting or renaming files
* open native terminal
* language support for Java - AWT, SWING (more to be added soon)
* Dark theme

# UI Blocks explaination

# Directory structure 
```
JavaUI
├─ pom.xml
└─ src
   └─ main
      ├─ java
      │  ├─ App.java
      │  ├─ CustomNode.java
      │  ├─ EditorView.java
      │  ├─ HotReloadManager.java
      │  ├─ JavaRunManager.java
      │  ├─ ProjectView.java
      │  ├─ RotatingIconLabel.java
      │  ├─ RunOutputPanel.java
      │  └─ WelcomeView.java
      └─ resources
         ├─ META-INF
         │  └─ MANIFEST.MF
         ├─ icons
         │  ├─ defaultIcon.png
         │  ├─ eyeClose.png
         │  ├─ eyeOpen.png
         │  ├─ folder_icon.png
         │  ├─ folder_icon_24.png
         │  ├─ javaIcon.png
         │  ├─ pauseIcon.png
         │  ├─ playIcon.png
         │  ├─ refreshIcon.png
         │  └─ settingIcon.png
         └─ themes
            └─ monokai.xml

```