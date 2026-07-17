# WebPanel

网页信息展示面板 — 实时监控网页内容变化，有新内容时自动弹出提醒。

支持 **Windows** 和 **Android** 双平台。

## 功能特性

- 全屏/靠边显示网页内容
- 定时检测页面内容变化（自动过滤时间戳，避免误判）
- 有新内容时自动弹出窗口提醒
- 无新内容一段时间后自动隐藏
- 可配置 URL、检测间隔、隐藏延迟等参数
- Android 端支持开机自启

## Windows 版

基于 **WPF + WebView2**（.NET 8 LTS）开发。

### 环境要求

- Windows 10 1803+
- Edge Chromium（WebView2 Runtime，Win10 自带）

### 构建

```bash
dotnet build WebPanel\WebPanel.csproj -c Release
```

### 运行

```
WebPanel\bin\Release\net8.0-windows\WebPanel.exe
```

### 配置文件 `config.ini`

```ini
[General]
; 网页地址
Url=https://example.com
; 自动刷新间隔（秒），0 表示不刷新
RefreshInterval=0
; 内容检测间隔（秒），0 表示不检测
ContentCheckInterval=30
; 无新内容后隐藏窗口延迟（分钟），0 表示不隐藏
HideDelayMinutes=10

[Window]
; 窗口透明度 0.0~1.0
Opacity=0.85
; 是否始终在最前面
Topmost=true
```

### 窗口操作

| 操作 | 说明 |
|------|------|
| 拖动 | 拖动顶部标题栏区域 |
| 最小化 | 点击标题栏右侧 `─` 按钮，隐藏到系统托盘 |
| 恢复 | 双击系统托盘图标 |
| 退出 | 右键系统托盘 → 退出 |
| 透明度 | 右键系统托盘 → 透明度 |
| 置顶切换 | 右键系统托盘 → 置顶 |

### 窗口特性

- 默认占据屏幕右侧 1/3，高度 80% 居中
- 无边框、无任务栏图标
- 顶部 32px 拖动条，可拖动移动窗口
- 透明度可通过托盘菜单调节

## Android 版

基于 **Kotlin + WebView** 原生开发。

### 环境要求

- Android 8.0+（API 26+）
- Android Studio + JDK 17+

### 构建

```bash
cd WebPanelAndroid
.\gradlew.bat assembleDebug
```

APK 输出位置：

```
app\build\outputs\apk\debug\app-debug.apk
```

### 功能说明

- 全屏沉浸式显示网页内容
- 右上角 ⚙ 按钮进入设置页面
- 后台前台服务保活，持续监控内容变化
- 通知栏显示服务状态，可快速进入设置
- 支持开机自启动

### 设置项

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| 网页地址 | https://news.qq.com | 要监控的网页 |
| 刷新间隔 | 0 | App 主动刷新间隔（秒），0 由网页自己刷新 |
| 检测间隔 | 30 | 内容变化检测间隔（秒） |
| 隐藏延迟 | 10 | 无新内容后隐藏到后台的时间（分钟） |
| 开机自启 | 开 | 开机自动启动服务 |

## 内容检测原理

两个平台使用相同的 JavaScript 脚本检测内容变化：

1. 获取页面 `document.body.innerText`
2. 用正则去除所有时间戳格式：
   - `YYYY/MM/DD HH:mm:ss`
   - `YYYYMMDDHHmmss`（14位数字）
   - `HH:mm:ss`（纯时间）
3. 对清理后的文本计算 hash
4. 与上次 hash 对比，不同则判定为有新内容

这样页面上的时钟更新不会触发误报，只有文件名、数量等实质性内容变化才会被检测到。

## 项目结构

```
WebPanel/
├── WebPanel/                    # Windows WPF 项目
│   ├── MainWindow.xaml/.cs      # 主窗口
│   ├── ConfigManager.cs         # INI 配置读写
│   ├── WindowHelper.cs          # Win32 API 窗口控制
│   ├── TrayIconManager.cs       # 系统托盘
│   └── config.ini               # 配置文件
├── WebPanelAndroid/             # Android Kotlin 项目
│   └── app/src/main/
│       ├── MainActivity.kt      # 主界面 + 内容检测
│       ├── SettingsActivity.kt  # 设置界面
│       ├── WebPanelApp.kt       # Application
│       ├── service/             # 前台服务
│       ├── util/                # 配置管理
│       └── receiver/            # 开机自启
└── README.md
```

## 许可

MIT License
