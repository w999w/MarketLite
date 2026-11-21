package io.github.marketlite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.widget.Toast;

/** 本应用自身的错误报告。不会读取其他应用日志、账号、设备序列号或上传任何数据。 */
final class AppErrors {
    private static final String PREFS="local_errors",PENDING="pending_crash";
    private static boolean installed;

    private AppErrors() {}

    /** 只由 MainActivity 安装；绝不能在注入的商店进程中替换宿主异常处理器。 */
    static synchronized void install(Context context) {
        if(installed)return;installed=true;
        Context app=context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{app.getSharedPreferences(PREFS,0).edit().putString(PENDING,format(app,"应用异常退出",error,null)).commit();}catch(Exception ignored){}
            if(previous!=null)previous.uncaughtException(thread,error);else android.os.Process.killProcess(android.os.Process.myPid());
        });
    }

    static String takePending(Context context) {
        android.content.SharedPreferences prefs=context.getSharedPreferences(PREFS,0);
        String report=prefs.getString(PENDING,null);if(report!=null)prefs.edit().remove(PENDING).apply();return report;
    }

    /** 不复制任意异常消息或路径；仅保留错误类型、版本、有限堆栈与固定命令的脱敏输出。 */
    private static String format(Context context,String operation,Throwable error,String detail) {
        String version="unknown";try{version=context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName;}catch(Exception ignored){}
        StringBuilder b=new StringBuilder("商店轻享 ").append(version).append("\nAndroid ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n操作：").append(operation);
        if(error!=null){b.append("\n错误类型：").append(error.getClass().getName());StackTraceElement[] stack=error.getStackTrace();for(int i=0;i<Math.min(12,stack.length);i++)b.append("\n  at ").append(stack[i]);}
        if(detail!=null)b.append("\n").append(detail.replaceAll("(?i)(token|password|secret|api[_-]?key)\\s*[=:]\\s*\\S+","$1=<REDACTED>"));
        return b.toString();
    }

    static void show(Activity activity,String operation,Throwable error,String detail){show(activity,format(activity,operation,error,detail));}
    static void show(Activity activity,String report){
        if(activity.isFinishing()||activity.isDestroyed())return;
        new AlertDialog.Builder(activity).setTitle("错误信息").setMessage(report).setNegativeButton("关闭",null).setPositiveButton("复制错误信息",(d,w)->{
            ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("MarketLite error",report));Toast.makeText(activity,"错误信息已复制",Toast.LENGTH_SHORT).show();}
        }).show();
    }
}
