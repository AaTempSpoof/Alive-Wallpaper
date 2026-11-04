# HyperOS 3 原生宿主适配

目标设备为红米 K80 Pro `24122RKC7C`（`miro`），Android 16，
系统版本 `OS3.0.305.0.WOMCNXM`，1440×3200 / 600 dpi，
KernelSU + LSPosed 2.2.0。

## 范围

此前小米六款超级壁纸能够运行，但主要验收宿主是 ColorOS。原生 HyperOS
上 `ColorOsBridge` 找不到 `SmoothTransitionController`，会主动跳过，
因此 Flyme Alive 的息屏、锁屏、桌面场景只能依赖通用壁纸回调。

本适配增加 `HyperOsBridge`，只在小米设备的 SystemUI 中加载，并监听以下
HyperOS 原生状态入口：

- `com.android.systemui.doze.DozeWallpaperState.transitionTo`
- `com.android.keyguard.doze.MiuiDozeService`
- `com.android.keyguard.fullaod.MiuiFullAodManager.updateSettings/fullAodEnable`
- `com.android.systemui.statusbar.policy.KeyguardStateControllerImpl`

桥接读取 `DozeWallpaperState.mIsAmbientMode`，并监听
`MiuiDozeService.onDozingRequested(boolean)`、
`notifyKeyguardState(boolean,boolean)` 和
`notifyKeyguardGoingAway(boolean)`，再通过现有受 `STATUS_BAR` 权限保护的
广播通道发送 `0=AOD`、`1=锁屏`、`2=桌面`。它不修改 HyperOS 动画、
不替换 AOD 窗口，也不在一加 ColorOS 上注册或运行。

全屏 AOD 只在 OpenAlive 自己拥有桌面或锁屏壁纸时，调整 HyperOS
`MiuiFullAodManager` 的进程内支持标志并让系统重算状态；不写
`full_screen_aod_support` 等全局设置。

HyperOS 原生一镜到底还要求 AOD 宿主同时满足两个条件：当前样式被识别为
超级壁纸样式，且壁纸信息声明支持 Ambient Mode。原系统只接受
`com.miui.miwallpaper*` 包，普通第三方壁纸会在 `AODView.makeNormalPanel`
中调用 `disableShowWallpaper()` 并铺黑背景，因此只剩下淡出与独立 AOD，
没有壁纸缩入 AOD 的连续画面。

本适配在 OpenAlive 拥有当前壁纸时，仅把 `Utils.isUsingSuperWallpaperStyle`、
SystemUI 的 `KeyguardCommonSettingObserver.getAodUsingSuperWallpaperStyle`
和 `WallPaperUtils.isWallpaperSupportsAmbientMode` 的返回值覆盖为兼容状态，
并让原生 `DozeHost` 保留首次遮罩动画。SystemUI 的一镜到底入口
`linkageViewAnim` 只认后者，因此只伪装 AOD 进程内的样式不足以触发壁纸缩放。
三份原始判断仍优先返回自身结果；其他壁纸、ColorOS 和不满足 HyperOS AOD
壁纸能力的机型不受影响。没有写 `aod_using_super_wallpaper`。

## AOD 设置

HyperOS 分支使用系统原生 `doze_always_on`。应用小米壁纸时会通过一次 Root
命令开启该开关，确保系统进入完整的 AOD 动画链路；不会调用 ColorOS 的
`AodMachineHelperProvider`。壁纸服务在 `goingtosleep`、`wakingup` 和
`keyguardgoingaway` 命令到达时提前进入相同场景，并在显示器短暂报告 `OFF`
时保留有限渲染窗口，等待 HyperOS 的 `DozeWallpaperState` 回调完成收尾。

## 动态壁纸应用

HyperOS 的 `LiveWallpaperChange` 会对目标应用检查 `MIUIOP(10045)`
（`CHANGE_WALLPAPER`）。首次显示选择器前，OpenAlive 通过一次 Root 命令把
该操作只授权给 `org.aliveclean`，避免选择器打开后立即显示“未应用”。

HyperOS 不支持一次把第三方动态壁纸同时设为主屏幕和锁定屏幕；需要在
系统选择器中分别选择“主屏幕”和“锁定屏幕”。两次操作后，系统桌面对应的
壁纸组件与锁屏壁纸组件才会都指向 `org.aliveclean.CleanWallpaper`。

## LSPosed 范围

红米 K80 Pro 至少需要启用：

- `android`（LSPosed 管理器中显示为 `system`）
- `com.android.systemui`

`com.oplus.wallpapers` 继续供一加 ColorOS 使用，小米设备不需要。

## 验证边界

静态检查、Rust 单测、完整 APK 构建、签名和资源对齐必须通过。原生 HyperOS
桥接还需要在应用真实壁纸后，分别检查熄屏 AOD、亮屏锁屏、认证解锁和桌面
四段转场，以及 SystemUI 重启后仍能加载。仅能打开应用或看到设置页不等同于
系统转场验收。
