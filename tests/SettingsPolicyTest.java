package io.github.marketlite;

/** 纯 Java 校验：所有合法排列均保留，错误配置回退到默认顺序。 */
public final class SettingsPolicyTest {
    private static int checked;
    private static void require(boolean condition,String reason){if(!condition)throw new AssertionError(reason);checked++;}
    private static void permutations(char[] data,int i){
        if(i==data.length){String s=new String(data);require(SettingsPolicy.normalizeOrder(s).equals(s),"合法排列 "+s);return;}
        for(int j=i;j<data.length;j++){char t=data[i];data[i]=data[j];data[j]=t;permutations(data,i+1);t=data[i];data[i]=data[j];data[j]=t;}
    }
    public static void main(String[] args){
        permutations(SettingsPolicy.DEFAULT_ORDER.toCharArray(),0);
        for(String s:new String[]{null,"","1234","012345678","01234566","abcdefgh","0123456-"})require(SettingsPolicy.DEFAULT_ORDER.equals(SettingsPolicy.normalizeOrder(s)),"错误顺序恢复默认");
        for(int n=-3;n<=11;n++)require(SettingsPolicy.normalizeStart(n)==(n>=0&&n<=8?n:0),"默认进入标签范围");
        System.out.println("SettingsPolicy: "+checked+" checks passed");
    }
}
