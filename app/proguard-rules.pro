# 农历库以反射方式读取部分字段，保留其全部类。
-keep class com.nlf.calendar.** { *; }
-dontwarn com.nlf.calendar.**

# 桌面小组件用反射解析 @JvmStatic 提供的静态方法，保留入口类。
-keep class com.lunarcalendar.app.ui.widget.** { *; }
