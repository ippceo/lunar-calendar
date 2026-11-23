# 农历日历（Lunar Calendar）

一个完全离线的 Android 日历 App：查看公历 + 农历、创建日程、阳历/农历生日提醒。

提醒使用 **Android 系统本地闹钟（AlarmManager）+ 本地通知**，不接 FCM、不接任何推送服务，也不申请网络权限。适合收不到 Google 推送的设备（外销机 / 无 GMS 设备）使用。

## 功能

- 月视图日历，每天显示农历日期、节气、传统节日（春节、中秋、端午等）
- 点击日期查看当天农历详情与日程
- 新建一次性日程
- 新建每年重复的阳历生日 / 农历生日（闰月自动按平月回退）
- 到点发本地通知，支持开关、编辑、删除
- 重启手机、修改系统时间/时区、App 更新后自动重新排期
- 提醒可靠性检查：通知权限、精确闹钟、忽略电池优化

## 下载 APK

推送到 GitHub 后，Actions 会自动构建 debug APK：

1. 打开仓库的 **Actions** 页面
2. 选择 **Build APK** 工作流最近一次运行
3. 在页面底部 **Artifacts** 下载 `lunar-calendar-debug`
4. 解压得到 `app-debug.apk`，传入手机安装（需允许“安装未知来源应用”）

也可以手动触发：Actions → Build APK → Run workflow。

## 安装后建议（重要）

首次打开 App 会请求通知权限。然后进入「提醒」页，把下面三项都设为“已允许”：

1. **通知权限**：否则到点不会弹通知
2. **精确闹钟**：Android 12+ 需要，保证准点触发（本 App 声明了日历类应用可用的 `USE_EXACT_ALARM`）
3. **忽略电池优化**：避免系统在后台冻结 App 的闹钟

国产 ROM / 部分外销机还需要在系统设置里：

- 允许 App「自启动」/「后台运行」
- 电池策略设为「不限制」/「允许后台活动」

注意：在系统设置里对 App 执行「强行停止」会清除所有已排期的闹钟，需要重新打开一次 App。

## 技术实现

- Kotlin + Jetpack Compose（Material 3）
- 农历、节气、节日：[lunar-java](https://github.com/6tail/lunar-java)（`cn.6tail:lunar`，纯 Java，离线计算）
- 数据存储：SharedPreferences + JSON（无需数据库）
- 提醒：`AlarmManager.setExactAndAllowWhileIdle` + `BroadcastReceiver` + 通知渠道
- 无网络权限、无第三方统计、无后台常驻服务

## 本地构建

需要 JDK 17 和 Android SDK（compileSdk 35）：

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 项目结构

```
app/src/main/java/com/lunarcalendar/app/
├── MainActivity.kt        # 入口、底部导航、状态管理
├── CalendarScreen.kt      # 月历网格 + 当天详情
├── ReminderScreen.kt      # 提醒列表 + 权限检查
├── EventEditorDialog.kt   # 新建/编辑日程与生日
├── Models.kt              # 事件数据模型 + JSON 序列化
├── LunarUtils.kt          # 农历/节气/节日查询、农历转阳历
├── Occurrences.kt         # 重复规则、下一次触发时间
├── EventRepository.kt     # 本地存储
├── ReminderScheduler.kt   # AlarmManager 排期
├── Notifications.kt       # 本地通知
└── Receivers.kt           # 到点提醒 / 开机重排
```

## 许可

MIT
