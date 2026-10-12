# Kindle X 咪咕版自制应用源码

本仓库收录四个自制 Android 5.1 应用的源码，适配 Kindle X 咪咕版（KFBJWI）。仓库不含设备固件、设备备份、第三方 APK 或私有签名密钥。

| 目录 | 应用 | 包名 | 用途 |
| --- | --- | --- | --- |
| `apps/weather` | 天气／成都 Kindle 桌面 | `com.liuyongliang.chengdukindledesk` | 墨水屏时钟、天气曲线和日历；内置本地 HTML 页面 |
| `apps/reader` | 书架 | `com.liuyongliang.kindlereader` | 本地图书书架和 EPUB、PDF、TXT 阅读 |
| `apps/floating-home` | 小圆点 | `com.liuyongliang.kindlefloatinghome` | 可拖动悬浮 HOME 按钮；开机启动；保持沉浸模式 |
| `apps/settings-shortcut` | 设置入口 | `com.liuyongliang.kindlesettingsshortcut` | 从启动器打开系统设置 |

## 构建

需要 JDK、Android SDK Platform 34 和 Build Tools 34.0.0。设置 `ANDROID_SDK_ROOT`（或 `ANDROID_HOME`），并确保 `java`、`javac`、`keytool` 在 `PATH` 中。构建脚本使用系统已有的 `aapt`、`d8`、`zipalign` 和 `apksigner`，不需要 Gradle。

```sh
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
./build.sh weather
./build.sh reader
./build.sh floating-home
./build.sh settings-shortcut
```

APK 生成到 `dist/`。首次构建会在 `.local/` 生成**仅用于本地调试**的签名密钥；这两个目录均被 `.gitignore` 排除。若要更新已经安装的正式 APK，须使用原签名密钥，并在本机设置 `APK_KEYSTORE`、`APK_KEY_ALIAS`、`APK_KEYSTORE_PASSWORD`，可选 `APK_KEY_PASSWORD`。原密钥未包含在此目录中。

`apps/reader` 的书本图标和 `apps/floating-home` 的浮窗图形由 `tools/generate_original_icons.py` 绘制；构建时直接使用已保存的 PNG。需要重新生成时安装 Pillow 后运行 `python3 tools/generate_original_icons.py`。

## 功能和设备条件

- `floating-home` 的沉浸模式维护需要 `WRITE_SECURE_SETTINGS`。在这台设备上它依赖 `/system/app` 系统预装身份；普通用户安装只能使用悬浮 HOME 功能，不一定能修改 `policy_control`。
- 天气应用使用 IP 地理定位（`ipwho.is`，失败时使用 `ip-api.com`）和 Open-Meteo 天气接口。`assets/isrgrootx1.pem` 是 ISRG Root X1 公共根证书，供旧 Android 设备访问 Open-Meteo；网络服务的可用性和条款以各服务提供方为准。
- 自制应用依赖设备上另行安装的 E-Ink 启动器 `cn.modificator.launcher`。它的源码和 APK **不在本仓库**；这是第三方应用，不能把从设备提取的 APK 当成自制源码提交。

## 仓库范围与版权

此目录只收录自制 Java、HTML、资源和构建脚本。未纳入 EasyTouch 的原始图标和 APK、Apple Books 图标、KOReader APK、Kindle／咪咕系统文件、设备分区镜像、测试书籍、调试截图，以及含签名密码的本机构建脚本。浮窗图形和书本图标已用本仓库的绘图脚本重新制作。

公开发布前应由仓库所有者选择源码许可证；此准备目录没有代为授予开源许可。
