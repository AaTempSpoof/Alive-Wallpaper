# Alive 壁纸

Alive 壁纸是一个独立的 Android 动态壁纸项目，提供息屏、锁屏和桌面的连贯过渡动画，并适配一加 ColorOS 与小米 HyperOS。

首页由项目自行渲染，包含当前壁纸预览、Alive 壁纸相册入口、息屏/锁屏/桌面三态预览和应用入口。支持启航、留光、星月、轻启、山脉和银河，也可自选照片。

## 使用要求

- Android 设备需要 Root 和 LSPosed。
- 启用模块后，在 LSPosed 中勾选系统框架 `android`、系统界面 `com.android.systemui`。
- ColorOS 设备同时勾选 `com.oplus.wallpapers`；小米设备同时勾选 `com.miui.aod`。
- 勾选作用域后重启系统。

## 下载与说明

- [下载 1.1](https://github.com/AaTempSpoof/Alive-Wallpaper/releases/tag/v1.1)
- [编译说明](BUILD.md)
- [发布说明](docs/release-notes.md)

## 致谢

感谢 [Maga-King/OpenAlive](https://github.com/Maga-King/OpenAlive) 提供 ColorOS 代码适配。其余功能、界面、跨平台适配和本项目使用的实现均已重新编写或独立维护。

Alive 壁纸是非官方第三方项目，与一加、OPPO、小米、Google 或其他品牌没有隶属关系或官方背书。