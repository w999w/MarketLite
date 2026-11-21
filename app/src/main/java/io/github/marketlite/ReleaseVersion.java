package io.github.marketlite;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compare the project's numeric major/minor/patch tags and optional alpha/beta/rc suffixes. */
final class ReleaseVersion {
    private static final Pattern TAG=Pattern.compile("v?(\\d{1,8})\\.(\\d{1,8})\\.(\\d{1,8})(?:-(alpha|beta|rc)(?:[.-]?(\\d{1,8}))?)?");
    static boolean valid(String value){return value!=null&&TAG.matcher(value).matches();}
    static int compare(String left,String right){
        Matcher a=TAG.matcher(left),b=TAG.matcher(right);
        if(!a.matches()||!b.matches())throw new IllegalArgumentException("Invalid release version");
        for(int i=1;i<=3;i++){int n=Integer.compare(Integer.parseInt(a.group(i)),Integer.parseInt(b.group(i)));if(n!=0)return n;}
        int rank=Integer.compare(rank(a.group(4)),rank(b.group(4)));if(rank!=0)return rank;
        return Integer.compare(a.group(5)==null?0:Integer.parseInt(a.group(5)),b.group(5)==null?0:Integer.parseInt(b.group(5)));
    }
    private static int rank(String suffix){return suffix==null?3:"rc".equals(suffix)?2:"beta".equals(suffix)?1:0;}
}
