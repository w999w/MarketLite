package io.github.marketlite;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 商店界面层适配。仅通过明确的资源 ID、容器类型和文案识别目标控件。
 * 不修改页面权限、账号、下载任务或安装逻辑；未知结构保持原状。
 * 每个被改变的 View 都记录原始属性：配置改变、暂停或配置不可用时先还原再应用。
 */
final class PresentationFilter {
    private final MarketLite.LiveSettings settings;
    private boolean[] flags;
    private int startTab;
    private String order;
    private final java.util.Set<View> roots=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private final java.util.Map<View,OriginalView> changedViews=new java.util.WeakHashMap<>();
    private final java.util.Map<View,MineBinding> mineBindings=new java.util.WeakHashMap<>();
    private final java.util.Set<String> observed=new java.util.HashSet<>();
    private final java.util.Set<ViewGroup> bottomVisited=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static final android.util.SparseArray<String> RESOURCE_NAMES=new android.util.SparseArray<>();
    private int detailViewId=-1,pagerViewId=-1;
    private boolean layoutFault;
    private boolean initialTabAttempted;
    PresentationFilter(MarketLite.LiveSettings settings){this.settings=settings;this.flags=settings.flags;this.startTab=settings.startTab;this.order=settings.order;}
    void refresh(){
        if(startTab!=settings.startTab)initialTabAttempted=false;
        flags=settings.flags;startTab=settings.startTab;order=settings.order;
        layoutFault=false;restoreChangedViews();
        for(View grid:new ArrayList<>(mineBindings.keySet()))if(grid!=null)reconcileMineGrid(grid);
        for(View root:new ArrayList<>(roots))if(root!=null&&root.isAttachedToWindow()&&root.getWindowVisibility()==View.VISIBLE)safeScan(root);
    }
    private void restoreChangedViews(){
        for(java.util.Map.Entry<View,OriginalView> entry:new ArrayList<>(changedViews.entrySet()))if(entry.getKey()!=null)try{entry.getValue().restore(entry.getKey());}catch(Exception | LinkageError ignored){}
        changedViews.clear();
    }
    private boolean hasUiRules(){if(startTab!=0||!SettingsPolicy.DEFAULT_ORDER.equals(order))return true;for(int i=ConfigProvider.ORCHARD;i<flags.length;i++)if(flags[i])return true;return false;}
    private void layoutFailure(Throwable error){if(!layoutFault)XposedBridge.log("MarketLite: UI layout rule paused ("+error.getClass().getSimpleName()+")");layoutFault=true;restoreChangedViews();for(View grid:new ArrayList<>(mineBindings.keySet()))if(grid!=null)reconcileMineGrid(grid);}
    private void safeScan(View root){if(layoutFault||!hasUiRules())return;bottomVisited.clear();try{scan(root);}catch(Exception | LinkageError e){layoutFailure(e);}}
    void install(ClassLoader loader){
        installHomeIndicator(loader);
        installHomeTransitions(loader);
        installMineItemFallback(loader);
        XposedHelpers.findAndHookMethod(Activity.class,"onResume",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){
                Activity a=(Activity)p.thisObject;
                if(!"com.xiaomi.market".equals(a.getPackageName()))return;
                settings.refresh();
                View decor=a.getWindow().getDecorView();
                roots.add(decor);
                if(Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(decor,"marketlite.ui"))){safeScan(decor);return;}
                XposedHelpers.setAdditionalInstanceField(decor,"marketlite.ui",true);
                decor.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener(){
                    boolean pending;
                    @Override public void onGlobalLayout(){
                        if(layoutFault||!hasUiRules())return;
                        try{applyFeaturedBeforeDraw(decor);}catch(Exception | LinkageError e){layoutFailure(e);return;}
                        // 合并密集布局事件，同时保证最后一次布局也能扫描，避免固定节流漏掉新控件。
                        if(pending)return;pending=true;decor.postDelayed(()->{pending=false;if(decor.isAttachedToWindow()&&decor.getWindowVisibility()==View.VISIBLE)safeScan(decor);},100);
                    }
                });
                safeScan(decor);
            }
        });
        {
            try {
                Class<?> cls=Class.forName("com.xiaomi.market.business_ui.search.NativeSearchGuideFragment",false,loader);
                try{XposedHelpers.findAndHookMethod(cls,"onViewCreated",View.class,android.os.Bundle.class,new XC_MethodHook(){
                    @Override protected void afterHookedMethod(MethodHookParam p){if(p.args[0] instanceof View){View root=(View)p.args[0];XposedHelpers.setAdditionalInstanceField(root,"marketlite.searchGuide",true);safeScan(root);}}
                });}catch(Exception | LinkageError e){XposedBridge.log("MarketLite: search footer view not matched");}
                for(Method m:cls.getDeclaredMethods()) if(m.getName().equals("parseResponseData") && m.getReturnType()==List.class){
                    XposedBridge.hookMethod(m,new XC_MethodHook(){
                        @Override protected void afterHookedMethod(MethodHookParam p){
                            if(!settings.flags[ConfigProvider.SEARCH_HISTORY_ONLY]||!(p.getResult() instanceof List))return;
                            List<?> result=(List<?>)p.getResult();List<Object> history=new ArrayList<>();
                            for(Object item:result) if(item!=null && item.getClass().getSimpleName().equals("SearchHistoryComponent"))history.add(item);
                            p.setResult(history);
                        }
                    });
                    XposedBridge.log("MarketLite attached: search guide history filter");
                }
            }catch(Exception | LinkageError e){XposedBridge.log("MarketLite: search guide not matched");}
        }
    }
    private boolean homeCustomized(){if(layoutFault)return false;if(!SettingsPolicy.DEFAULT_ORDER.equals(order))return true;for(int i=ConfigProvider.HOME_FIRST;i<ConfigProvider.HOME_FIRST+SettingsPolicy.HOME_COUNT;i++)if(flags[i])return true;return false;}
    private boolean skipLongTransition(Object pager,int target){
        if(!homeCustomized()||!Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(pager,"marketlite.homePager")))return false;
        try{int current=(Integer)XposedHelpers.callMethod(pager,"getCurrentItem");return current!=target&&(Math.abs(current-target)>1||!SettingsPolicy.DEFAULT_ORDER.equals(order));}catch(Exception e){return false;}
    }
    private void installHomeTransitions(ClassLoader loader){
        try{Class<?> pager=Class.forName("androidx.viewpager.widget.OriginalViewPager",false,loader);
            XposedHelpers.findAndHookMethod(pager,"setCurrentItem",int.class,new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){int target=(Integer)p.args[0];if(skipLongTransition(p.thisObject,target)){XposedHelpers.callMethod(p.thisObject,"setCurrentItem",target,false);p.setResult(null);}}});
            XposedHelpers.findAndHookMethod(pager,"setCurrentItem",int.class,boolean.class,new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){if((Boolean)p.args[1]&&skipLongTransition(p.thisObject,(Integer)p.args[0]))p.args[1]=false;}});
            XposedBridge.log("MarketLite attached: compact home transitions");
        }catch(Exception | LinkageError e){XposedBridge.log("MarketLite: home transition not matched");}
    }
    private static final class MineBinding {
        final java.lang.ref.WeakReference<Object> adapter;
        final List<?> original;
        List<?> applied;boolean pending;
        MineBinding(Object adapter,List<?> data){this.adapter=new java.lang.ref.WeakReference<>(adapter);original=new ArrayList<>(data);applied=new ArrayList<>(data);}
    }
    private static int mineIndex(Object item){
        if(item==null)return -1;String type=item.getClass().getSimpleName();if(!type.equals("StandardProductMenuBean")&&!type.equals("MineAppToolsBean")&&!type.equals("MineGridMenuBean"))return -1;
        try{String label=(String)XposedHelpers.callMethod(item,"getName");for(int i=0;i<ConfigProvider.MINE_LABELS.length;i++)if(ConfigProvider.MINE_LABELS[i].equals(label))return i;}catch(Exception ignored){}return -1;
    }
    private List<Object> visibleMineItems(List<?> original){List<Object> result=new ArrayList<>();for(Object item:original){int index=mineIndex(item);if(layoutFault||index<0||!flags[ConfigProvider.MINE_FLAGS[index]])result.add(item);}return result;}
    private boolean anyMineHidden(){if(layoutFault)return false;for(int index:ConfigProvider.MINE_FLAGS)if(flags[index])return true;return false;}
    private boolean allMineHidden(){for(int index:ConfigProvider.MINE_FLAGS)if(!flags[index])return false;return true;}
    private void updateMineGrid(View grid,List<?> visible){if(visible.isEmpty()){if(changeVisibility(grid,View.GONE))XposedBridge.log("MarketLite UI: hidden empty mine shortcuts card");}else{OriginalView state=changedViews.remove(grid);if(state!=null)state.restore(grid);}}
    private void reconcileMineGrid(View grid){
        if(!anyMineHidden()&&!mineBindings.containsKey(grid))return;
        try{
            Object adapter=XposedHelpers.getObjectField(grid,"adapter");if(adapter==null)return;Object raw=XposedHelpers.callMethod(adapter,"getData");if(!(raw instanceof List))return;
            List<?> current=new ArrayList<>((List<?>)raw);MineBinding binding=mineBindings.get(grid);
            if(binding==null||binding.adapter.get()!=adapter||!binding.applied.equals(current)){
                boolean known=false;for(Object item:current)if(mineIndex(item)>=0){known=true;break;}if(!known)return;
                binding=new MineBinding(adapter,current);mineBindings.put(grid,binding);
            }
            List<Object> visible=visibleMineItems(binding.original);updateMineGrid(grid,visible);
            if(current.equals(visible)||binding.pending)return;
            final MineBinding target=binding;target.pending=true;
            grid.post(()->{try{if(mineBindings.get(grid)!=target||XposedHelpers.getObjectField(grid,"adapter")!=adapter)return;List<Object> next=visibleMineItems(target.original);target.applied=new ArrayList<>(next);XposedHelpers.callMethod(adapter,"setDataList",next);updateMineGrid(grid,next);XposedBridge.log("MarketLite UI: mine shortcuts "+target.original.size()+" -> "+next.size());}catch(Exception e){XposedBridge.log("MarketLite: mine shortcut refresh unavailable");}finally{target.pending=false;}});
        }catch(Exception e){if(observed.add("mine adapter unavailable"))XposedBridge.log("MarketLite: mine adapter unavailable");}
    }
    private void installMineItemFallback(ClassLoader loader){
        try{Class<?> view=Class.forName("com.xiaomi.market.common.component.mine_middle.MineMiddleAppManagerItemView",false,loader);Class<?> context=Class.forName("com.xiaomi.market.common.component.base.INativeFragmentContext",false,loader);Class<?> bean=Class.forName("com.xiaomi.market.common.component.componentbeans.BaseNativeBean",false,loader);
            XposedHelpers.findAndHookMethod(view,"onBindData",context,bean,int.class,new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){if(p.thisObject instanceof View)applyMineItem((View)p.thisObject);}});
        }catch(Exception | LinkageError e){XposedBridge.log("MarketLite: mine item fallback unavailable");}
    }
    private void applyMineItem(View v){
        int index=-1;for(int i=0;i<ConfigProvider.MINE_LABELS.length;i++)if(text(v,ConfigProvider.MINE_LABELS[i])){index=i;break;}
        if(layoutFault||index<0||!flags[ConfigProvider.MINE_FLAGS[index]]){OriginalView state=changedViews.remove(v);if(state!=null)state.restore(v);return;}
        ViewGroup.LayoutParams lp=v.getLayoutParams();if(lp==null)return;
        if(lp.width!=0){OriginalView state=remember(v);state.resized=true;state.appliedWidth=0;state.appliedWeight=lp instanceof android.widget.LinearLayout.LayoutParams?((android.widget.LinearLayout.LayoutParams)lp).weight:0;lp.width=0;v.setLayoutParams(lp);}changeVisibility(v,View.GONE);
    }
    private void installHomeIndicator(ClassLoader loader){
        try{
            Class<?> base=Class.forName("com.xiaomi.market.common.component.pager.BasePagerTabContainer",false,loader);
            Class<?> pager=Class.forName("com.xiaomi.market.common.component.pager.PagerTabContainer",false,loader);
            XposedHelpers.findAndHookMethod(pager,"performDraw",int.class,float.class,float.class,int.class,float.class,android.graphics.Paint.class,android.graphics.Canvas.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    if(!(p.thisObject instanceof View)||!inside((View)p.thisObject,"sticky_header_wrapper"))return;
                    float offset=indicatorOffset(p.thisObject,XposedHelpers.getIntField(p.thisObject,"currentPosition"),XposedHelpers.getFloatField(p.thisObject,"currentPositionOffset"));if(offset==0)return;
                    android.graphics.Canvas canvas=(android.graphics.Canvas)p.args[6];p.setObjectExtra("marketlite.indicatorSave",canvas.save());canvas.translate(offset,0);
                }
                @Override protected void afterHookedMethod(MethodHookParam p){Object saved=p.getObjectExtra("marketlite.indicatorSave");if(saved instanceof Integer)((android.graphics.Canvas)p.args[6]).restoreToCount((Integer)saved);}
            });
            XposedHelpers.findAndHookMethod(base,"getIndicatorPos",int.class,float.class,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){if(p.thisObject instanceof View&&inside((View)p.thisObject,"sticky_header_wrapper")&&p.getResult() instanceof android.graphics.Rect){float offset=indicatorOffset(p.thisObject,(Integer)p.args[0],(Float)p.args[1]);if(offset!=0){android.graphics.Rect rect=new android.graphics.Rect((android.graphics.Rect)p.getResult());rect.offset(Math.round(offset),0);p.setResult(rect);}}}
            });
            XposedHelpers.findAndHookMethod(base,"scrollTab",int.class,float.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){if(homeTabsFit(p.thisObject)){View pager=(View)p.thisObject;if(pager.getScrollX()!=0)pager.scrollTo(0,pager.getScrollY());p.setResult(null);}}
            });
            XposedHelpers.findAndHookMethod(android.widget.HorizontalScrollView.class,"scrollTo",int.class,int.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){if(homeTabsFit(p.thisObject))p.args[0]=0;}
            });
            XposedBridge.log("MarketLite attached: home indicator alignment");
        }catch(Exception | LinkageError e){XposedBridge.log("MarketLite: home indicator not matched");}
    }
    private boolean homeTabsFit(Object pager){return homeCustomized()&&pager instanceof View&&Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(pager,"marketlite.homeTabsFit"))&&inside((View)pager,"sticky_header_wrapper");}
    private static float indicatorOffset(Object pager,int index,float fraction){
        try{int count=XposedHelpers.getIntField(pager,"tabCount");if(index<0||index>=count)return 0;
            View current=(View)XposedHelpers.callMethod(pager,"getTabViewAt",index);if(current==null)return 0;float from=current.getTranslationX();
            if(fraction>0&&index+1<count){View next=(View)XposedHelpers.callMethod(pager,"getTabViewAt",index+1);if(next!=null)return from*(1-fraction)+next.getTranslationX()*fraction;}
            return from;
        }catch(Exception e){return 0;}
    }
    /** 只缓存目标进程中的资源名称，不记录用户文本、账号或网络数据。 */
    private static String id(View v){int id=v.getId();if(id==View.NO_ID)return "";String name=RESOURCE_NAMES.get(id);if(name!=null)return name;try{name=v.getResources().getResourceEntryName(id);}catch(Exception e){name="";}RESOURCE_NAMES.put(id,name);return name;}
    private static boolean text(View v,String label){if(v instanceof TextView && label.equals(((TextView)v).getText().toString().trim()))return true;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(text(g.getChildAt(i),label))return true;}return false;}
    private static boolean isolated(View v,String label,String[] others){if(!text(v,label))return false;for(String other:others)if(!other.equals(label)&&text(v,other))return false;return true;}
    private static boolean inside(View v,String container){for(android.view.ViewParent p=v.getParent();p instanceof View;p=p.getParent())if(id((View)p).equals(container))return true;return false;}
    private static boolean insideSearchGuide(View v){for(View current=v;current!=null;current=current.getParent() instanceof View?(View)current.getParent():null)if(Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(current,"marketlite.searchGuide")))return true;return false;}
    private void applyFeaturedBeforeDraw(View decor){
        if(!flags[ConfigProvider.FEATURED])return;
        if(detailViewId<0){detailViewId=decor.getResources().getIdentifier("detail_pager_tab_bg_layout","id","com.xiaomi.market");pagerViewId=decor.getResources().getIdentifier("pager_tab_container","id","com.xiaomi.market");}
        if(detailViewId==0||pagerViewId==0)return;View detail=decor.findViewById(detailViewId);if(detail==null)return;View pager=detail.findViewById(pagerViewId);if(pager instanceof ViewGroup)featuredTab((ViewGroup)pager);
    }
    private void scan(View v){
        String n=id(v);boolean hide=false;
        if(n.equals("pager_tab_container")&&v instanceof ViewGroup){
            ViewGroup group=(ViewGroup)v;
            if(inside(v,"sticky_header_wrapper"))homeTabs(group);
            else if(flags[ConfigProvider.FEATURED]&&inside(v,"detail_pager_tab_bg_layout"))featuredTab(group);
        }
        if(n.equals("tab_container")&&v instanceof ViewGroup&&inside(v,"tab_container_layout"))bottomTabs((ViewGroup)v);
        if(flags[ConfigProvider.ORCHARD]&&(n.equals("top_bar_orchard")||(n.equals("entrance")&&inside(v,"main_search_with_download"))))hide=true;
        if(flags[ConfigProvider.SEARCH_AD]&&n.equals("native_search_top_ad_view")){
            collapseAd(v);
            if(v.getParent() instanceof ViewGroup){
                ViewGroup wrapper=(ViewGroup)v.getParent();
                if(wrapper.getChildCount()==1 && wrapper.getParent() instanceof ViewGroup && ((ViewGroup)wrapper.getParent()).getClass().getName().contains("RecyclerView"))collapseAd(wrapper);
            }
            return;
        }
        if(flags[ConfigProvider.SEARCH_HISTORY_ONLY]&&(n.equals("hot_word_root")||n.equals("hotwords_root")||n.equals("search_related_recommend_root")))hide=true;
        if(flags[ConfigProvider.SEARCH_END]&&v instanceof TextView&&"到底啦".contentEquals(((TextView)v).getText())&&insideSearchGuide(v))hide=true;
        if(n.equals("tab_view_root")&&inside(v,"tab_container_layout")){
            String[] tabs={"首页","短剧","游戏","小游戏","我的"};
            if(v.getParent() instanceof ViewGroup)bottomTabs((ViewGroup)v.getParent());
            if((flags[ConfigProvider.SHORT_DRAMA]&&isolated(v,"短剧",tabs))||(flags[ConfigProvider.MINI_GAMES]&&isolated(v,"小游戏",tabs))||(flags[ConfigProvider.GAMES]&&isolated(v,"游戏",tabs))){
                if(v.getVisibility()==View.VISIBLE&&changeVisibility(v,View.INVISIBLE))XposedBridge.log("MarketLite UI: hidden bottom tab");return;
            }
        }
        if(flags[ConfigProvider.MINE_RECOMMEND]&&n.equals("mine_ad_container"))hide=true;
        if((n.equals("mine_middle_menu_container")||n.equals("mine_standard_product_menu_container"))&&v instanceof ViewGroup){ViewGroup menu=(ViewGroup)v;for(int i=0;i<menu.getChildCount();i++){View grid=menu.getChildAt(i);if(grid.getClass().getSimpleName().equals("MineMiddleGridView"))reconcileMineGrid(grid);}if(n.equals("mine_middle_menu_container")&&allMineHidden()&&menu.getChildCount()==1&&menu.getChildAt(0).getClass().getSimpleName().equals("MineMiddleGridView"))hide=true;}
        if(v.getClass().getSimpleName().equals("MineMiddleAppManagerItemView")&&inside(v,"mine_middle_menu_container"))applyMineItem(v);
        // 只精简重复图案；安全状态文字、安装环境风险提示及处理按钮保持原状。
        if(flags[ConfigProvider.SECURITY_COMPACT]&&n.equals("securityStatusIcon")&&inside(v,"mineSecurityView"))hide=true;
        if(hide){if(changeVisibility(v,View.GONE))XposedBridge.log("MarketLite UI: hidden "+n);return;}
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)scan(g.getChildAt(i));}
    }
    private void collapseAd(View v){
        ViewGroup.LayoutParams lp=v.getLayoutParams();boolean changed=false;
        if(lp!=null){if(lp.height!=0){remember(v).collapsed=true;lp.height=0;changed=true;}if(lp instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)lp;if(m.topMargin!=0||m.bottomMargin!=0){remember(v).collapsed=true;m.topMargin=0;m.bottomMargin=0;changed=true;}}if(changed)v.setLayoutParams(lp);}
        changed|=changeVisibility(v,View.GONE);
        if(changed)XposedBridge.log("MarketLite UI: collapsed search ad "+id(v));
    }
    private OriginalView remember(View v){OriginalView state=changedViews.get(v);if(state==null){state=new OriginalView(v);changedViews.put(v,state);}return state;}
    private boolean changeVisibility(View v,int visibility){if(v.getVisibility()==visibility)return false;OriginalView state=remember(v);state.visibilityChanged=true;state.appliedVisibility=visibility;v.setVisibility(visibility);return true;}
    private void hideTab(View v){if(v.getVisibility()==View.VISIBLE)changeVisibility(v,View.INVISIBLE);}
    private void moveTab(View v,float x){if(v.getTranslationX()!=x){OriginalView state=remember(v);state.translated=true;state.appliedTranslation=x;v.setTranslationX(x);}}
    private void featuredTab(ViewGroup pager){
        ViewGroup group=tabRow(pager);View target=null;List<View> remaining=new ArrayList<>();
        for(int i=0;i<group.getChildCount();i++){View tab=group.getChildAt(i);if(tab instanceof TextView&&text(tab,"精选"))target=tab;else if(tab instanceof TextView&&tab.getVisibility()==View.VISIBLE&&((TextView)tab).getText().length()>0)remaining.add(tab);}
        if(target==null)return;
        if(target.getVisibility()==View.VISIBLE&&changeVisibility(target,View.GONE))XposedBridge.log("MarketLite UI: hidden featured tab only");
        if(!(group instanceof android.widget.LinearLayout)||remaining.size()!=2||pager.getWidth()<=0)return;
        int available=pager.getWidth()-group.getPaddingLeft()-group.getPaddingRight();
        for(int i=0;i<group.getChildCount();i++){
            View child=group.getChildAt(i);if(child==target||child.getVisibility()==View.GONE)continue;
            ViewGroup.LayoutParams lp=child.getLayoutParams();if(lp instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)lp;available-=m.leftMargin+m.rightMargin;}
            if(!remaining.contains(child))available-=child.getWidth();
        }
        if(available<=0)return;
        for(int i=0;i<remaining.size();i++){View tab=remaining.get(i);ViewGroup.LayoutParams raw=tab.getLayoutParams();if(!(raw instanceof android.widget.LinearLayout.LayoutParams))continue;android.widget.LinearLayout.LayoutParams lp=(android.widget.LinearLayout.LayoutParams)raw;int width=available/2+(i==1?available%2:0);
            if(lp.width!=width||lp.weight!=0f){OriginalView state=remember(tab);state.resized=true;state.appliedWidth=width;state.appliedWeight=0;lp.width=width;lp.weight=0;tab.setLayoutParams(lp);}}
    }
    private static ViewGroup tabRow(ViewGroup group){
        while(group.getChildCount()==1&&group.getChildAt(0) instanceof ViewGroup)group=(ViewGroup)group.getChildAt(0);
        return group;
    }
    private void bottomTabs(ViewGroup group){
        if(!flags[ConfigProvider.SHORT_DRAMA]&&!flags[ConfigProvider.MINI_GAMES]&&!flags[ConfigProvider.GAMES])return;
        if(!bottomVisited.add(group))return;
        String[] labels={"首页","短剧","游戏","小游戏","我的"};
        List<View> shown=new ArrayList<>();int matched=0;boolean home=false,mine=false;
        for(int i=0;i<group.getChildCount();i++){
            View tab=group.getChildAt(i);int j=-1;for(int k=0;k<labels.length;k++)if(isolated(tab,labels[k],labels)){j=k;break;}
            if(j<0)continue;matched++;home|=j==0;mine|=j==4;
            boolean hidden=(j==1&&flags[ConfigProvider.SHORT_DRAMA])||(j==2&&flags[ConfigProvider.GAMES])||(j==3&&flags[ConfigProvider.MINI_GAMES]);
            if(hidden)hideTab(tab);else if(tab.getVisibility()==View.VISIBLE&&tab.getWidth()>0)shown.add(tab);
        }
        if(!home||!mine||matched<2||shown.size()<2||group.getWidth()<=0)return;
        float spacing=Math.min(group.getWidth()/(float)shown.size(),120*group.getResources().getDisplayMetrics().density);
        for(int i=0;i<shown.size();i++){View tab=shown.get(i);float center=group.getWidth()/2f+(i-(shown.size()-1)/2f)*spacing;moveTab(tab,center-tab.getLeft()-tab.getWidth()/2f);}
        String key="bottom count="+shown.size();if(observed.add(key))XposedBridge.log("MarketLite UI: compact bottom tabs "+shown.size());
    }
    /** 只恢复模块实际改过且仍是模块设置值的属性，避免覆盖宿主后来更新的状态。 */
    private static final class OriginalView {
        final int visibility,height,width,top,bottom;
        final float weight;
        boolean resized,collapsed,visibilityChanged,translated;
        int appliedWidth,appliedVisibility;float appliedWeight,appliedTranslation;
        final float translationX;
        final ViewGroup.LayoutParams originalParams;
        OriginalView(View v){
            visibility=v.getVisibility();translationX=v.getTranslationX();originalParams=v.getLayoutParams();height=originalParams!=null?originalParams.height:0;width=originalParams!=null?originalParams.width:0;weight=originalParams instanceof android.widget.LinearLayout.LayoutParams?((android.widget.LinearLayout.LayoutParams)originalParams).weight:0;
            if(originalParams instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)originalParams;top=m.topMargin;bottom=m.bottomMargin;}else{top=bottom=0;}
        }
        void restore(View v){
            if(visibilityChanged&&v.getVisibility()==appliedVisibility)v.setVisibility(visibility);
            if(translated&&v.getTranslationX()==appliedTranslation)v.setTranslationX(translationX);
            ViewGroup.LayoutParams lp=v.getLayoutParams();if(lp==originalParams&&lp!=null){boolean changed=false;
                if(collapsed){if(lp.height==0&&height!=0){lp.height=height;changed=true;}if(lp instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)lp;if(m.topMargin==0&&top!=0){m.topMargin=top;changed=true;}if(m.bottomMargin==0&&bottom!=0){m.bottomMargin=bottom;changed=true;}}}
                if(resized){if(lp.width==appliedWidth&&lp.width!=width){lp.width=width;changed=true;}if(lp instanceof android.widget.LinearLayout.LayoutParams){android.widget.LinearLayout.LayoutParams linear=(android.widget.LinearLayout.LayoutParams)lp;if(linear.weight==appliedWeight&&linear.weight!=weight){linear.weight=weight;changed=true;}}}if(changed)v.setLayoutParams(lp);}
        }
    }
    private void homeTabs(ViewGroup group){
        ViewGroup pager=group;
        try{Object nativePager=XposedHelpers.getObjectField(group,"pager");if(nativePager!=null)XposedHelpers.setAdditionalInstanceField(nativePager,"marketlite.homePager",true);}catch(Exception ignored){}
        group=tabRow(group);
        if(group.getChildCount()<2)return;
        boolean custom=homeCustomized();
        if(!custom&&startTab==0){XposedHelpers.setAdditionalInstanceField(pager,"marketlite.homeTabsFit",false);return;}
        View[] tabs=new View[8];int[] positions=new int[8];java.util.Arrays.fill(positions,-1);
        List<View> unknown=new ArrayList<>();
        for(int i=0;i<group.getChildCount();i++){
            View child=group.getChildAt(i);int matched=-1;
            for(int j=0;j<8;j++)if(isolated(child,ConfigProvider.HOME_LABELS[j],ConfigProvider.HOME_LABELS)){matched=j;break;}
            if(matched<0&&text(child,"影音VIP"))matched=3;
            if(matched<0||tabs[matched]!=null)unknown.add(child);else{tabs[matched]=child;positions[matched]=i;}
        }
        if(tabs[3]==null&&positions[2]>=0&&positions[4]==positions[2]+2){View candidate=group.getChildAt(positions[2]+1);if(unknown.remove(candidate))tabs[3]=candidate;}
        if(tabs[3]==null&&group.getChildCount()==15){
            boolean alternating=true;for(int i=0;i<8;i++)if(i!=3&&positions[i]!=2*i)alternating=false;
            if(alternating){View candidate=group.getChildAt(6);if(unknown.remove(candidate))tabs[3]=candidate;}
        }
        if(!SettingsPolicy.normalizeOrder(order).equals(order))return;
        StringBuilder found=new StringBuilder();for(int i=0;i<tabs.length;i++)if(tabs[i]!=null)found.append(ConfigProvider.HOME_LABELS[i]).append(',');
        String detected="home count="+group.getChildCount()+" matched="+found;if(observed.add(detected))XposedBridge.log("MarketLite UI: "+detected);
        // 只在八个已知标签全部识别时进行布局重排；未知结构仅处理已确认的单项。
        boolean allMatched=true;for(View tab:tabs)if(tab==null)allMatched=false;
        if(!allMatched){XposedHelpers.setAdditionalInstanceField(pager,"marketlite.homeTabsFit",false);for(int i=0;i<tabs.length;i++)if(tabs[i]!=null&&flags[ConfigProvider.HOME_FIRST+i])hideTab(tabs[i]);return;}
        int cursor=Integer.MAX_VALUE;Class<?> tabClass=null;
        for(View tab:tabs)if(tab!=null&&tab.getWidth()>0){cursor=Math.min(cursor,tab.getLeft());tabClass=tab.getClass();}
        if(cursor==Integer.MAX_VALUE)return;
        int gap=tabs[0]!=null&&tabs[1]!=null?Math.max(0,tabs[1].getLeft()-tabs[0].getRight()):0;
        for(int i=0;i<8;i++){
            int j=order.charAt(i)-'0';View tab=tabs[j];if(tab==null)continue;
            if(flags[ConfigProvider.HOME_FIRST+j]){hideTab(tab);continue;}
            if(custom&&tab.getVisibility()==View.VISIBLE&&tab.getWidth()>0){moveTab(tab,cursor-tab.getLeft());cursor+=tab.getWidth()+gap;}
            if(!initialTabAttempted&&startTab==j+1&&tab.isShown()&&tab.isClickable()){initialTabAttempted=true;tab.post(tab::performClick);}
        }
        if(custom)for(View tab:unknown)if(tab.getClass()==tabClass&&tab.isClickable()&&tab.getVisibility()==View.VISIBLE&&tab.getWidth()>0){moveTab(tab,cursor-tab.getLeft());cursor+=tab.getWidth()+gap;}
        boolean complete=true;for(View tab:tabs)if(tab==null||tab.getWidth()<=0)complete=false;
        boolean fits=custom&&complete&&pager.getWidth()>0&&group.getLeft()+cursor-gap+pager.getPaddingRight()<=pager.getWidth();
        XposedHelpers.setAdditionalInstanceField(pager,"marketlite.homeTabsFit",fits);
        if(fits&&pager.getScrollX()!=0)pager.scrollTo(0,pager.getScrollY());
    }
}
