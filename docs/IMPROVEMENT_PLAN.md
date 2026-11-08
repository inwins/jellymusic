# Jellyfin 音乐播放器 — 现状与改进计划

> 版本：v2.27.1（2026-11）
> 包名：`io.github.inwins.jellymusic`
> 定位：单 ExoPlayer + Media3 的原生 Jellyfin 音乐客户端，兼顾离线、音频学习与可视化。

---

## 1. 当前能力（v2.27.1）

### 1.1 播放与音频
- 单 ExoPlayer + MediaSessionService：后台播放、通知栏/锁屏、蓝牙/耳机按键、音频焦点、拔耳机暂停。
- 无缝播放调优、交叉淡入淡出、响度归一化（ReplayGain 风格）、跳过静音、预取下一首。
- 变速 0.5–2x、变调（±半音）、A-B 循环、睡眠定时（时长/播完当前曲）、淡入淡出。
- Jellyfin 播放上报（开始/进度/停止/标记已播放）+ 离线补报队列。
- 同一服务器双地址：可配置局域网地址，Wi-Fi 下优先、失败回退远程地址。
- 添加服务器支持 HTTP/HTTPS + 主机 + 端口（默认 8096），支持 Quick Connect 与多服务器切换。

### 1.2 曲库与发现
- 首页：最近添加、每日推荐、全部歌曲、流派。
- 专辑/艺术家/歌单/本地歌单；艺术家页相关艺术家、全部歌曲（按专辑分组、前 50 首可展开/收起）、专辑区。
- 全部歌曲分页、拼音 A–Z 索引、排序/过滤记忆、评分过滤、多选批量操作。
- 搜索：歌曲/专辑/艺术家 + 历史；即时电台、Smart Playlist、每日推荐、心动模式、不感兴趣。
- 相关艺人/推荐、听歌统计、年度/习惯分析（本地）。

### 1.3 离线与下载
- 手动下载、暂停/恢复、并发队列、错误可见/重试、存储用量、SAF 自定义目录与迁移。
- 自动下载：收藏 / 歌单 / 最新添加，可仅 Wi-Fi。
- 文件夹（目录）浏览与批量下载（含子目录）；文件夹多选下载。
- 下载管理：按名称/艺术家/专辑/大小/最近排序、单曲大小、多选删除、删除前确认、全部删除。
- 下载时同目录保存 `.lrc` 歌词；删除/重命名歌曲时同步处理歌词。
- 离线模式：手动开关 + 服务器不可达自动离线；离线首页/专辑/艺术家/搜索/全部歌曲可用。

### 1.4 界面与交互
- Material 3、动态取色（Android 12+）、AMOLED 纯黑、主题切换。
- 封面形状（方形/黑胶持续旋转）、自定义背景、封面多色渐变、彩色光晕、节拍脉冲、毛玻璃控制面板。
- 6 种可视化（条形/环形/组合/波形/镜像频谱/粒子）+ 全屏可视化（隐藏系统栏、轻点播放/暂停）。
- 歌词：同步歌词、偏移、全屏、和弦显示、歌词海报；歌词页封面模糊动态背景。
- Finamp 风格细进度条：缓冲进度、小圆点滑块、拖动时间气泡、已播放/剩余时间。
- 全局多选：长按歌曲或文件夹直接进入多选，全选/批量队列/歌单/下载/收藏/删除。

### 1.5 音乐学习与差异化
- 本地音频分析：BPM、调性、和弦、音阶、旋律、MFCC、能量/亮度/人声估计/节拍强度。
- 乐理入门、练耳、声乐训练（音域/长音/跟唱/热身/节奏）、调音器（多乐器）、和弦库、音阶图、节拍器、鼓机。
- 和声混音/调性匹配、跑步电台（按 BPM）、精彩片段、跳前奏、移调建议。
- 歌词海报、桌面歌词、练声/练习工具为播放器类产品中少见的差异化能力。

### 1.6 工程与发布
- Kotlin + Jetpack Compose + Media3/ExoPlayer；R8 发布构建（约 3 MB）。
- 单元测试（Robolectric + Compose UI）+ CI；多语言（中/英）。
- Quick Connect、令牌加密（SecretBox）、崩溃日志、响应缓存、Authorization 头。
- Release 签名（本地 keystore，不入库）与 GitHub Releases（v2.27.1）。
- 包名已切换为 `io.github.inwins.jellymusic`；旧 `com.example.jellymusic` 不会被覆盖安装。

---

## 2. 旧计划完成情况

| 旧计划项 | 状态 |
|---|---|
| 播放上报/续播 | ✅ 已实现（含离线队列） |
| Quick Connect | ✅ 已实现 |
| 转码档位可选 | ❌ 仍为直连/服务器默认 |
| 无缝播放 | ✅ 队列调优 |
| 睡眠定时/倍速/淡入淡出 | ✅ 已实现 |
| 签名 + CI | ✅ 已实现 |
| 自动下载（收藏/歌单/最新） | ✅ 已实现（Wi-Fi 限制） |
| 离线模式 | ✅ 已实现 |
| Android Auto | ❌ 未实现 |
| ReplayGain | ✅ 响度归一化 |
| 大库分页/排序 | ✅ 全部歌曲；专辑/艺术家部分分页 |
| 均衡器 | ✅ 基础（低音/虚拟环绕/响度）；❌ 参数均衡 |
| 多服务器 | ✅ 切换 + 双地址；❌ 聚合库 |
| 文件夹浏览 | ✅ 已实现 |
| 桌面小组件 | ❌ 未实现 |
| 平板/横屏 | ❌ 未实现 |
| 投屏/远程控制 | ❌ 未实现 |
| 播放统计 | ✅ 已实现 |
| 逐字歌词 | ❌ 未实现 |
| 更多语言/无障碍 | ⚠️ 中英双语；无障碍部分覆盖 |
| 单元/UI 测试 | ✅ 已实现 |

---

## 3. 新路线图

### P0 — 平台集成与可靠性（v2.28–v2.29）

1. **Android Auto / MediaLibraryService**
   - `MediaLibraryService` + `MediaLibrarySession`，浏览专辑/艺术家/歌单/收藏/全部歌曲。
   - 复用单 ExoPlayer，手机与车机同一队列；遵守 AA 无分页限制（优先搜索/分组）。
2. **Last.fm / ListenBrainz Scrobbling**
   - 客户端上报 + 离线待传队列；补充 Jellyfin 上报之外的生态。
3. **桌面小组件 + 快捷设置磁贴**
   - Glance：4x1 控制、封面、歌词/可视化；Quick Settings Tile。
4. **投屏与遥控**
   - Chromecast/DLNA（media3-cast）；Jellyfin “Play On” 控制其他客户端。
5. **下载索引加固**
   - 索引双写（内部存储 + 原子写 + 备份）；启动时按 `<id>.<ext>` 扫描重建。
   - “校验下载”在存储不可用时只标记缺失，不直接删除；默认目录避免被 MIUI 清理。
6. **队列历史/多队列**
   - 保存/恢复历史队列；音乐与有声书独立队列；“下一首播放/最后播放”。
7. **歌词增强**
   - 翻译/罗马音/拼音、逐字高亮、歌词搜索；卡拉 OK（人声分离，服务端插件）。
8. **平板/横屏双栏**
   - 列表 + 播放器/队列双栏；为大屏和折叠屏适配。

### P1 — 生态与音频进阶（v2.30–v2.31）

- **SyncPlay 一起听**：Jellyfin 原生建组/加入/播放/暂停/seek/队列。
- **有声书/播客模式**：章节、书签、续播、失焦自动回退、按类型独立倍速/定时。
- **音乐视频**：Jellyfin 音乐视频与音频互切。
- **Hi-Res / USB DAC**：bit-perfect、独占输出、DSD（需自定义 AudioSink/FFmpeg 扩展）。
- **参数均衡/Crossfeed**：PEQ + AutoEQ 导入、crossfeed、mono/balance、按输出设备配置。
- **发现**：遗忘的收藏、往年今日、年度报告可分享海报、随机专辑、加权/重新洗牌、按心情/年代/厂牌浏览。
- **关于这首歌 / MusicBrainz**：采样、翻唱、艺人百科。
- **播放列表**：文件夹、协作歌单、批量编辑、服务器歌单自动同步。
- **语音/歌词搜索**：语音识别；哼唱识别需第三方 API。

### P2 — 大工程与实验特性

- AI DJ / 自然语言歌单（本地规则或接 LLM）。
- 人声分离卡拉 OK + 麦克风评分（复用练声评分）。
- 音频指纹/哼唱识别、重复曲目合并。
- Android TV / Wear OS 完整应用。
- Discord Rich Presence / Home Assistant Webhook。
- 跨设备播放接力。
- 转码下载（Opus/FLAC）与多服务器聚合库（需 item→server 路由重构）。

### 明确不做

- 视频客户端（Findroid 领域）；音乐视频仅作为可选。
- 纯本地文件播放器（超出 Jellyfin 客户端定位）。

---

## 4. 技术债与风险

- **下载索引脆弱**：当前为 `Android/data/.../index.json` 单文件、非原子写、无扫描重建；MIUI 清理会丢索引（音频可能仍在）。P0 修复。
- **包名/身份**：v2.27.0 起为 `io.github.inwins.jellymusic`；旧 `com.example` 用户不会被升级。
- **许可证**：仓库为 GPL-2.0，依赖多为 Apache-2.0；如需更明确的兼容性可考虑 GPL-3.0。
- **签名密钥**：`release.jks` 与密码必须备份；丢失将无法升级已安装用户。
- **转码**：目前无质量档位与下载转码；弱网/流量体验有限。
- **多服务器**：仅切换与双地址；聚合库涉及 ID 冲突与请求路由，暂列 P2。
- **分析准确性**：BPM/调性/和弦为本地启发式估计，需在 UI 标注“估算”。
- **测试**：70+ 单元测试；仍需车机/投屏/大屏真机验证。
- **本地化/无障碍**：仅中英；部分控件缺少完整 TalkBack 标签。

---

## 5. 里程碑

| 版本 | 主题 | 主要交付 |
|---|---|---|
| v2.28 | 车载与桌面 | Android Auto、小组件、Last.fm/ListenBrainz |
| v2.29 | 可靠性与投屏 | 下载索引重建、队列历史、Chromecast/Play On |
| v2.30 | 协作与大屏 | SyncPlay、平板双栏、歌词翻译/逐字 |
| v2.31 | 音频进阶 | PEQ/AutoEQ、crossfeed、Hi-Res/USB DAC、有声书模式 |
| v3.0 | 实验特性 | AI DJ、卡拉 OK、TV/Wear OS、指纹识别 |

---

## 6. 验证方式

- 每次发布：`assembleDebug testDebugUnitTest` + 签名 `assembleRelease` + `apksigner verify`。
- 服务器侧：Jellyfin 仪表盘确认播放上报；Scrobbling 在 Last.fm/ListenBrainz 验证。
- 设备侧：ADB/SSH 安装，检查后台/锁屏/蓝牙/耳机/断网/弱网/离线。
- 大库：5000+ 曲目滚动、搜索、排序、下载页加载时延。
- 车机/投屏：AA 桌面单元或真车、Chromecast/DLNA 真机。

---

## 7. 调研参考

- Finamp（Jellyfin 音乐客户端事实标准）：https://github.com/finamp-app/finamp
- Symfonium（商业多后端播放器）：https://symfonium.app/
- Poweramp / Musicolet（本地播放器音频与队列能力）
- Spotify / Apple Music / YouTube Music / 网易云音乐（发现、歌词、一起听、AI DJ）
- Jellyfin API：`/Sessions/Playing*`、`/QuickConnect/*`、`/SyncPlay/*`、`/Audio/{id}/universal`
- Jellyfin SyncPlay 参考：https://github.com/jellyfin/jellyfin/blob/master/Jellyfin.Api/Controllers/SyncPlayController.cs
