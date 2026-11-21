package io.github.marketlite;

import java.lang.reflect.Method;
import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 模块入口。只在小米应用商店主进程中加载，不 Hook 其他应用或系统进程。
 * Hook 不修改下载请求、安装验证、账号、设备标识或安全检查；只控制展示。
 */
public final class MarketLite implements IXposedHookLoadPackage {
    private static final String PACKAGE = "com.xiaomi.market";

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!PACKAGE.equals(p.packageName) || !PACKAGE.equals(p.processName)) return;
        final java.util.concurrent.atomic.AtomicBoolean attached=new java.util.concurrent.atomic.AtomicBoolean();
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Context ctx = (Context) param.args[0];
                if (!PACKAGE.equals(ctx.getPackageName())||!attached.compareAndSet(false,true)) return;
                LiveSettings settings=new LiveSettings(ctx);
                settings.refresh();
                // 仅对已知展示方法、预期返回类型生效。商店小版本改签名时跳过该规则。
                suppress(settings,ConfigProvider.SPLASH,p.classLoader,boolean.class,false,"com.xiaomi.market.ui.splash.SplashManager","canShowSplash","needShowSplash");
                suppress(settings,ConfigProvider.SPLASH,p.classLoader,void.class,null,"com.xiaomi.market.ui.splash.SplashManager","trySplashWhenApplicationForeground");
                suppress(settings,ConfigProvider.DETAIL,p.classLoader,boolean.class,false,"com.xiaomi.market.common.network.retrofit.response.bean.AppDetailV3","showRecommend","showTopBanner","showTopVideo","needShowAds","getShowOpenScreenAd");
                installDownloadRecommendations(settings,p.classLoader);
                suppress(settings,ConfigProvider.UPDATE,p.classLoader,void.class,null,"com.xiaomi.market.ui.UpdateListRvAdapter","generateRecommendGroupItems");
                PresentationFilter filter=new PresentationFilter(settings);
                settings.onChange=filter::refresh;
                try{filter.install(p.classLoader);}catch(Exception | LinkageError e){XposedBridge.log("MarketLite: presentation initialization skipped ("+e.getClass().getSimpleName()+")");}
                settings.observe();
            }
        });
    }

    /** 下载推荐使用独立展示模型，复制为空推荐列表，不返回 null、不修改下载任务。 */
    private static void installDownloadRecommendations(LiveSettings settings,ClassLoader loader){
        try{
            Class<?> fragment=Class.forName("com.xiaomi.market.ui.DownloadListFragment",false,loader);
            Class<?> result=Class.forName("com.xiaomi.market.viewmodels.RecommendGroupResult",false,loader);
            Method parse=fragment.getDeclaredMethod("parseRecommendGroupResult",org.json.JSONObject.class);
            Method copy=result.getMethod("copy",java.util.ArrayList.class);
            if(parse.getReturnType()!=result||copy.getReturnType()!=result)throw new NoSuchMethodException("recommendation model changed");
            XposedBridge.hookMethod(parse,new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){
                if(!settings.flags[ConfigProvider.DOWNLOAD]||!result.isInstance(p.getResult()))return;
                try{p.setResult(copy.invoke(p.getResult(),new java.util.ArrayList<>()));}catch(Exception | LinkageError e){XposedBridge.log("MarketLite: download recommendation unchanged");}
            }});
            XposedBridge.log("MarketLite attached: download recommendation model");
        }catch(Exception | LinkageError e){XposedBridge.log("MarketLite unsupported: download suggestion model");}
    }

    private static void suppress(LiveSettings settings,int flag,ClassLoader loader,Class<?> expected,Object value,String type,String... names) {
        try {
            int matches=0;
            for (Method method : Class.forName(type, false, loader).getDeclaredMethods()) {
                if (!java.util.Arrays.asList(names).contains(method.getName())||method.getReturnType()!=expected) continue;
                try {
                    XposedBridge.hookMethod(method,new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){if(settings.flags[flag])p.setResult(value);}});
                    matches++;
                    XposedBridge.log("MarketLite attached: " + type + "#" + method.getName());
                }
                catch (Exception | LinkageError e) { XposedBridge.log("MarketLite skipped: " + method.getName()); }
            }
            if(matches==0)XposedBridge.log("MarketLite unsupported: "+ConfigProvider.KEYS[flag]+" (no matching signature)");
        } catch (ClassNotFoundException | LinkageError e) { XposedBridge.log("MarketLite unsupported: " + type); }
    }

    /** 设置快照。读不到配置信息时全部关闭；不会保留可能已卸载模块的旧配置。 */
    static final class LiveSettings {
        final Context context;
        volatile boolean[] flags=new boolean[ConfigProvider.KEYS.length];
        int startTab;
        String order=SettingsPolicy.DEFAULT_ORDER;
        Runnable onChange;
        final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable pendingRefresh=this::refresh;
        private boolean unavailable;
        LiveSettings(Context context){this.context=context;}
        void refresh(){
            try(Cursor c=context.getContentResolver().query(ConfigProvider.FLAGS_URI,null,null,null,null)){
                if(c==null||!c.moveToFirst()){failOpen();return;}
                // 按列名读取，旧版设置缺少新开关时默认不修改，不错位读取其他字段。
                boolean[] next=new boolean[flags.length];for(int i=0;i<next.length;i++){int col=c.getColumnIndex(ConfigProvider.KEYS[i]);next[i]=col>=0&&c.getInt(col)!=0;}
                int startCol=c.getColumnIndex("home_start"),orderCol=c.getColumnIndex("home_order");
                unavailable=false;
                apply(next,SettingsPolicy.normalizeStart(startCol>=0?c.getInt(startCol):0),SettingsPolicy.normalizeOrder(orderCol>=0?c.getString(orderCol):null));
            }catch(Exception | LinkageError e){failOpen();}
        }
        private void failOpen(){
            if(!unavailable)XposedBridge.log("MarketLite: settings unavailable; restoring native presentation");
            unavailable=true;apply(new boolean[flags.length],0,SettingsPolicy.DEFAULT_ORDER);
        }
        private void apply(boolean[] next,int start,String nextOrder){
            if(java.util.Arrays.equals(flags,next)&&startTab==start&&java.util.Objects.equals(order,nextOrder))return;
            flags=next;startTab=start;order=nextOrder;
            if(onChange!=null)try{onChange.run();}catch(Exception | LinkageError e){XposedBridge.log("MarketLite: presentation refresh skipped ("+e.getClass().getSimpleName()+")");}
            XposedBridge.log("MarketLite: live settings refreshed");
        }
        void observe(){
            try{context.getContentResolver().registerContentObserver(ConfigProvider.FLAGS_URI,false,new android.database.ContentObserver(handler){
                // 批量按钮一次改多个配置，只合并成一次跨进程读取与界面刷新。
                @Override public void onChange(boolean selfChange){handler.removeCallbacks(pendingRefresh);handler.postDelayed(pendingRefresh,75);}
            });}catch(Exception e){XposedBridge.log("MarketLite: live observer unavailable");}
        }
    }

}
