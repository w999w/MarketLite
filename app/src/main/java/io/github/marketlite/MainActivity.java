package io.github.marketlite;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** 单独的配置应用：页面、权限检测、用户主动的系统工具操作均在本进程中执行。 */
public final class MainActivity extends Activity {
    private static final int INK=0xff172529, MUTED=0xff697778, ACCENT=0xff007b72, BG=0xfff2f6f5;
    private LinearLayout content;
    private SharedPreferences prefs,permissions;
    private static final String VECTOR="org.matrix.vector.manager",KERNELSU="me.weishu.kernelsu";
    private static final String PROJECT_URL=GithubUpdates.PROJECT;
    private final java.util.List<LinearLayout> moduleCards=new java.util.ArrayList<>();
    private TextView vectorStatus,rootStatus,moduleNotice,rootNotice;
    private LinearLayout rootActions;
    private long installedAt,lastRootCheck;
    private int rootState=-1;
    private boolean probingRoot,checkingUpdate;
    private final SharedPreferences.OnSharedPreferenceChangeListener permissionChanges=(p,k)->{if(ConfigProvider.MARKET_SEEN.equals(k))runOnUiThread(this::refreshPermissions);};
    private TextView quickStatus;
    private PopupWindow locationPopup;
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle state){super.onCreate(state);AppErrors.install(this);prefs=getSharedPreferences(ConfigProvider.PREFS,0);permissions=getSharedPreferences(ConfigProvider.PERMISSIONS,0);try{installedAt=getPackageManager().getPackageInfo(getPackageName(),0).lastUpdateTime;}catch(Exception ignored){}permissions.registerOnSharedPreferenceChangeListener(permissionChanges);show(0);String crash=AppErrors.takePending(this);if(crash!=null)AppErrors.show(this,crash);else if(!permissions.getBoolean("intro_seen",false)){permissions.edit().putBoolean("intro_seen",true).apply();permissionGuide();}}
    @Override protected void onResume(){super.onResume();refreshQuickStatus();refreshPermissions();try{getContentResolver().notifyChange(ConfigProvider.FLAGS_URI,null);}catch(Exception e){AppErrors.show(this,"通知模块刷新",e,null);}if(permissions.getBoolean("auto_root_check",false)&&android.os.SystemClock.elapsedRealtime()-lastRootCheck>3000)checkRoot(false);}
    @Override protected void onPause(){if(locationPopup!=null)locationPopup.dismiss();super.onPause();}
    @Override protected void onDestroy(){if(permissions!=null)permissions.unregisterOnSharedPreferenceChangeListener(permissionChanges);super.onDestroy();}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private GradientDrawable round(int color,int radius){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(radius));return b;}
    private boolean installed(String packageName){try{getPackageManager().getPackageInfo(packageName,0);return true;}catch(Exception e){return false;}}
    private boolean moduleReady(){return permissions!=null&&installed(VECTOR)&&permissions.getLong(ConfigProvider.MARKET_SEEN,0)>0&&permissions.getLong(ConfigProvider.MARKET_SEEN,0)>=installedAt;}
    private void openManager(String pkg){try{Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);if(intent!=null)startActivity(intent);else Toast.makeText(this,"未检测到管理器，请先安装。",Toast.LENGTH_LONG).show();}catch(Exception e){AppErrors.show(this,"打开权限管理器",e,null);}}
    private void permissionGuide(){
        new android.app.AlertDialog.Builder(this).setTitle("权限与使用准备").setMessage("必要 · Vector\n模块 → 商店轻享 → 启用 → 使用模块建议的范围，仅勾选小米应用商店。重新打开应用商店，再返回本应用验证连接。\n\n可选 · KernelSU\n超级用户 → 商店轻享 → 允许。仅终止应用商店及卸载／恢复快应用框架需要 Root。点击检查 Root 时可能出现授权提示。\n\n无需相册、通讯录、无障碍等权限。").setPositiveButton("打开 Vector",(d,w)->openManager(VECTOR)).setNeutralButton("打开 KernelSU",(d,w)->openManager(KERNELSU)).setNegativeButton("知道了",null).show();
    }
    private void setEnabledTree(View v,boolean enabled){v.setEnabled(enabled);if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)setEnabledTree(g.getChildAt(i),enabled);}}
    private void gate(View view,boolean allowed){if(view==null)return;view.setAlpha(allowed?1f:.43f);setEnabledTree(view,allowed);}
    private void refreshPermissions(){
        if(permissions==null)return;boolean connected=moduleReady();boolean manager=installed(VECTOR),kernel=installed(KERNELSU);
        if(vectorStatus!=null){vectorStatus.setText(!manager?"未检测到 Vector 管理器":connected?"已验证 · 商店模块最近连接成功":"尚未验证 · 启用模块及建议作用域后打开商店");vectorStatus.setTextColor(connected?ACCENT:MUTED);}
        if(rootStatus!=null){rootStatus.setText(probingRoot?"正在检查 Root…":rootState==1?"已验证 · Root 权限正常":rootState==0?"未获 Root 授权或检查失败":!kernel?"未检测到 KernelSU · Root 未验证":"尚未验证 · 请检查 Root 权限");rootStatus.setTextColor(rootState==1?ACCENT:MUTED);}
        if(moduleNotice!=null){moduleNotice.setText(!manager?"缺少 Vector：配置暂不可用。":"尚未验证商店模块连接，配置暂不可用。请到首页检查。");moduleNotice.setVisibility(connected?View.GONE:View.VISIBLE);}
        for(LinearLayout card:moduleCards)gate(card,connected);
        if(rootNotice!=null){rootNotice.setText(rootState==0?"Root 未授权：请在首页检查。":"Root 未验证：请在首页检查。");rootNotice.setVisibility(rootState==1?View.GONE:View.VISIBLE);}
        gate(rootActions,rootState==1&&!probingRoot);
    }
    private interface RootFinished{void complete(RootCommands.Result result,Throwable failure);}
    private void executeRoot(RootCommands.Operation operation,RootFinished callback){new Thread(()->{RootCommands.Result value=null;Throwable failure=null;try{value=RootCommands.run(operation);}catch(Exception | LinkageError e){failure=e;}final RootCommands.Result result=value;final Throwable error=failure;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())callback.complete(result,error);});},"marketlite-root-command").start();}
    private void checkRoot(boolean manual){
        if(probingRoot)return;probingRoot=true;lastRootCheck=android.os.SystemClock.elapsedRealtime();refreshPermissions();
        executeRoot(RootCommands.Operation.CHECK,(result,error)->{rootState=result!=null&&result.isRoot()?1:result!=null&&result.timedOut?-1:0;probingRoot=false;permissions.edit().putBoolean("auto_root_check",rootState==1).apply();refreshPermissions();if(manual&&rootState!=1)AppErrors.show(this,"检查 Root 权限",error,result==null?null:result.details(RootCommands.Operation.CHECK));});
    }
    private void heading(String s){TextView t=text(s,15,ACCENT,true);t.setPadding(dp(6),dp(20),0,dp(8));content.addView(t);}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(1);c.setPadding(dp(18),dp(16),dp(18),dp(16));c.setBackground(round(Color.WHITE,18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);content.addView(c,lp);return c;}
    private void row(LinearLayout c,String title,String note){c.addView(text(title,17,INK,true));TextView v=text(note,13,MUTED,false);v.setPadding(0,dp(5),0,dp(12));c.addView(v);}
    private void toggle(LinearLayout c,String label,String note,String key){Switch s=new Switch(this);s.setText(label);s.setTextColor(INK);s.setTextSize(16);s.setPadding(0,dp(8),0,dp(5));s.setChecked(prefs.getBoolean(key,ConfigProvider.defaultValue(key)));s.setOnCheckedChangeListener((b,on)->prefs.edit().putBoolean(key,on).apply());c.addView(s,new LinearLayout.LayoutParams(-1,-2));description(c,note,location(key));}
    private Switch visibleToggle(LinearLayout c,String label,String key){Switch s=new Switch(this);s.setText("显示"+label);s.setTextColor(INK);s.setTextSize(16);s.setPadding(0,dp(8),0,dp(5));s.setChecked(!prefs.getBoolean(key,ConfigProvider.defaultValue(key)));s.setOnCheckedChangeListener((b,on)->prefs.edit().putBoolean(key,!on).apply());c.addView(s,new LinearLayout.LayoutParams(-1,-2));description(c,"开启保留入口，关闭隐藏入口。",location(key));return s;}
    private LinearLayout functionBlock(LinearLayout parent){LinearLayout block=new LinearLayout(this);block.setOrientation(1);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(5);p.bottomMargin=dp(5);parent.addView(block,p);return block;}
    private void functionHeader(LinearLayout c,String title,TextView action){
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setMinimumHeight(dp(32));top.addView(text(title,16,INK,false),new LinearLayout.LayoutParams(0,-2,1));if(action!=null)top.addView(action);c.addView(top,new LinearLayout.LayoutParams(-1,-2));
    }
    private void mineShortcuts(LinearLayout c){
        Switch[] switches=new Switch[ConfigProvider.MINE_LABELS.length];
        TextView all=text("全部关闭",14,ACCENT,true);all.setPadding(dp(8),dp(4),dp(8),dp(4));all.setBackground(round(0xffd9ebe7,7));functionHeader(c,"快捷入口",all);
        Runnable refresh=()->{boolean shown=false;for(Switch s:switches)if(s!=null&&s.isChecked())shown=true;all.setText(shown?"全部关闭":"全部保留");};
        for(int i=0;i<switches.length;i++){String key=ConfigProvider.KEYS[ConfigProvider.MINE_FLAGS[i]];Switch s=visibleToggle(c,ConfigProvider.MINE_LABELS[i],key);switches[i]=s;s.setOnCheckedChangeListener((b,on)->{prefs.edit().putBoolean(key,!on).apply();refresh.run();});}
        all.setOnClickListener(v->{boolean any=false;for(Switch s:switches)if(s.isChecked())any=true;SharedPreferences.Editor edit=prefs.edit();for(int index:ConfigProvider.MINE_FLAGS)edit.putBoolean(ConfigProvider.KEYS[index],any);edit.apply();for(Switch s:switches)s.setChecked(!any);refresh.run();});refresh.run();
    }
    private String location(String key){switch(key){
        case "splash":return "打开应用商店 → 开屏";
        case "detail":return "首页或搜索结果 → 应用详情 → 广告与推荐";
        case "download":return "首页 → 下载管理 → 推荐";
        case "update":return "我的 → 应用升级 → 推荐";
        case "orchard":return "首页 → 左上角 → 种果树";
        case "featured":return "应用详情 → 精选按钮";
        case "search_ad":return "首页 → 搜索栏 → 搜索结果 → 顶部广告";
        case "search_history_only":return "首页 → 搜索栏 → 搜索引导页";
        case "search_end":return "首页 → 搜索栏 → 搜索引导页 → 底部";
        case "short_drama":return "首页 → 底部导航 → 短剧";
        case "mini_games":return "首页 → 底部导航 → 小游戏";
        case "games":return "首页 → 底部导航 → 游戏";
        case "mine_recommend":return "我的 → 页面底部 → 推荐";
        case "mine_install_history":return "我的 → 安装记录";
        case "mine_reservation":return "我的 → 预约游戏";
        case "mine_official":return "我的 → 官方应用";
        case "mine_dual_apps":return "我的 → 快捷入口 → 应用双开";
        case "mine_app_lock":return "我的 → 快捷入口 → 应用锁";
        case "mine_permissions":return "我的 → 快捷入口 → 权限管理";
        case "mine_interconnect":return "我的 → 快捷入口 → 互联升级";
        case "mine_security_compact":return "我的 → 应用安全检测 → 状态展示";
        default:return "首页 → 搜索栏下方 → 标签";
    }}
    private void description(LinearLayout c,String note,String path){
        TextView v=text("",13,MUTED,false);v.setPadding(0,0,0,dp(10));
        android.text.SpannableString body=new android.text.SpannableString(note+"  !");int end=body.length();
        body.setSpan(new android.text.style.ReplacementSpan(){
            @Override public int getSize(android.graphics.Paint paint,CharSequence value,int start,int end,android.graphics.Paint.FontMetricsInt fm){if(fm!=null){fm.top=Math.min(fm.top,-dp(19));fm.ascent=Math.min(fm.ascent,-dp(19));fm.descent=Math.max(fm.descent,dp(7));fm.bottom=Math.max(fm.bottom,dp(7));}return dp(34);}
            @Override public void draw(android.graphics.Canvas canvas,CharSequence value,int start,int end,float x,int top,int y,int bottom,android.graphics.Paint source){
                android.graphics.Paint paint=new android.graphics.Paint(source);paint.setAntiAlias(true);float cx=x+dp(15),cy=y+(source.ascent()+source.descent())/2f;
                paint.setColor(ACCENT);canvas.drawCircle(cx,cy,dp(9),paint);paint.setColor(Color.WHITE);paint.setTextSize(dp(13));paint.setTextAlign(android.graphics.Paint.Align.CENTER);paint.setTypeface(Typeface.DEFAULT_BOLD);canvas.drawText("!",cx,cy-(paint.ascent()+paint.descent())/2f,paint);
            }
        },end-1,end,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        body.setSpan(new android.text.style.ClickableSpan(){
            @Override public void onClick(View widget){showLocation(widget,path);}
            @Override public void updateDrawState(android.text.TextPaint ds){ds.setColor(ACCENT);ds.setFakeBoldText(true);ds.setUnderlineText(false);}
        },end-1,end,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        v.setText(body);v.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());v.setHighlightColor(Color.TRANSPARENT);c.addView(v);
    }
    private void showLocation(View anchor,String path){
        if(locationPopup!=null)locationPopup.dismiss();
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(16),dp(12),dp(16),dp(14));box.setBackground(round(0xffe1f2ee,14));
        TextView title=text("功能位置",13,ACCENT,true);box.addView(title);TextView route=text(path,15,INK,false);route.setPadding(0,dp(5),0,0);box.addView(route);
        PopupWindow popup=new PopupWindow(box,Math.min(dp(300),getResources().getDisplayMetrics().widthPixels-dp(32)),-2,true);
        popup.setBackgroundDrawable(round(0xffe1f2ee,14));popup.setOutsideTouchable(true);popup.setElevation(dp(8));popup.setTouchInterceptor((view,event)->{if(event.getAction()==android.view.MotionEvent.ACTION_DOWN||event.getAction()==android.view.MotionEvent.ACTION_OUTSIDE)popup.dismiss();return true;});
        popup.setOnDismissListener(()->{if(locationPopup==popup)locationPopup=null;});locationPopup=popup;popup.showAsDropDown(anchor,0,dp(2));
    }
    private void link(LinearLayout c,String label,String url){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))));c.addView(b);}
    private void action(LinearLayout c,String label,boolean primary,Runnable run){TextView b=text(label,15,primary?Color.WHITE:ACCENT,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(12),dp(13),dp(12),dp(13));b.setBackground(round(primary?ACCENT:0xffe1f2ee,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);c.addView(b,lp);b.setOnClickListener(v->{try{run.run();}catch(Exception | LinkageError error){AppErrors.show(this,label,error,null);}});}
    private void section(LinearLayout c,String label){TextView t=text(label,15,ACCENT,true);t.setPadding(0,dp(7),0,dp(6));c.addView(t);}
    private void groupHeading(String title,String subtitle){LinearLayout group=new LinearLayout(this);group.setOrientation(1);group.setPadding(dp(4),dp(20),dp(4),dp(10));LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);View marker=new View(this);marker.setBackground(round(ACCENT,3));line.addView(marker,new LinearLayout.LayoutParams(dp(5),dp(23)));TextView label=text(title,22,INK,true);label.setPadding(dp(10),0,0,0);line.addView(label);group.addView(line);TextView note=text(subtitle,13,MUTED,false);note.setPadding(dp(15),dp(5),0,0);group.addView(note);content.addView(group);}
    private LinearLayout pageCard(String page){LinearLayout c=card();TextView title=text(page,18,ACCENT,true);title.setPadding(dp(10),dp(7),dp(10),dp(7));title.setBackground(round(0xffedf5f3,9));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(5);c.addView(title,p);if(!"系统工具".equals(page))moduleCards.add(c);return c;}
    private void hero(LinearLayout c,String title,String subtitle){LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);ImageView icon=new ImageView(this);icon.setImageResource(getResources().getIdentifier("ic_launcher","drawable",getPackageName()));line.addView(icon,new LinearLayout.LayoutParams(dp(66),dp(66)));LinearLayout words=new LinearLayout(this);words.setOrientation(1);words.setPadding(dp(14),0,0,0);words.addView(text(title,23,INK,true));TextView sub=text(subtitle,13,MUTED,false);sub.setPadding(0,dp(4),0,0);words.addView(sub);line.addView(words,new LinearLayout.LayoutParams(0,-2,1));c.addView(line);}
    private void openMarket(){try{Intent i=getPackageManager().getLaunchIntentForPackage("com.xiaomi.market");if(i!=null)startActivity(i);else Toast.makeText(this,"未检测到小米应用商店",Toast.LENGTH_LONG).show();}catch(Exception e){AppErrors.show(this,"打开应用商店",e,null);}}
    private void openUrl(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){AppErrors.show(this,"打开网页",e,null);}}
    private void stopMarket(){if(rootState!=1||probingRoot){Toast.makeText(this,"请先在首页检查 Root 权限",Toast.LENGTH_LONG).show();return;}executeRoot(RootCommands.Operation.STOP_MARKET,(result,error)->{if(result!=null&&result.success())Toast.makeText(this,"已终止应用商店",Toast.LENGTH_LONG).show();else{rootState=-1;refreshPermissions();AppErrors.show(this,"终止应用商店",error,result==null?null:result.details(RootCommands.Operation.STOP_MARKET));}});}
    private void menu(View anchor){PopupMenu p=new PopupMenu(this,anchor);p.getMenu().add("终止应用商店进程").setEnabled(rootState==1&&!probingRoot);p.getMenu().add("打开应用商店");p.getMenu().add("权限检查");p.setOnMenuItemClickListener(item->{if(item.getTitle().equals("打开应用商店"))openMarket();else if(item.getTitle().equals("权限检查"))show(0);else stopMarket();return true;});p.show();}
    private void orderEditor(LinearLayout c){
        char[] ordered=SettingsPolicy.normalizeOrder(prefs.getString("home_order",SettingsPolicy.DEFAULT_ORDER)).toCharArray();
        LinearLayout list=new LinearLayout(this);list.setOrientation(1);c.addView(list);
        Runnable refresh=new Runnable(){public void run(){
            list.removeAllViews();
            for(int k=0;k<ordered.length;k++){
                final int index=k, tab=ordered[k]-'0';
                LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(52));
                TextView grip=text("⠛",26,MUTED,true);grip.setGravity(Gravity.CENTER);grip.setContentDescription("长按拖动 "+ConfigProvider.HOME_LABELS[tab]);
                row.addView(grip,new LinearLayout.LayoutParams(dp(42),dp(52)));
                TextView label=text(ConfigProvider.HOME_LABELS[tab],16,INK,false);label.setPadding(dp(10),0,0,0);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
                Switch visible=new Switch(MainActivity.this);String key=ConfigProvider.KEYS[14+tab];visible.setContentDescription("显示 "+ConfigProvider.HOME_LABELS[tab]);visible.setChecked(!prefs.getBoolean(key,ConfigProvider.defaultValue(key)));visible.setOnCheckedChangeListener((b,on)->prefs.edit().putBoolean(key,!on).apply());row.addView(visible,new LinearLayout.LayoutParams(-2,-2));
                final float[] touchX={dp(21)};
                grip.setOnTouchListener((v,event)->{if(event.getAction()==android.view.MotionEvent.ACTION_DOWN)touchX[0]=event.getX();return false;});
                grip.setOnLongClickListener(v->row.startDragAndDrop(android.content.ClipData.newPlainText("tab",""),new View.DragShadowBuilder(label){
                    private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    private int width,height,gap;
                    @Override public void onProvideShadowMetrics(android.graphics.Point size,android.graphics.Point touch){
                        gap=Math.max(0,Math.round(label.getLeft()-grip.getLeft()+label.getPaddingLeft()-touchX[0])-dp(8));
                        paint.setTextSize(label.getTextSize());width=gap+(int)Math.ceil(paint.measureText(label.getText().toString()))+dp(20);height=dp(44);
                        size.set(width,height);touch.set(0,height/2);
                    }
                    @Override public void onDrawShadow(android.graphics.Canvas canvas){
                        paint.setColor(0xffd9efeb);canvas.drawRoundRect(gap,0,width,height,dp(12),dp(12),paint);
                        paint.setColor(INK);canvas.drawText(label.getText().toString(),gap+dp(8),height/2f-(paint.ascent()+paint.descent())/2f,paint);
                    }
                },index,0));
                row.setOnDragListener((v,event)->{
                    switch(event.getAction()){
                        case android.view.DragEvent.ACTION_DRAG_STARTED:return event.getLocalState() instanceof Integer;
                        case android.view.DragEvent.ACTION_DRAG_ENTERED:row.setBackgroundColor(0xffd9efeb);return true;
                        case android.view.DragEvent.ACTION_DRAG_EXITED:case android.view.DragEvent.ACTION_DRAG_ENDED:row.setBackgroundColor(Color.TRANSPARENT);return true;
                        case android.view.DragEvent.ACTION_DROP:
                            int from=(Integer)event.getLocalState();if(from==index)return true;
                            char moving=ordered[from];if(from<index)System.arraycopy(ordered,from+1,ordered,from,index-from);else System.arraycopy(ordered,index,ordered,index+1,from-index);
                            ordered[index]=moving;prefs.edit().putString("home_order",new String(ordered)).apply();list.post(this);return true;
                        default:return true;
                    }
                });list.addView(row);
            }
        }};refresh.run();
    }
    private enum QuickState{ENABLED("已安装 · 已启用"),DISABLED("已安装 · 已停用"),UNINSTALLED("已卸载（当前用户）"),MISSING("未检测到组件"),UNKNOWN("无法读取状态");final String label;QuickState(String label){this.label=label;}}
    private QuickState quickState(){
        try{android.content.pm.ApplicationInfo info=getPackageManager().getApplicationInfo(RootCommands.QUICK_PACKAGE,android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES|android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS);
            return (info.flags&android.content.pm.ApplicationInfo.FLAG_INSTALLED)==0?QuickState.UNINSTALLED:info.enabled?QuickState.ENABLED:QuickState.DISABLED;
        }catch(android.content.pm.PackageManager.NameNotFoundException e){return QuickState.MISSING;}catch(Exception e){return QuickState.UNKNOWN;}
    }
    private void refreshQuickStatus(){if(quickStatus!=null)quickStatus.setText("当前状态："+quickState().label);}
    private void quickAction(boolean restore){
        if(rootState!=1||probingRoot){Toast.makeText(this,"请先在首页检查 Root 权限",Toast.LENGTH_LONG).show();return;}
        RootCommands.Operation op=restore?RootCommands.Operation.RESTORE_QUICK:RootCommands.Operation.UNINSTALL_QUICK;
        new android.app.AlertDialog.Builder(this).setTitle(restore?"恢复快应用框架":"卸载快应用框架").setMessage(restore?"恢复系统预装组件。":"仅为当前主用户卸载快应用框架，保留系统镜像中的原始组件。此操作独立于模块，卸载商店轻享不会自动恢复。需要 Root 授权。").setNegativeButton("取消",null).setPositiveButton("确认",(d,w)->executeRoot(op,(result,error)->{
            QuickState state=quickState();refreshQuickStatus();boolean verified=restore?(state==QuickState.ENABLED||state==QuickState.DISABLED):state==QuickState.UNINSTALLED;
            if(result!=null&&result.success()&&verified)Toast.makeText(this,restore?"已恢复快应用框架":"已卸载主用户的快应用框架",Toast.LENGTH_LONG).show();
            else AppErrors.show(this,restore?"恢复快应用框架":"卸载快应用框架",error,(result==null?"":result.details(op)+"\n")+"实际状态："+state.label);
        })).show();
    }
    private void commandText(LinearLayout c,String value){TextView command=text(value,13,INK,false);command.setTypeface(Typeface.MONOSPACE);command.setTextIsSelectable(true);command.setPadding(dp(10),dp(10),dp(10),dp(10));command.setBackground(round(0xffedf5f3,8));c.addView(command,new LinearLayout.LayoutParams(-1,-2));}
    private String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "unknown";}}
    private void checkUpdates(){
        if(checkingUpdate)return;checkingUpdate=true;
        Toast.makeText(this,"正在检查 GitHub Releases…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                GithubUpdates.Release release=GithubUpdates.latest();String current=appVersion();
                runOnUiThread(()->{
                    checkingUpdate=false;if(isFinishing()||isDestroyed())return;
                    if(release==null){new android.app.AlertDialog.Builder(this).setTitle("检查更新").setMessage("未找到已发布的安装包。").setPositiveButton("查看 Releases",(d,w)->openUrl(GithubUpdates.RELEASES)).setNegativeButton("关闭",null).show();return;}
                    boolean newer=ReleaseVersion.valid(current)&&ReleaseVersion.compare(release.tag,current)>0;
                    String notes=release.notes.length()>2000?release.notes.substring(0,2000)+"…":release.notes;
                    android.app.AlertDialog.Builder dialog=new android.app.AlertDialog.Builder(this).setTitle(newer?"发现新版本":"当前无可用更新").setMessage("当前："+current+"\n发布："+release.tag+(newer&&!notes.isEmpty()?"\n\n"+notes:""));
                    if(newer)dialog.setPositiveButton("下载 APK",(d,w)->openUrl(release.apk)).setNeutralButton("发布详情",(d,w)->openUrl(release.page));else dialog.setPositiveButton("查看 Releases",(d,w)->openUrl(GithubUpdates.RELEASES));
                    dialog.setNegativeButton("关闭",null).show();
                });
            }catch(Exception e){runOnUiThread(()->{checkingUpdate=false;if(!isFinishing()&&!isDestroyed())AppErrors.show(this,"检查 GitHub 更新",e,e instanceof java.io.IOException&&e.getMessage()!=null&&e.getMessage().matches("GitHub HTTP \\d{3}")?e.getMessage():"请检查网络后重试，也可在关于页打开 Releases。");});}
        },"marketlite-release-check").start();
    }
    private void show(int page){
        if(locationPopup!=null)locationPopup.dismiss();
        quickStatus=null;vectorStatus=rootStatus=moduleNotice=rootNotice=null;rootActions=null;moduleCards.clear();
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(Build.VERSION.SDK_INT>=30) root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(1);content.setPadding(dp(20),dp(22),dp(20),dp(20));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView title=text(new String[]{"商店轻享","配置","关于","快应用帮助"}[page],30,INK,true);title.setPadding(dp(4),0,0,dp(15));header.addView(title,new LinearLayout.LayoutParams(0,-2,1));if(page<2){TextView more=text("⋮",30,INK,true);more.setContentDescription("更多操作");more.setGravity(Gravity.CENTER);header.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));more.setOnClickListener(v->menu(more));}content.addView(header);
        if(page==0){
            LinearLayout c=card();c.setBackground(round(0xffe1f2ee,20));hero(c,"更清爽的商店","MarketLite · "+appVersion());
            int count=0;for(int i:new int[]{0,1,2,3,6,7,10})if(prefs.getBoolean(ConfigProvider.KEYS[i],ConfigProvider.defaultValue(ConfigProvider.KEYS[i])))count++;
            TextView summary=text("已开启 "+count+" 项广告去除设置",16,ACCENT,true);summary.setPadding(0,dp(18),0,dp(4));c.addView(summary);
            c.addView(text("配置保存后自动刷新 · 部分内容需重新进入页面",13,MUTED,false));
            action(c,"打开应用商店",true,this::openMarket);action(c,"前往配置",false,()->show(1));
            action(c,"检查更新",false,this::checkUpdates);
            heading("权限检查");c=card();functionHeader(c,"Vector · 必需",null);vectorStatus=text("正在检查模块连接…",14,MUTED,false);c.addView(vectorStatus);action(c,"打开 Vector 授权模块",false,()->openManager(VECTOR));action(c,"验证模块连接（打开商店）",false,()->{permissions.edit().remove(ConfigProvider.MARKET_SEEN).apply();openMarket();});
            functionHeader(c,"KernelSU · 系统工具需要",null);rootStatus=text("尚未验证 Root",14,MUTED,false);c.addView(rootStatus);action(c,"打开 KernelSU 授权 Root",false,()->openManager(KERNELSU));action(c,"检查 Root 权限",false,()->checkRoot(true));action(c,"查看权限授权说明",false,this::permissionGuide);
            heading("设备信息");c=card();row(c,"当前设备",Build.MODEL+" · Android "+Build.VERSION.RELEASE);String ver="未安装";try{ver=getPackageManager().getPackageInfo("com.xiaomi.market",0).versionName;}catch(Exception ignored){}row(c,"应用商店版本",ver);row(c,"模块作用域","仅小米应用商店 · 请在 Vector 中确认启用");
        }
        if(page==1){
            moduleNotice=text("尚未验证商店模块连接，配置暂不可用。",14,ACCENT,true);moduleNotice.setPadding(dp(12),dp(12),dp(12),dp(12));moduleNotice.setBackground(round(0xffe1f2ee,12));moduleNotice.setOnClickListener(v->show(0));content.addView(moduleNotice);
            groupHeading("去广告","开启开关即可去除对应页面中的广告与推荐");
            LinearLayout c=pageCard("开屏");toggle(c,"去除开屏广告","包括切回前台的开屏展示。","splash");
            c=pageCard("应用详情");toggle(c,"去除详情页广告与推荐","控制详情页广告、横幅、视频及推荐。","detail");
            c=pageCard("搜索");toggle(c,"去除搜索结果广告","隐藏顶部广告容器。","search_ad");toggle(c,"去除搜索引导推荐（实验）","仅保留搜索历史及未匹配内容。","search_history_only");
            c=pageCard("下载管理");toggle(c,"去除下载页推荐（实验）","仅处理已识别的下载推荐展示模型。","download");
            c=pageCard("我的");toggle(c,"去除升级页推荐","仅处理应用升级的推荐分组。","update");toggle(c,"去除底部推荐","隐藏我的页面广告容器。","mine_recommend");
            groupHeading("功能开关","按照所在页面管理入口与展示方式");
            c=pageCard("首页");toggle(c,"去除种果树","隐藏首页活动入口。","orchard");c=functionBlock(c);functionHeader(c,"标签管理（实验）",null);c=functionBlock(c);c.setPadding(dp(12),0,0,0);c.addView(text("排序与显示",14,MUTED,true));description(c,"长按拖动排序，开关控制显示。","首页 → 搜索栏下方 → 标签栏");orderEditor(c);TextView label=text("默认进入标签",14,MUTED,true);label.setPadding(0,dp(12),0,dp(5));c.addView(label);description(c,"选择打开商店时优先进入的标签。","首页 → 搜索栏下方 → 默认标签");Spinner spinner=new Spinner(this);String[] options=new String[ConfigProvider.HOME_LABELS.length+1];options[0]="商店默认";System.arraycopy(ConfigProvider.HOME_LABELS,0,options,1,ConfigProvider.HOME_LABELS.length);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);spinner.setAdapter(adapter);spinner.setSelection(Math.min(options.length-1,Math.max(0,prefs.getInt("home_start",0))));spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> parent,View v,int position,long id){prefs.edit().putInt("home_start",position).apply();}public void onNothingSelected(android.widget.AdapterView<?> parent){}});c.addView(spinner);
            c=pageCard("搜索");toggle(c,"去除到底啦","隐藏搜索引导页底部提示，默认保留。","search_end");
            c=pageCard("应用详情");toggle(c,"去除精选按钮","仅隐藏精选标签，保留详情和评论内容。","featured");
            c=pageCard("底部导航");toggle(c,"去除短剧","隐藏短剧入口。","short_drama");toggle(c,"去除小游戏","隐藏小游戏入口。","mini_games");toggle(c,"去除游戏","隐藏普通游戏入口，默认保留。","games");
            c=pageCard("我的");mineShortcuts(functionBlock(c));LinearLayout status=functionBlock(c);functionHeader(status,"安全状态",null);toggle(status,"精简应用安全检测显示","去掉重复圆形图案；保留检测结果、风险提示与处理入口。","mine_security_compact");
            c=pageCard("系统工具");c=functionBlock(c);functionHeader(c,"快应用框架",null);description(c,"需要 KernelSU 授权；仅卸载主用户的组件，可恢复。","系统应用 → 快应用框架");quickStatus=text("当前状态：检查中",15,ACCENT,true);quickStatus.setPadding(0,0,0,dp(8));c.addView(quickStatus);refreshQuickStatus();action(c,"刷新状态",false,this::refreshQuickStatus);action(c,"查看命令与恢复方法",false,()->show(3));rootNotice=text("Root 未验证：请在首页检查。",13,ACCENT,true);rootNotice.setPadding(0,dp(8),0,dp(4));rootNotice.setOnClickListener(v->show(0));c.addView(rootNotice);rootActions=functionBlock(c);action(rootActions,"卸载快应用框架",false,()->quickAction(false));action(rootActions,"恢复快应用框架",false,()->quickAction(true));
            TextView hint=text("配置自动保存并刷新；部分内容需再次进入对应页面。",13,MUTED,false);hint.setPadding(dp(6),dp(8),dp(6),dp(8));content.addView(hint);
        }
        if(page==2){LinearLayout c=card();hero(c,"商店轻享","MarketLite · "+appVersion());TextView intro=text("小米应用商店的界面自定义模块。",15,MUTED,false);intro.setPadding(0,dp(16),0,dp(4));c.addView(intro);row(c,"开发说明","由 ChatGPT 编写，与用户共同实机测试。不同应用商店版本可能仅支持部分功能。");heading("卸载与恢复");c=card();row(c,"可恢复的界面设置","停用 Vector 中的模块或卸载商店轻享，再强行停止并重新打开商店，可恢复原有显示。所有配置仅保存在本应用中；卸载后配置记录会清除。");row(c,"不会随卸载自动恢复","快应用框架的主用户卸载属于独立系统操作，卸载本应用不会重新安装框架。请先恢复快应用，或使用恢复命令。卸载本应用也不会取消手机 Root。");action(c,"查看快应用恢复说明",false,()->show(3));heading("开源信息");c=card();row(c,"开源许可","GPL-3.0-or-later · 免费");action(c,"查看许可证",false,()->openUrl("https://www.gnu.org/licenses/gpl-3.0.html"));heading("项目与致谢");c=card();row(c,"MarketLite","项目源码、安装包及恢复文档已在 GitHub 免费公开。");action(c,"打开 GitHub 项目",false,()->openUrl(PROJECT_URL));action(c,"GitHub Releases",false,()->openUrl(GithubUpdates.RELEASES));action(c,"检查更新",false,this::checkUpdates);action(c,"查看参考项目",false,()->openUrl("https://github.com/callng/NewFuckMarketAds"));}
        if(page==3){LinearLayout c=card();row(c,"适用范围","仅对 Android 主用户 0 的系统预装快应用框架操作，包名 com.miui.hybrid；不删除系统分区。若本机没有该预装包，请勿强刷其他机型组件。");row(c,"卸载命令","点击卸载并确认后执行：");commandText(c,"su -c \""+RootCommands.Operation.UNINSTALL_QUICK.command+"\"");row(c,"恢复命令","点击恢复并确认后执行：");commandText(c,"su -c \""+RootCommands.Operation.RESTORE_QUICK.command+"\"");row(c,"本应用无法打开时","先停用 Vector 模块，再重新打开商店。若快应用框架此前已卸载，可通过已授权的 ADB 检查并恢复：");commandText(c,"adb shell pm list packages -u --user 0 com.miui.hybrid\nadb shell cmd package install-existing --user 0 com.miui.hybrid");row(c,"恢复注意事项","若 ADB 提示权限不足，请在已授权的 Root 终端执行上方恢复命令。恢复要求官方系统仍包含原始组件；命令失败时保留错误信息，不尝试刷写分区。恢复后刷新状态。卸载本应用不会自动恢复快应用框架。");action(c,"GitHub 恢复文档",false,()->openUrl(PROJECT_URL+"/blob/main/docs/quick-app-recovery.md"));action(c,"返回配置",true,()->show(1));}
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(dp(10),dp(8),dp(10),dp(8));nav.setBackgroundColor(Color.WHITE);String[] labels={"首页","配置","关于"};for(int i=0;i<3;i++){final int n=i;TextView v=text(labels[i],15,i==page?ACCENT:MUTED,i==page);v.setGravity(Gravity.CENTER);v.setPadding(0,dp(13),0,dp(13));if(i==page)v.setBackground(round(0xffd9efeb,22));v.setOnClickListener(w->show(n));nav.addView(v,new LinearLayout.LayoutParams(0,-2,1));}root.addView(nav);setContentView(root);refreshPermissions();
    }
}
