# Jellyfin 音乐播放器 — 竞品调研与改进计划

> 版本：2026-10（对应本产品 v1.9.0）
> 调研对象：Findroid、Finamp、Jellyfin 官方 Android、Ultrasonic、DSub/DSub2000，并以 Symfonium / Manet 作为商业产品参照。
> 目标：找出同类产品的共性能力、社区真实痛点，并给出本产品可执行的分阶段改进计划。

---

## 1. 调研方法与信息来源

- GitHub 仓库 README、Releases、Issues（按 enhancement / bug 标签与点赞数筛选）
- Google Play / F-Droid 商店页与用户评论
- Jellyfin 官方文档与 API（Session/Playstate、Quick Connect、Universal Audio）
- 社区讨论（Reddit r/jellyfin、Jellyfin Android Discussion #1805、XDA 评测）

主要来源：

- Findroid：https://github.com/jarnedemeulemeester/findroid
- Findroid 音乐支持请求：#516
- Finamp：https://github.com/finamp-app/finamp
- Finamp 下载系统重写：`DOWNLOADS_PLAN.md`
- Finamp Issues：#1778（收藏不同步）、#1795（自动下载）、#1809（离线模式错误）、#1793（特定曲目中断）、#1763（下载问题难排查）
- Jellyfin Android：https://github.com/jellyfin/jellyfin-android
- Jellyfin Android 无缝播放：#981；后台播放卡顿：#39
- Jellyfin Android Auto 讨论：#1805；AA 曲目错乱：#1150
- 播放上报 API：`POST /Sessions/Playing`、`/Sessions/Playing/Progress`、`/Sessions/Playing/Stopped`
- Universal Audio：`GET /Audio/{itemId}/universal`
- Quick Connect：`/QuickConnect/Initiate`、`/QuickConnect/Connect`、`/Users/AuthenticateWithQuickConnect`
- Ultrasonic：https://gitlab.com/ultrasonic/ultrasonic
- DSub2000：https://github.com/paroj/DSub2000
- XDA：Jellyfin 音乐播放与离线体验不足的讨论

---

## 2. 竞品速览

| 产品 | 定位 | 技术栈 | 关键能力 | 主要问题（社区反馈） |
|---|---|---|---|---|
| **Finamp** | 专注 Jellyfin 的音乐播放器 | Flutter | 无缝播放、离线下载、转码、ReplayGain、歌词（逐字）、Android Auto/CarPlay、Quick Connect、播放上报、自动下载、自定义首页、队列 Next Up、睡眠定时、倍速、淡入淡出、多服务器（规划） | 下载系统历史上脆弱（已重写）；收藏偶发不同步；离线模式边界错误；大库性能；AA 相关限制 |
| **Findroid** | 专注 Jellyfin 的视频客户端 | Kotlin + Compose + ExoPlayer/mpv | 原生 UI、离线下载、直连播放、字幕、PiP、媒体章节 | **不支持音乐**（#516 有 12+ 赞）；部分下载/离线播放问题；AA/投屏仍在规划 |
| **Jellyfin 官方 Android** | 官方客户端（视频为主） | Kotlin/Java + WebView/ExoPlayer | 官方兼容性、Android Auto（重写中）、投屏 | 音乐播放不稳定（锁屏/弱网中断）、离线体验差、AA 与手机端两个播放器导致状态错乱（#1150）、无缝播放长期未实现（#981） |
| **Ultrasonic** | Subsonic 系音乐客户端 | Kotlin + media3 | 多服务器、离线、缓存、随机、Jukebox、ReplayGain、AA | 维护节奏慢；AA 能力有限；UI 偏旧 |
| **DSub / DSub2000** | Subsonic 系经典客户端 | Java | 缓存、无缝、队列拖拽、音频焦点、锁屏、睡眠定时、ReplayGain | 架构老、依赖旧、维护成本高 |
| **Symfonium**（商业对照） | 多后端音乐播放器 | 闭源 | 完整离线、转码、ReplayGain、AA、投屏、播放上报 | 收费；部分服务端插件兼容问题 |

### 结论

1. **Jellyfin 音乐客户端是明确空白**：Findroid 不做音乐，官方客户端音乐体验长期被诟病，Finamp 是事实标准。
2. **Finamp 定义了“合格 Jellyfin 音乐客户端”的能力基线**：无缝、离线下载、转码、ReplayGain、AA、Quick Connect、播放上报、歌词、队列管理。
3. **社区最常抱怨的不是“能不能播”，而是“可不可靠”**：锁屏/弱网中断、离线下载难管理、AA 状态错乱、播放记录/Scrobbling 不生效。
4. **AA 双播放器是官方客户端的架构性问题**；本产品已经是“单 ExoPlayer + MediaSession”，天然规避该问题，这是可放大的优势。

---

## 3. 社区需求与问题（按主题）

### 3.1 播放可靠性与后台
- 锁屏/切后台后播放中断、弱网恢复困难（XDA、官方 issue #39）。
- 需要稳定的前台服务、媒体通知、蓝牙/耳机按键、音频焦点、拔耳机暂停。
- **本产品**：已实现 MediaSessionService、通知栏/锁屏/蓝牙控制、音频焦点、becoming noisy、后台播放。

### 3.2 无缝播放与音质
- 无缝播放是古典/现场/概念专辑的刚需（官方 #981、Reddit）。
- ReplayGain（音量归一化）被 Finamp/Ultrasonic 用户视为“和 Spotify 一致”的关键。
- 均衡器、淡入淡出、倍速是进阶需求。
- **本产品**：尚未显式保证无缝；无 ReplayGain/EQ/倍速/淡入淡出。

### 3.3 离线与下载
- 用户希望“下载收藏/歌单/整库/最新”，并**自动保持更新**（Finamp #1795）。
- 下载失败要可见、可重试；下载系统要稳定（Finamp 下载重写文档、#1763）。
- 离线要有**明确模式**，不能只是“报错”（Finamp #1809）。
- 需要离线元数据（封面、歌词、增益），否则离线体验残缺。
- **本产品**：有手动下载、SAF 目录、迁移、取消/重试、错误标记；缺自动下载、下载队列/后台任务、离线模式、离线元数据。

### 3.4 Android Auto / 车载
- AA 不支持分页、列表项有上限；大库需要搜索/分组策略（官方讨论 #1805）。
- 手机与车机必须是**同一个播放器/队列**，否则状态错乱（#1150）。
- 用户期望在车机浏览专辑/艺术家/歌单/收藏。
- **本产品**：已有 MediaSession（基础控制），但未实现可浏览的 `MediaLibraryService`；单播放器架构是优势。

### 3.5 播放上报 / Scrobbling / 续播
- Jellyfin 的“正在播放”、播放次数、续播、Last.fm/ListenBrainz 插件都依赖客户端上报
  `POST /Sessions/Playing`、`/Sessions/Playing/Progress`、`/Sessions/Playing/Stopped`。
- 不上报会导致：服务器看不到播放、播放次数不增长、换设备无法续播、Scrobbling 失效。
- **本产品**：完全未上报（重要缺口）。

### 3.6 登录与服务器
- Quick Connect（免密码登录）是电视/车机/共享场景的常见需求（Finamp/Jellyfin 均支持）。
- 多服务器/多用户、局域网发现、自签名证书、mTLS、HTTP 307/308 重定向（Finamp #1810）。
- **本产品**：仅单服务器 + 用户名密码；无 Quick Connect/发现/多服务器。

### 3.7 曲库与检索
- 大库性能、分页、排序/过滤、全部歌曲、流派、文件夹视图、字母快速滚动（Finamp #1790、Findroid #1312）。
- 搜索需要覆盖歌曲/专辑/艺术家/流派，并保留历史。
- **本产品**：已有全部歌曲分页、流派、歌曲/专辑/艺术家搜索、搜索历史；缺专辑/艺术家分页、排序过滤、文件夹视图、字母滚动。

### 3.8 队列与播放控制
- 队列 Next Up、拖动排序、随机/循环、睡眠定时、倍速、淡入淡出、播放速度（Finamp）。
- 队列在重启后恢复、可回到历史队列。
- **本产品**：已有拖动排序、下一首播放/加入队列、四种播放模式、重启恢复；缺睡眠定时、倍速、淡入淡出、历史队列。

### 3.9 歌词、封面与元数据
- 同步歌词、逐字高亮、全屏；艺术家图/专辑封面离线可用。
- **本产品**：同步歌词 + 全屏；封面走网络（离线依赖 Coil 缓存）；缺逐字歌词、离线元数据包。

### 3.10 多语言、无障碍、平板
- Finamp 支持大量语言；AA/无障碍标签；平板分栏。
- **本产品**：中英双语；缺繁体/更多语言、平板布局、部分无障碍标签。

---

## 4. 本产品现状与差距

### 4.1 已经具备（相对优势）
- 单 ExoPlayer + MediaSessionService：后台播放、通知栏/锁屏/蓝牙控制、音频焦点、拔耳机暂停。
- 本地优先收藏/歌单 + 联网自动同步（解决 Finamp #1778 类问题）。
- 播放缓存（边播边缓存、听完保留、按听歌次数/最后播放日期淘汰）。
- 手动下载（SAF 目录、迁移、取消/重试、错误提示）。
- 全屏同步歌词、四种播放模式、队列拖动排序/下一首/加入队列、启动恢复队列。
- 搜索（歌曲/专辑/艺术家）+ 历史、全部歌曲分页、流派浏览。
- 响应缓存（减少请求）、令牌移出 URL（Authorization 头）、401 处理、网络恢复自动同步。
- 中英双语、应用内更新日志、R8 发布构建（2.6MB）。

### 4.2 关键差距（按影响排序）

| 差距 | 影响 | 现状 |
|---|---|---|
| 播放上报/续播 | 服务器无播放记录、无续播、无 Scrobbling | 未实现 |
| 转码质量与无缝播放 | 弱网/移动流量体验、专辑连贯性 | 仅直连/默认转码 |
| 自动下载与离线模式 | 通勤/飞行场景核心体验 | 仅手动下载 |
| Android Auto 浏览 | 车载场景 | 仅基础媒体控制 |
| Quick Connect | 登录便利性/电视场景 | 未实现 |
| ReplayGain | 音量一致性 | 未实现 |
| 睡眠定时/倍速/淡入淡出 | 睡前/播客/有声书场景 | 未实现 |
| 大库分页/排序/文件夹 | 大库性能与查找 | 部分 |
| 多服务器/多用户 | 家庭共享 | 未实现 |
| 测试/CI/签名发布 | 质量与可发布性 | 未实现 |

---

## 5. 改进计划（分阶段）

### P0 — v2.0 “可靠流媒体”（建议 2 周）

**目标**：让播放“像 Spotify 一样可靠”，并与 Jellyfin 生态打通。

1. **播放上报与续播**（3–4 人日）
   - 实现 `PlaySessionId`；播放开始/每 10 秒/暂停/恢复/停止时上报。
   - 字段：`ItemId`、`PositionTicks`、`IsPaused`、`CanSeek`、`PlayMethod`、`PlaylistIndex/Length`、`EventName`。
   - 停止/切歌时上报 `Stopped`，带最终位置。
   - 拉取 `UserData.PlaybackPositionTicks`，打开专辑/歌单时从上次位置继续（可配置“总是从头”）。
   - 验收：Jellyfin 仪表盘显示“正在播放”；播放次数增长；换设备可续播；Last.fm/ListenBrainz 插件可记录。

2. **Quick Connect 登录**（1–2 人日）
   - `GET /QuickConnect/Enabled` → `POST /QuickConnect/Initiate` → 显示 6 位码 → 每 5 秒 `GET /QuickConnect/Connect?Secret=` → `POST /Users/AuthenticateWithQuickConnect`。
   - 登录页增加“使用 Quick Connect”；服务端未启用时给出提示。

3. **转码质量与无缝播放**（2–3 人日）
   - 设置项：原始/320/192/128 kbps；局域网默认原始，移动网络默认 192。
   - 使用 `GET /Audio/{id}/universal?static=false&audioCodec=aac&maxStreamingBitrate=...&transcodingContainer=...&deviceId=...&userId=...`。
   - 流缓存键加入“质量档位”，切换质量时清理旧档缓存。
   - 无缝：单队列 + 预加载下一首（`DefaultLoadControl` 调优）、`setPauseAtEndOfMediaItems(false)`、避免每首重建 MediaSource；用 gapless 专辑（FLAC/MP3）验收无间隙。
   - 验收：弱网下不中断；切歌无可闻间隙；流量可控。

4. **睡眠定时 / 倍速 / 淡入淡出**（1 人日）
   - 睡眠定时（15/30/60/90 分钟、播完当前曲目）；`exo.setPlaybackParameters(speed)`；淡入淡出 200–500ms。

5. **发布工程**（2–3 人日）
   - 签名 release（keystore 由你提供或使用调试证书签名用于内测）。
   - GitHub Actions：assembleDebug + 单元测试 + lint。
   - 版本发布脚本已具备（构建→提交→打标签），补充 release APK 产物归档。

**v2.0 验收清单**
- 播放上报在服务器仪表盘可见；续播生效。
- Quick Connect 可登录。
- 三种音质可切换；无缝专辑无间隙。
- 睡眠定时/倍速可用。
- 签名 APK 可安装；CI 绿。

---

### P1 — v2.1 “离线与车载”（建议 3 周）

1. **自动下载 + 下载队列 + 后台任务**（5–6 人日）
   - 规则：收藏、指定歌单、指定专辑、整个库、最新添加。
   - WorkManager 周期同步；新增曲目自动加入队列；可转码下载（省空间）。
   - 下载队列持久化、并发上限、断点/重试、通知进度、存储上限与清理策略。
   - 离线元数据：封面、歌词、`NormalizationGain` 一并保存。
   - 验收：新增收藏/歌单曲目在联网后自动下载；断网续传；失败可见可重试。

2. **离线模式**（3–4 人日）
   - 显式开关 + “服务器不可达自动离线”。
   - 离线时仅展示已下载/已缓存内容；禁用网络请求；队列可离线播放。
   - 恢复联网自动退出离线并同步待办。
   - 验收：飞行模式下完整可用；无“加载失败”噪音。

3. **Android Auto 浏览**（5–7 人日）
   - 将 `MediaSessionService` 升级为 `MediaLibraryService` + `MediaLibrarySession`。
   - 实现 `onGetLibraryRoot` / `onGetChildren` / `onGetItem` / `onSearch`：专辑、艺术家、歌单、收藏、全部歌曲。
   - 遵守 AA 无分页限制：限制每层数量、优先搜索、按字母分组。
   - 复用同一个 ExoPlayer（避免官方客户端 #1150 的双实例错乱）。
   - 验收：车机可浏览并播放专辑/歌单；手机与车机队列一致。

4. **ReplayGain**（2 人日）
   - 解析 `NormalizationGain`（dB），按曲目/专辑模式应用线性增益；设置项：关/曲目/专辑；削波保护。

5. **大库体验**（3 人日）
   - 专辑/艺术家/流派分页（`StartIndex/Limit`）；字母快速滚动；排序（名称/添加时间/年份）与过滤（收藏/最近添加）。
   - 验收：5000+ 曲库滚动流畅；切换排序不卡顿。

**v2.1 验收清单**
- 收藏/歌单自动下载并保持更新。
- 飞行模式完整可用。
- 车机可浏览与播放。
- 音量一致性明显改善。
- 大库分页与排序可用。

---

### P2 — v2.2 “打磨与扩展”（建议 3–4 周）

- **均衡器**（Android `Equalizer`/audio session）与预设。
- **多服务器/多用户**：切换服务器、独立会话与缓存命名空间。
- **文件夹浏览**：按 Jellyfin 目录树浏览。
- **桌面小组件**（Glance）：当前曲目 + 播放/暂停/下一首。
- **平板/横屏分栏**：列表 + 播放器双栏。
- **远程控制/投屏**：Jellyfin Sessions “Play On”；Chromecast（media3 Cast）。
- **播放统计**：最近播放/最常播放（服务器 `UserData` + 播放上报）。
- **逐字歌词**（若服务端提供）；歌词搜索/手动匹配。
- **更多语言**：繁体中文、日语、韩语、德语、法语、西班牙语等。
- **无障碍**：TalkBack 标签、长按操作可发现性、触控区域。
- **质量**：单元测试（收藏/歌单同步、缓存淘汰、播放模式）、Compose UI 测试、基准测试。

---

## 6. 技术方案要点

### 6.1 播放上报（P0）
```http
POST /Sessions/Playing
{
  "ItemId": "...",
  "PlaySessionId": "uuid",
  "PositionTicks": 123456789,
  "IsPaused": false,
  "CanSeek": true,
  "PlayMethod": "DirectPlay",
  "PlaylistIndex": 3,
  "PlaylistLength": 12
}
```
- 每 10 秒 `POST /Sessions/Playing/Progress`，`EventName` 使用 `TimeUpdate/Pause/Unpause/...`。
- 停止时 `POST /Sessions/Playing/Stopped`（带最终 `PositionTicks`）。
- 离线时把最后位置写入本地；联网后仅补报 `Stopped`（避免伪造长时间播放）。

### 6.2 Quick Connect（P0）
- `GET /QuickConnect/Enabled` 检查开关。
- `POST /QuickConnect/Initiate` 返回 `Secret`/`Code`。
- 每 5 秒 `GET /QuickConnect/Connect?Secret=...` 直到 `Authenticated=true`。
- `POST /Users/AuthenticateWithQuickConnect` 用 `Secret` 换 `AccessToken`。

### 6.3 转码与无缝（P0）
- 直连：`/Audio/{id}/stream?static=true&container=flac`。
- 转码：`/Audio/{id}/universal?static=false&audioCodec=aac&maxStreamingBitrate=192000&transcodingContainer=mp3&maxAudioChannels=2&deviceId=...&userId=...`。
- 无缝：保持单一 `setMediaItems` 队列；`DefaultLoadControl` 预加载；避免 `stop()/prepare()` 切歌；对 FLAC/MP3 测试。

### 6.4 自动下载（P1）
- 规则持久化到 `AppSettings`；WorkManager `PeriodicWorkRequest`（如 6 小时）+ 网络恢复触发。
- 通过 `MinDateLastSaved`/`DateCreated` 增量拉取；新曲目入队。
- 下载任务使用前台服务（`dataSync`）与通知；断点续传基于 Range 或重新下载。
- 离线元数据与音频存同一目录，索引记录 `uri`（已具备）。

### 6.5 离线模式（P1）
- `NetworkCallback` + 主动 ping 判定；`offlineMode` 状态。
- 离线时 `JellyfinApi` 短路（返回缓存或本地），UI 显示横幅。
- 本地曲库 = 下载 + 播放缓存 + 本地收藏/歌单。

### 6.6 Android Auto（P1）
- `MediaLibraryService` + `MediaLibrarySession`；`MediaSession.Callback` 处理播放。
- `onGetChildren` 以 Jellyfin API 数据构建 `MediaItem` 树；限制数量（如 100）。
- 搜索走 `onSearch`（AA 无分页）。
- 复用现有 `MusicPlayer.exo`，保证手机/车机同一队列。

### 6.7 ReplayGain（P1）
- `BaseItem` 增加 `normalizationGain: Float?`（解析 `NormalizationGain`）。
- 切歌时 `exo.volume = 10^(gain/20)`；提供开关与曲目/专辑模式；增益 > 0 时做削波保护（限幅）。

---

## 7. 里程碑与排期建议

| 版本 | 主题 | 内容 | 预估 |
|---|---|---|---|
| v2.0 | 可靠流媒体 | 播放上报/续播、Quick Connect、转码质量、无缝、睡眠定时/倍速、签名+CI | 2 周 |
| v2.1 | 离线与车载 | 自动下载/队列、离线模式、Android Auto、ReplayGain、大库分页/排序 | 3 周 |
| v2.2 | 打磨与扩展 | EQ、多服务器、文件夹、Widget、平板、投屏、统计、更多语言、测试 | 3–4 周 |

---

## 8. 风险与取舍

- **Android Auto**：Google 政策与 AA 限制（无分页、列表上限）；需要真车/AA 模拟器测试。
- **转码**：增加服务器 CPU；默认直连，移动网络才转码，可关闭。
- **播放上报**：离线补报可能造成统计偏差；只补报最终位置，不伪造时长。
- **无缝播放**：受编码/容器与网络影响；对不支持 gapless 的格式给出说明。
- **R8/签名**：MediaSessionService 需保留；已有 keep 规则；签名证书需你提供。
- **多服务器**：缓存/下载/同步需按服务器隔离，改动面大，放到 P2。
- **不做**：视频播放（Findroid 领域）、纯本地文件播放器（Finamp 明确拒绝，超出产品定位）。

---

## 9. 本产品可放大的差异化优势

1. **单播放器架构**：天然避免官方客户端 AA/手机双实例错乱。
2. **本地优先收藏/歌单同步**：解决 Finamp 收藏不同步类问题。
3. **按听歌次数淘汰的播放缓存**：比单纯 LRU 更贴合“常听优先”。
4. **令牌不落 URL + Authorization 头**：更安全，便于日志与代理。
5. **下载目录 SAF + 迁移**：比 Finamp 历史下载系统更可控。
6. **全屏同步歌词 + 播放模式 + 队列拖拽**：已达到主流体验。

---

## 10. 下一步建议

优先启动 **P0 / v2.0**：
1) 播放上报与续播；2) Quick Connect；3) 转码质量 + 无缝；4) 睡眠定时/倍速；5) 签名与 CI。

完成后再进入 **v2.1** 的自动下载与 Android Auto。每完成一个版本，使用 `scripts/release.sh <version>` 构建、提交并打标签。
