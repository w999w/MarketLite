package io.github.marketlite;

import org.json.JSONArray;
import org.json.JSONObject;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

/** Read published GitHub releases, including alpha versions. No token, background polling or automatic installation. */
final class GithubUpdates {
    static final String PROJECT="https://github.com/w999w/MarketLite";
    static final String RELEASES=PROJECT+"/releases";
    private static final String API="https://api.github.com/repos/w999w/MarketLite/releases?per_page=30";
    private static final String FEED=RELEASES+".atom";
    static final class Release {
        final String tag,notes,apk,page;
        Release(String tag,String notes,String apk,String page){this.tag=tag;this.notes=notes;this.apk=apk;this.page=page;}
    }
    static Release latest() throws Exception {
        HttpsURLConnection connection=(HttpsURLConnection)new URL(API).openConnection();
        connection.setConnectTimeout(10000);connection.setReadTimeout(12000);
        connection.setRequestProperty("Accept","application/vnd.github+json");connection.setRequestProperty("User-Agent","MarketLite-Android");
        connection.setInstanceFollowRedirects(false);
        try{
            int status=connection.getResponseCode();
            // GitHub also publishes a public release feed; it remains useful when API quota is exhausted.
            if(status==403||status==429)return fromFeed();
            if(status!=200)throw new IOException("GitHub HTTP "+status);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream stream=connection.getInputStream()){
                byte[] buf=new byte[8192];int n;while((n=stream.read(buf))!=-1){if(bytes.size()+n>2*1024*1024)throw new IOException("Release response too large");bytes.write(buf,0,n);}
            }
            return choose(new JSONArray(bytes.toString("UTF-8")));
        }finally{connection.disconnect();}
    }
    private static Release fromFeed() throws Exception {
        HttpsURLConnection connection=(HttpsURLConnection)new URL(FEED).openConnection();
        connection.setConnectTimeout(10000);connection.setReadTimeout(12000);connection.setInstanceFollowRedirects(false);
        try{
            int status=connection.getResponseCode();if(status!=200)throw new IOException("GitHub HTTP "+status);
            XmlPullParser parser=Xml.newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);
            String tag=null;String prefix=RELEASES+"/tag/";
            try(InputStream input=connection.getInputStream()){
                parser.setInput(input,"UTF-8");
                for(int event=parser.getEventType();event!=XmlPullParser.END_DOCUMENT;event=parser.next()){
                    if(event!=XmlPullParser.START_TAG||!"link".equals(parser.getName()))continue;
                    String url=parser.getAttributeValue(null,"href");if(url==null||!url.startsWith(prefix))continue;
                    String candidate=url.substring(prefix.length());
                    if(ReleaseVersion.valid(candidate)&&(tag==null||ReleaseVersion.compare(candidate,tag)>0))tag=candidate;
                }
            }
            // The feed does not enumerate binary assets. Open the verified official release page,
            // so the user can inspect and choose the attachment instead of assuming it exists.
            return tag==null?null:new Release(tag,"请在发布详情查看更新说明并下载 APK。",null,prefix+tag);
        }finally{connection.disconnect();}
    }
    static Release choose(JSONArray releases) throws Exception {
        Release best=null;
        for(int i=0;i<releases.length();i++){
            JSONObject item=releases.getJSONObject(i);String tag=item.optString("tag_name");
            if(item.optBoolean("draft")||!ReleaseVersion.valid(tag))continue;
            JSONArray assets=item.optJSONArray("assets");if(assets==null)continue;
            String expected="MarketLite-"+(tag.startsWith("v")?tag.substring(1):tag)+".apk";
            String apk=RELEASES+"/download/"+tag+"/"+expected;
            boolean found=false;for(int j=0;j<assets.length();j++){JSONObject asset=assets.getJSONObject(j);if(expected.equals(asset.optString("name"))&&apk.equals(asset.optString("browser_download_url"))&&"uploaded".equals(asset.optString("state"))){found=true;break;}}
            if(!found)continue;
            if(best==null||ReleaseVersion.compare(tag,best.tag)>0)best=new Release(tag,item.optString("body",""),apk,RELEASES+"/tag/"+tag);
        }
        return best;
    }
}
