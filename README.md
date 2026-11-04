# Alive 壁纸

面向一加/ColorOS 与小米/HyperOS 的第三方动态壁纸模块，基于 OpenAlive 的魅族一镜到底能力整理而成。它把息屏、锁屏和桌面动画串成一套连续过渡，并提供本机可直接使用的动态壁纸样式。

## 功能

- 息屏、锁屏、桌面三态连续过渡
- 启航、留光、星月、轻启、山脉、银河等 Alive 风格
- 支持自选锁屏/桌面照片与相框编辑
- 优先使用系统壁纸选择器，系统未完成切换时才使用 Root 兜底
- 支持 ColorOS / HyperOS 的原生息屏与系统界面适配
- 首页显示当前壁纸预览与三态预览

## 安装要求

- Android 14 及以上
- Root（KernelSU / Magisk）
- LSPosed
- 仅支持主用户

## 安装步骤

1. 从 Releases 下载最新 APK 并安装。
2. 在 LSPosed 中启用 **Alive 壁纸** 模块。
3. 勾选作用域：
   - `系统框架`
   - `系统界面`
   - ColorOS：`com.oplus.wallpapers`
   - HyperOS / 小米：`com.miui.aod`
4. 重启系统或重启 SystemUI 后打开模块。
5. 选择风格，点击“应用当前风格”。优先使用系统壁纸选择器；如果系统未切换成功，模块会在确认后使用 Root 兜底。

首次使用建议先确认 Root 已授权，并允许模块在必要时启动辅助应用进程。

## 使用说明

- 首页的“应用当前风格”用于把当前选中的 Alive 风格应用到壁纸服务。
- “锁屏壁纸”“桌面壁纸”用于从相册选择静态图片，并保留当前 Alive 动效。
- 系统壁纸选择器返回取消时，不会自动改用 Root，避免覆盖用户当前壁纸。
- 系统选择器返回成功但实际没有切换时，模块才会尝试 Root 兜底，并再次核对实际壁纸组件。

## 已知限制

- 不同厂商系统版本、桌面和壁纸服务实现差异较大，部分设备仍可能需要手动在系统动态壁纸列表中选择一次。
- 首次安装后必须正确勾选 LSPosed 作用域，否则 `AMBIENT_WALLPAPER` 相关系统权限校验可能失败。
- 仓库源码不包含签名私钥、运行数据或本地测试 APK。

## 构建

需要 JDK 17、Android SDK 35、NDK 29 和 Rust `aarch64-linux-android` 目标。完整构建说明见 [BUILD.md](BUILD.md)。

```bash
python tools/build_release.py
```

## 目录说明

- `app/`：Android 应用、资源和模块入口
- `native/`：Rust 渲染与 JNI 适配
- `vendor/`：第三方运行库和品牌原版资源
- `docs/`：开发记录与发布说明
- `tools/`：构建、导入和验证脚本

## 第三方来源与许可

本项目自实现代码遵循上游 MIT 许可。APK 和源码中还包含魅族 Flyme、小米 ThemeManager、Vivo、ImageGen/ONNX Runtime 等第三方原始资源或运行库，具体来源和许可证边界见 [THIRD_PARTY.md](THIRD_PARTY.md)。第三方资源不适用本项目 MIT 许可，请勿把整个 APK 或全部内置资源作为 MIT 作品再发布。

本项目为非官方第三方模块，与魅族、一加、OPPO、Vivo、小米或 Google 没有隶属关系，也未获得其官方背书。