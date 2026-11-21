package io.github.marketlite;
public final class ReleaseVersionTest {
    public static void main(String[] args){
        check("v0.4.1-alpha","0.4.0-alpha",1);
        check("v0.4.1-alpha","0.4.1-alpha",0);
        check("0.4.1","v0.4.1-rc2",1);
        check("0.4.1-alpha2","v0.4.1-alpha10",-1);
        check("0.10.0-beta","v0.9.9",1);
        if(ReleaseVersion.valid("not-a-release"))throw new AssertionError();
        System.out.println("ReleaseVersion: 6 checks passed");
    }
    private static void check(String a,String b,int expected){if(Integer.signum(ReleaseVersion.compare(a,b))!=expected)throw new AssertionError(a+" vs "+b);}
}
