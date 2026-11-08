# app_process 与 UserService 通过类名查找入口，不允许裁剪。
-keep class rikka.shizuku.server.ShizukuService { public static void main(java.lang.String[]); }
-keep class moe.shizuku.starter.ServiceStarter { public static void main(java.lang.String[]); }
-keep class moe.shizuku.manager.adb.PairingContext { *; }
# JNI_OnLoad 无条件注册两个类，无 Java 引用的终端类也不能裁剪。
-keep class rikka.rish.RishHost { *; }
-keep class rikka.rish.RishTerminal { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
