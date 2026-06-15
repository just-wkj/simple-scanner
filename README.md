# Simple Scanner

## 中文

Simple Scanner 是一个轻量的 Android 扫码工具，支持二维码和常见条形码。扫码结果可以复制；如果结果被识别为网址，可以直接调用系统浏览器打开。

这个项目保留 `build.sh` 作为实际 APK 打包脚本，同时提供 Gradle 配置，方便 Android Studio / IntelliJ IDEA 正确识别 Android SDK 和 ZXing 依赖。

### 功能

- 支持二维码和常见条形码识别
- 扫描结果支持复制
- 网址结果支持浏览器打开
- 不依赖联网服务

### 环境要求

- Android SDK，默认路径：`~/Library/Android/sdk`
- Android build-tools `32.0.0`
- Android platform `android-32`
- JDK 8，用于 `build.sh`
- JDK 17，用于 Gradle/IDE 导入 Android Gradle Plugin

如果你的 SDK 或工具版本不同，可以通过环境变量覆盖：

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_BUILD_TOOLS_VERSION="32.0.0"
export ANDROID_PLATFORM_VERSION="android-32"
export JDK8_HOME="/path/to/jdk8"
```

### IDE 配置

推荐用 Android Studio 打开项目。如果使用 IntelliJ IDEA，需要确认已启用 Android 插件。

1. 用 IDE 打开项目根目录，不要只打开 `src/`。
2. 看到 Gradle 提示时选择加载/信任项目。
3. Gradle JDK 选择 JDK 17。
4. 等待 Gradle Sync 完成后，`android.*` 和 `com.google.zxing.*` 包会自动识别。
5. 如果仍然标红，检查 Project Structure 里的 Android SDK 是否存在，并确认 `libs/core-3.3.1.jar` 在项目中。

这个 Gradle 配置主要用于 IDE 识别和代码导航；日常产出 APK 仍建议使用下面的 `./build.sh`。

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

### GitHub Release 自动打包

仓库内已包含 GitHub Actions workflow：

```text
.github/workflows/release.yml
```

生成 Release 的推荐方式是打一个 `v` 开头的 tag 并推送：

```sh
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions 会自动：

- 安装 Android SDK platform/build-tools
- 执行 `./build.sh`
- 生成 `SimpleScanner-v1.0.0.apk`
- 创建 GitHub Release
- 把 APK 上传到 Release 附件

也可以在 GitHub 页面进入 `Actions`，选择 `Build APK and Release`，手动运行 workflow。手动运行只上传构建产物 artifact，不创建正式 Release；推送 tag 才会创建 Release。

### Git 提交建议

建议提交：

```text
.github/workflows/release.yml
.gitignore
AndroidManifest.xml
README.md
build.gradle
build.sh
libs/core-3.3.1.jar
res/
settings.gradle
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

The project keeps `build.sh` as the APK packaging path and also includes Gradle configuration so Android Studio / IntelliJ IDEA can resolve the Android SDK and ZXing dependency correctly.

### Features

- QR code and common barcode scanning
- Copy scan results
- Open URL results in a browser
- No network service dependency

### Requirements

- Android SDK, default path: `~/Library/Android/sdk`
- Android build-tools `32.0.0`
- Android platform `android-32`
- JDK 8 for `build.sh`
- JDK 17 for Gradle/IDE Android Gradle Plugin import

You can override local paths and versions with environment variables:

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_BUILD_TOOLS_VERSION="32.0.0"
export ANDROID_PLATFORM_VERSION="android-32"
export JDK8_HOME="/path/to/jdk8"
```

### IDE Setup

Android Studio is recommended. If you use IntelliJ IDEA, make sure the Android plugin is enabled.

1. Open the project root, not only the `src/` directory.
2. Load/trust the Gradle project when prompted.
3. Set the Gradle JDK to JDK 17.
4. Wait for Gradle Sync to finish; `android.*` and `com.google.zxing.*` should resolve.
5. If imports are still red, check Project Structure for a valid Android SDK and confirm `libs/core-3.3.1.jar` exists.

The Gradle files are mainly for IDE indexing and navigation. For APK output, `./build.sh` remains the recommended path.

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

### GitHub Release Build

The repository includes this GitHub Actions workflow:

```text
.github/workflows/release.yml
```

Create a Release by pushing a tag that starts with `v`:

```sh
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions will:

- Install the required Android SDK platform/build-tools
- Run `./build.sh`
- Generate `SimpleScanner-v1.0.0.apk`
- Create a GitHub Release
- Upload the APK as a Release asset

You can also open `Actions` on GitHub and manually run `Build APK and Release`. Manual runs upload an artifact only; tag pushes create a formal Release.

### Git Notes

Recommended files to commit:

```text
.github/workflows/release.yml
.gitignore
AndroidManifest.xml
README.md
build.gradle
build.sh
libs/core-3.3.1.jar
res/
settings.gradle
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
