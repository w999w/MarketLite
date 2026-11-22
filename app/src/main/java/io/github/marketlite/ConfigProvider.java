package io.github.marketlite;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * 配置桥接：设置仅保存在本应用私有空间；商店进程只能读取显示偏好。
 * KEYS 是跨进程 ABI：老版本按索引读取，新版本按列名读取。新增字段只能追加，
 * 不能重排或删除存量字段。读取错误时由模块端恢复原始表现。
 */
public final class ConfigProvider extends ContentProvider {
    static final String[] KEYS = {"splash", "detail", "download", "update", "orchard", "featured", "search_ad", "search_history_only", "short_drama", "mini_games", "mine_recommend", "mine_install_history", "mine_reservation", "mine_official", "home_recommend", "home_rank", "home_essential", "home_vip", "home_award", "home_video", "home_category", "home_apps", "games", "search_end", "mine_dual_apps", "mine_app_lock", "mine_permissions", "mine_interconnect", "mine_security_compact"};
    static final String[] MINE_LABELS={"安装记录","预约游戏","官方应用","应用双开","应用锁","权限管理","互联升级"};
    static final int[] MINE_FLAGS={11,12,13,24,25,26,27};
    // 命名索引避免规则中的魔法数字；值必须与 KEYS 的位置一致。
    static final int SPLASH=0, DETAIL=1, DOWNLOAD=2, UPDATE=3, ORCHARD=4, FEATURED=5,
            SEARCH_AD=6, SEARCH_HISTORY_ONLY=7, SHORT_DRAMA=8, MINI_GAMES=9, MINE_RECOMMEND=10,
            HOME_FIRST=14, GAMES=22, SEARCH_END=23, SECURITY_COMPACT=28;
    static final String[] HOME_LABELS = {"推荐","榜单","必备","0.01影音VIP","金米奖","看好片","分类","软件"};
    static boolean defaultValue(String key) {
        if(key.startsWith("home_"))return key.equals("home_essential")||key.equals("home_vip")||key.equals("home_award")||key.equals("home_video");
        for(int index:MINE_FLAGS)if(key.equals(KEYS[index]))return false;
        return !key.equals("games")&&!key.equals("search_end")&&!key.equals("mine_security_compact");
    }
    static final String PREFS = "settings";
    static final String PERMISSIONS = "permission_status";
    static final String MARKET_SEEN = "market_seen_at";
    static final Uri FLAGS_URI=Uri.parse("content://io.github.w999w.marketlite.settings/flags");
    private final SharedPreferences.OnSharedPreferenceChangeListener changes=(prefs,key)->getContext().getContentResolver().notifyChange(FLAGS_URI,null);
    @Override public boolean onCreate() { getContext().getSharedPreferences(PREFS,0).registerOnSharedPreferenceChangeListener(changes);return true; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String order) {
        String caller=getCallingPackage();
        if (!"com.xiaomi.market".equals(caller) && !getContext().getPackageName().equals(caller))
            throw new SecurityException("Unknown caller");
        if("com.xiaomi.market".equals(caller))getContext().getSharedPreferences(PERMISSIONS,0).edit().putLong(MARKET_SEEN,System.currentTimeMillis()).apply();
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, 0);
        // 0.4.1 起移除暂停功能；旧版本遗留的暂停标志不再影响保存的配置。
        String[] columns=java.util.Arrays.copyOf(KEYS,KEYS.length+2);columns[KEYS.length]="home_start";columns[KEYS.length+1]="home_order";
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] row = new Object[KEYS.length+2];
        for (int i=0; i<KEYS.length; i++) row[i] = prefs.getBoolean(KEYS[i],defaultValue(KEYS[i])) ? 1 : 0;
        row[KEYS.length]=SettingsPolicy.normalizeStart(prefs.getInt("home_start",0));
        row[KEYS.length+1]=SettingsPolicy.normalizeOrder(prefs.getString("home_order",SettingsPolicy.DEFAULT_ORDER));
        cursor.addRow(row);
        return cursor;
    }
    @Override public String getType(Uri u) { return "vnd.android.cursor.item/vnd.marketlite.settings"; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
}
