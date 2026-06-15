# Simple Scanner

## 中文

Simple Scanner 是一个轻量的 Android 扫码工具，支持二维码和常见条形码。扫码结果可以复制；如果结果被识别为网址，可以直接调用系统浏览器打开。

这个项目目前不是 Gradle 项目，而是使用 `build.sh` 直接调用 Android SDK 工具链完成离线构建。

### 功能

- 支持二维码和常见条形码识别
- 扫描结果支持复制
- 网址结果支持浏览器打开
- 不依赖联网服务

### 环境要求

- Android SDK，默认路径：`~/Library/Android/sdk`
- Android build-tools `32.0.0`
- Android platform `android-32`
- JDK 8

如果你的 SDK 或工具版本不同，可以通过环境变量覆盖：

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_BUILD_TOOLS_VERSION="32.0.0"
export ANDROID_PLATFORM_VERSION="android-32"
export JDK8_HOME="/path/to/jdk8"
```

### 打包

```sh
./build.sh
```

构建成功后输出：

```text
build/SimpleScanner.apk
```

### 安装到手机

手机开启 USB 调试后执行：

```sh
adb install -r build/SimpleScanner.apk
```

如果 ColorOS 的安装确认导致 `adb install` 不稳定，也可以用：

```sh
adb push build/SimpleScanner.apk /data/local/tmp/SimpleScanner.apk
adb shell pm install -r /data/local/tmp/SimpleScanner.apk
```

### Git 提交建议

建议提交：

```text
AndroidManifest.xml
README.md
build.sh
libs/core-3.3.1.jar
res/
src/
```

不要提交：

```text
build/
.idea/
*.iml
*.apk
*.idsig
*.dex
*.class
*.keystore
.DS_Store
```

注意：`build/simple-scanner.keystore` 是本地自动生成的签名密钥，默认不提交。只要不删除当前 `build/` 目录，后续本机覆盖安装会继续使用同一个签名。如果换电脑或清空 `build/` 后重新生成签名，可能需要先卸载手机上的旧版本再安装。

## English

Simple Scanner is a lightweight Android scanner app for QR codes and common barcodes. Scan results can be copied, and URL results can be opened with the system browser.

This project is currently not a Gradle project. It uses `build.sh` to call the Android SDK tools directly for an offline build.

### Features

- QR code and common barcode scanning
- Copy scan results
- Open URL results in a browser
- No network service dependency

### Requirements

- Android SDK, default path: `~/Library/Android/sdk`
- Android build-tools `32.0.0`
- Android platform `android-32`
- JDK 8

You can override local paths and versions with environment variables:

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_BUILD_TOOLS_VERSION="32.0.0"
export ANDROID_PLATFORM_VERSION="android-32"
export JDK8_HOME="/path/to/jdk8"
```

### Build

```sh
./build.sh
```

The signed APK will be generated at:

```text
build/SimpleScanner.apk
```

### Install

Enable USB debugging on your Android device, then run:

```sh
adb install -r build/SimpleScanner.apk
```

If `adb install` is blocked by ColorOS install confirmation, use:

```sh
adb push build/SimpleScanner.apk /data/local/tmp/SimpleScanner.apk
adb shell pm install -r /data/local/tmp/SimpleScanner.apk
```

### Git Notes

Recommended files to commit:

```text
AndroidManifest.xml
README.md
build.sh
libs/core-3.3.1.jar
res/
src/
```

Do not commit:

```text
build/
.idea/
*.iml
*.apk
*.idsig
*.dex
*.class
*.keystore
.DS_Store
```

Note: `build/simple-scanner.keystore` is generated locally and ignored by default. Keep the current `build/` directory if you want future builds on this machine to keep the same signing key for app updates. If the key is regenerated on another machine or after cleaning `build/`, Android may require uninstalling the old app before installing the new one.
