Jellyfin 音乐播放器 —— 构建说明
================================

项目：io.github.inwins.jellymusic
路径：项目根目录
技术栈：Kotlin + Jetpack Compose + Media3(ExoPlayer)
compileSdk 34 / minSdk 26 / targetSdk 34


一、环境要求
------------
1. JDK 17
   验证：java -version
2. Gradle 8.7
   Gradle 8.7（命令中的 gradle 可用本机安装路径）
   验证：gradle --version
3. Android SDK
   本机 Android SDK 根目录（ANDROID_HOME）
   需要：platforms;android-34、build-tools、platform-tools(adb)
   local.properties 中已配置：
       sdk.dir=/path/to/Android/Sdk
   若换机器，请重新创建 local.properties 并写入自己的 SDK 路径。
4. 设备调试
   adb 位于 adb
   测试机：Android 8+ 设备（开启 USB 调试；root 可选）


二、构建 Debug APK
------------------
在项目根目录执行：

    gradle assembleDebug --console=plain

产物：

    app/build/outputs/apk/debug/app-debug.apk   （约 22 MB）

提示：
- 如果 gradlew 可用，也可以 ./gradlew assembleDebug；
- 本机网络受限时优先使用 gradle，避免 wrapper 下载失败。


三、运行单元测试
----------------
    gradle testDebugUnitTest --console=plain

或使用脚本：

    GRADLE=gradle ./scripts/test.sh

测试包含 Robolectric + Compose UI 测试。
报告：app/build/reports/tests/testDebugUnitTest/index.html


四、构建 Release APK
--------------------
    gradle assembleRelease --console=plain

产物：

    app/build/outputs/apk/release/app-release-unsigned.apk   （约 3.1 MB）

说明：
- release 开启 R8（minify + shrinkResources）；
- 未配置签名时输出为 unsigned，不能直接覆盖安装 debug 版；
- R8 构建较慢（本机约 10–15 分钟），内存不足可在 gradle.properties 调大：
      org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g

配置正式签名（可选）：
在项目根目录创建 keystore.properties（不要提交到 git）：

    storeFile=release.jks
    storePassword=你的密码
    keyAlias=你的别名
    keyPassword=你的密码

再次执行 assembleRelease，会输出已签名的 app-release.apk。
注意：签名必须与已安装版本一致，否则 Android 会拒绝覆盖安装。


五、安装到设备
--------------
1. 确认设备：

    adb devices

2. 直接安装（data 保留）：

    adb install -r app/build/outputs/apk/debug/app-debug.apk

3. 本机 MIUI 推荐方式（adb install 偶发失败时）：

    ADB=adb
    $ADB push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/jellymusic.apk
    $ADB shell "su -c 'pm install -r /data/local/tmp/jellymusic.apk'"

4. 启动：

    $ADB shell am start -n io.github.inwins.jellymusic/.MainActivity

重要：
- 使用 -r 覆盖安装可保留登录状态和下载数据；
- 不要 uninstall / 清除数据。卸载会删除 /sdcard/Android/data/io.github.inwins.jellymusic
  （下载索引与默认下载文件都在里面）；
- debug 版与 release 版签名不同，不能互相覆盖，切换时需要卸载（会丢数据）。


六、发布流程（版本号 + 提交 + tag）
----------------------------------
1. 修改版本号：app/build.gradle.kts
       versionCode = 58          // 每次 +1
       versionName = "2.26.8"

2. 写更新日志：
       app/src/main/res/raw/changelog.txt       （英文）
       app/src/main/res/raw-zh/changelog.txt    （中文）

3. 执行发布脚本（会 assembleDebug、自动 commit、打 tag）：

       GRADLE=gradle ./scripts/release.sh 2.26.9

   脚本不会构建 release APK，只构建 debug 用于安装验证。

4. 安装验证：

       adb install -r app/build/outputs/apk/debug/app-debug.apk


七、常用命令速查
----------------
清理：

    gradle clean

编译 + 单测：

    gradle assembleDebug testDebugUnitTest --console=plain

查看日志：

    adb logcat | grep -i jellymusic

强制停止：

    adb shell am force-stop io.github.inwins.jellymusic


八、数据位置与注意事项
----------------------
- 默认下载目录：/sdcard/Android/data/io.github.inwins.jellymusic/files/audio/
  文件命名：歌手 - 歌名 [ItemId].扩展名
  索引：同目录 index.json
- 自定义下载目录：设置 → 下载 → 选择文件夹（SAF 目录）
  索引仍保存在 Android/data 下的 index.json
- 设置 → 下载 → 重命名下载文件：可把旧的“ItemId.ext”文件名批量改成可读名称
- MIUI 的“清理存储/手机管家”可能清理 Android/data，导致下载列表丢失；
  重要数据建议备份，或把下载目录选到公共 Music 文件夹。
- 换签名/卸载重装会丢失 Android/data 下的索引；登录信息可能被系统备份恢复，
  但下载文件不会，表现为“登录还在、下载没了”。


九、目录结构
------------
app/src/main/java/io/github/inwins/jellymusic/   源码
  ui/             Compose 界面
  player/         播放器、可视化、节拍器、音频效果
  data/           数据存储（下载、设置、缓存、分析等）
  jellyfin/       Jellyfin API 与模型
app/src/main/res/         资源与中英文文案
app/src/test/             单元测试 / Robolectric / Compose 测试
app/src/androidTest/      仪器化测试
scripts/release.sh        发布脚本
scripts/test.sh           单元测试脚本
.github/workflows/        CI
