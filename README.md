# 鸿蒙 4 状态栏（Harmony4StatusBar）

把 MagicOS 11 状态栏的信号 / Wi-Fi / 电池替换成 **HarmonyOS 4 的矢量图标**。
图标直接从鸿蒙 4 的 `SystemUI.apk` 提取（标准 VectorDrawable），等比缩放，任何分辨率下都是清晰的。

当前版本覆盖：

- 移动信号 `hm_signal_0..4`（含数据连接 `_data` 变体，来自 `stat_sys_signal_*`）
- Wi-Fi `hm_wifi_0..4`（来自 `stat_sys_wifi2_signal_*`）
- 电池：按鸿蒙 4 的 115×64 几何重画（外框矢量 + 按电量填充 + 充电闪电 + 居中百分比）

## 构建

GitHub Actions：仓库自带 `.github/workflows/build-apk.yml`，push 后 Actions 里下载 artifact 即可。

本地：Android Studio 打开本目录 → Build APK。

## 安装

1. 安装 APK；
2. LSPosed 启用「鸿蒙4状态栏」，作用域勾选「系统界面」（`com.android.systemui`）；
3. 打开模块点「重启系统界面」。

## 已知限制

- 只处理「百分比在图标内」的电池模式；图标外模式交回系统；
- 其余系统小图标（蓝牙、闹钟、定位、热点、NFC 等）尚未接入，后续用 `extract_harmony.py` 按同一流程补齐；
- 电池重画依赖 `HwStatusBarBatteryView` 的字段名（`mLevel/mIsCharging/mIsShowPercentIn/mTintColor`），系统大版本更新后可能需要适配。
