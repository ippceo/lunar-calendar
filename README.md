# 拾光（Lunar Calendar）

一个完全离线的 Android 日历 App：查看公历 + 农历、创建日程、阳历/农历生日与纪念日提醒、管理待办，并提供桌面小组件。

提醒使用 **Android 系统本地闹钟（AlarmManager）+ 本地通知**，不接 FCM、不接任何推送服务，也不申请网络权限。适合收不到 Google 推送的设备（外销机 / 无 GMS 设备）使用。

## 功能

### 日历

- 月视图 / 年视图切换；年视图一屏展示全年 12 个月，可点月份进入
- 一周从**周一**开始，周末高亮
- 可选择是否显示相邻月份的日期（默认隐藏，月初月末留白）
- 年、月可拨盘快速跳转，支持 1900–2100
- 每天显示农历日期、节气、传统节日（春节、中秋、端午等）
- 点击日期查看当天农历详情与日程

### 提醒

- 历史提醒列表支持「日程 / 生日 / 纪念日」左右滑动切换
- 新建类型统一为下拉菜单：日程、阳历生日、农历生日、纪念日
- 农历生日可选出生年份（提醒仍按每年农历同一天）
- 纪念日每年提醒，并显示从原始日期累计「已经 X 天」
- 支持**提前 1~7 天提醒**，可添加多条并分别设置时间
- 支持开关、编辑、删除；过期日程置底并显示删除线
- 到点发本地通知；重启手机、改时间/时区、App 更新后自动重新排期

### 待办

- 独立「待办」分页，支持增删改、勾选完成
- 可选日期时间并提醒，也可作为无期限清单
- 未完成在前、已完成置底并加删除线
- 随数据导出/导入一起备份

### 数据与桌面

- 数据导出 / 导入（JSON 文件，换机迁移，含日程、生日、纪念日、待办）
- 桌面 **2×2 事件小组件**：可选日程 / 生日 / 纪念日；日程与生日支持自动或手动指定，24 小时内按小时倒计时，生日显示年龄，纪念日可切换「已经 / 还有」为主显示
- 桌面 **待办小组件**：支持 2×2 / 2×4 / 4×4，可直接在桌面勾选完成
- 提醒可靠性检查：通知权限、精确闹钟、忽略电池优化

## 下载 APK

正式版通过 GitHub Releases 发布：

1. 打开 [Releases](https://github.com/ippceo/lunar-calendar/releases) 页面
2. 下载最新版本中的 `lunar-calendar-vX.Y.Z.apk`
3. 传入手机安装（需允许“安装未知来源应用”）

所有 Release 使用同一签名，后续版本可直接覆盖安装、保留数据。

`main` 分支的每次提交也会触发 Actions 构建 debug 包，仅供开发调试，不建议日常使用。

## 安装后建议（重要）

首次打开 App 会请求通知权限。然后进入「提醒」页，把下面三项都设为“已允许”：

1. **通知权限**：否则到点不会弹通知
2. **精确闹钟**：Android 12+ 需要，保证准点触发（本 App 声明了日历类应用可用的 `USE_EXACT_ALARM`）
3. **忽略电池优化**：避免系统在后台冻结 App 的闹钟

国产 ROM / 部分外销机还需要在系统设置里：

- 允许 App「自启动」/「后台运行」
- 电池策略设为「不限制」/「允许后台活动」

注意：在系统设置里对 App 执行「强行停止」会清除所有已排期的闹钟，需要重新打开一次 App。

## 签名与覆盖升级

Release 包使用**固定的 release 签名**（CI 从 GitHub Secrets 读取 keystore），因此后续版本可以**直接覆盖安装、保留数据**，无需先卸载。

- 首次从旧的 debug 包切换到 release 签名包时，仍需卸载一次（两者签名不同）。
- 请始终下载 GitHub Releases 里的 `lunar-calendar-vX.Y.Z.apk`，不要用 Actions 里的 debug 包。
- CI 需要的 Secrets：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。

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
├── MainActivity.kt        # 入口、底部导航、状态管理（旋转屏幕保留状态）
├── CalendarScreen.kt      # 月视图 / 年视图、年月拨盘、当天详情
├── ReminderScreen.kt      # 提醒列表（日程/生日/纪念日/待办）+ 权限检查
├── SettingsScreen.kt      # 提醒可靠性检查 + 数据备份
├── EventEditorDialog.kt   # 新建/编辑日程、生日、纪念日（含提前提醒）
├── TodoEditorDialog.kt    # 新建/编辑待办
├── Models.kt              # 事件数据模型 + JSON 序列化
├── Todos.kt               # 待办数据模型、排序、存储
├── LunarUtils.kt          # 农历/节气/节日查询、农历转阳历
├── Occurrences.kt         # 重复规则、下一次触发时间、纪念日累计天数
├── EventRepository.kt     # 本地存储
├── ReminderScheduler.kt   # 事件闹钟排期
├── TodoScheduler.kt       # 待办闹钟排期
├── Notifications.kt       # 本地通知
├── Receivers.kt           # 到点提醒 / 开机重排
├── DataBackup.kt          # 导出 / 导入 JSON 备份
├── AppSettings.kt         # 从最近任务隐藏等设置
└── ui/widget/
    ├── LunarWidgetProvider.kt        # 桌面事件小组件
    ├── LunarWidgetConfigActivity.kt  # 事件小组件配置页
    └── TodoWidgetProvider.kt         # 桌面待办小组件
```

## 桌面小组件

**事件小组件（2×2）**

长按桌面空白处 → 添加小组件 → 选择「拾光」的 2×2 组件，添加后点一下卡片进入配置页：

- 显示内容：日程 / 生日 / 纪念日
- 日程与生日可选「自动」（取最近一条）或「手动选择」
- 剩余不足 24 小时时按小时倒计时；生日卡片额外显示年龄
- 纪念日样式为「标题已经 / 大号天数 / ─── 天 ─── / 原始日期」，并可切换主显示为「还有 X 天」

**待办小组件**

选择「拾光 · 待办」，可拖动调整尺寸：

- 2×2 约显示 2 条，2×4 约 4 条，4×4 最多 6 条
- 每行可直接勾选完成，点标题打开 App
- 表头显示未完成数量，全部完成或为空时显示空状态

## 许可

MIT
