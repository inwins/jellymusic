# Jellyfin 音乐播放器

[![GitHub release](https://img.shields.io/github/v/release/inwins/jellymusic?include_prereleases&label=release)](https://github.com/inwins/jellymusic/releases)
[![License](https://img.shields.io/github/license/inwins/jellymusic)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)

非官方 Jellyfin 音乐客户端。Kotlin + Jetpack Compose + Media3/ExoPlayer，单播放器架构，支持后台播放、离线下载、可视化、歌词与音乐学习工具。

> **Unofficial** Jellyfin music client for Android, built with Kotlin, Compose and Media3. Not affiliated with the Jellyfin project.

---

## 下载安装

- 最新签名 APK：[GitHub Releases](https://github.com/inwins/jellymusic/releases/latest)
- 系统要求：Android 8.0+（minSdk 26）
- 首次使用需填写 Jellyfin 服务器地址（HTTP/HTTPS、主机、端口，默认 `8096`）并登录，或使用 Quick Connect。

> 自 v2.27.0 起包名为 `io.github.inwins.jellymusic`。旧的 `com.example.jellymusic` 不会被覆盖升级，两者数据独立。

---

## 功能

### 播放
- 单 ExoPlayer + MediaSessionService：后台播放、通知栏/锁屏、蓝牙/耳机按键、音频焦点、拔耳机暂停
- 无缝播放调优、交叉淡入淡出、响度归一化（ReplayGain 风格）、跳过静音、预取下一首
- 0.5–2x 变速、变调、A-B 循环、睡眠定时、淡入淡出
- Jellyfin 播放上报（开始/进度/停止/已播放）+ 离线补报
- 同一服务器双地址：Wi-Fi 下优先本地地址，失败自动回退远程
- 多服务器保存与切换、Quick Connect 登录

### 曲库与发现
- 首页（最近添加 / 每日推荐 / 全部歌曲 / 流派）、专辑、艺术家、歌单、本地歌单、文件夹浏览
- 艺术家页：相关艺术家、全部歌曲（按专辑分组、展开/收起）、专辑区
- 全部歌曲分页、拼音 A–Z 索引、排序/过滤记忆、评分过滤
- 搜索歌曲/专辑/艺术家 + 搜索历史
- Instant Mix、Smart Playlist、每日推荐、心动模式、不感兴趣、相关艺人、听歌统计

### 离线与下载
- 手动下载、暂停/恢复、并发队列、错误提示/重试、自定义 SAF 目录与迁移
- 自动下载：收藏 / 歌单 / 最新添加，可仅 Wi-Fi
- 文件夹（目录）批量下载，含子目录；支持多选多个目录一次下载
- 下载管理：按名称/艺术家/专辑/大小/最近排序、单曲大小、多选删除、删除确认、全部删除
- 下载时在同目录保存 `.lrc` 歌词；重命名/删除歌曲时同步处理
- 离线模式：手动开关 + 服务器不可达自动进入；离线首页/专辑/艺术家/搜索/全部歌曲可用

### 界面
- Material 3、动态取色（Android 12+）、AMOLED 纯黑、主题切换
- 封面形状（方形/黑胶持续旋转）、自定义背景、多色渐变、封面光晕、节拍脉冲、毛玻璃面板
- 6 种可视化（条形/环形/组合/波形/镜像频谱/粒子）+ 全屏可视化（隐藏系统栏，轻点播放/暂停）
- 同步歌词、偏移、全屏、和弦、歌词海报、歌词页封面模糊动态背景
- Finamp 风格细进度条：缓冲进度、拖动时间气泡、已播放/剩余时间
- 全局多选：长按歌曲或文件夹直接进入多选，批量队列/歌单/下载/收藏/删除

### 音乐学习（差异化）
- 本地音频分析：BPM、调性、和弦、音阶、旋律、MFCC、能量/亮度/人声估计/节拍强度
- 乐理入门、练耳、声乐训练（音域/长音/跟唱/热身/节奏）
- 调音器（多乐器）、和弦库、音阶图、节拍器、鼓机
- 和声混音/调性匹配、跑步电台（BPM）、精彩片段、跳前奏、移调建议
- 桌面歌词、歌词海报、练声/练习工具

---

## 构建

环境：
- JDK 17
- Android SDK 34（`ANDROID_HOME`）
- Gradle 8.7（`./gradlew` 或本机 `gradle`）

```bash
# Debug APK
./gradlew assembleDebug

# 单元测试（Robolectric + Compose UI）
./gradlew testDebugUnitTest

# Release APK（R8，约 3 MB；需签名配置）
./gradlew assembleRelease
```

签名：

```bash
# 生成 release.jks 与 keystore.properties（keystore.properties 已被 .gitignore 排除）
./scripts/generate-keystore.sh

# 构建并校验
./gradlew assembleRelease
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

详细构建说明（含安装、版本发布、签名、数据目录）见 [readme.txt](readme.txt)。

---

## 项目结构

```
app/src/main/java/io/github/inwins/jellymusic/
  ui/            Compose 界面（主页、详情、播放页、设置、练习工具）
  player/        ExoPlayer、MediaSession 服务、可视化、节拍器、音效
  data/          下载、缓存、设置、音频分析、歌单、评分、会话
  jellyfin/      Jellyfin API 与数据模型
  util/          乐理、音高检测、合成器、歌词海报、格式化
app/src/test/         单元测试 / Robolectric / Compose 测试
app/src/androidTest/  仪器化测试
scripts/              发布、测试、密钥生成脚本
docs/                 改进计划与调研
```

---

## 已知限制

- 暂不支持 Android Auto、Chromecast/DLNA、SyncPlay、桌面小组件、平板双栏
- 暂无可选转码质量档位与转码下载（当前直连或服务器默认）
- 播放统计与音频分析（BPM/调性/和弦等）为本地估算，可能存在误差
- 仅有中/英界面；无障碍覆盖不完整
- 下载索引目前位于 `Android/data`，部分系统清理工具可能删除；后续将支持索引重建
- 无 Last.fm / ListenBrainz 客户端上报（Jellyfin 侧插件仍可工作）

---

## 数据与隐私

- 令牌加密保存，播放/图片请求使用 `Authorization` 头，不把令牌放进 URL
- 无第三方统计/广告；仅在启用时向你的 Jellyfin 服务器上报播放状态
- 可视化需要 `RECORD_AUDIO` 权限（读取音频输出，不录音）
- 所有音乐与服务器数据均来自你自己的 Jellyfin 实例

---

## 致谢

- [Jellyfin](https://jellyfin.org/)：媒体服务器与 API
- [AndroidX Media3](https://developer.android.com/media/media3)、[Jetpack Compose](https://developer.android.com/jetpack/compose)、[Coil](https://coil-kt.github.io/coil/)
- 设计参考：Finamp、Symfonium、Poweramp、Spotify、Apple Music、YouTube Music、网易云音乐

## License

[GNU General Public License v2.0](LICENSE)

---

### English

An unofficial Jellyfin music client for Android (Kotlin, Jetpack Compose, Media3). Features: background playback, gapless/crossfade, loudness normalization, pitch/speed, sleep timer, Jellyfin playback reporting, dual server addresses, multi-server switching, offline downloads (including folder batch downloads), lyrics with LRC sidecar files, 6 visualizer styles, and a built-in music-practice suite (tuner, metronome, ear training, vocal training). Requires Android 8.0+ and a Jellyfin server. Download the signed APK from [Releases](https://github.com/inwins/jellymusic/releases/latest).
