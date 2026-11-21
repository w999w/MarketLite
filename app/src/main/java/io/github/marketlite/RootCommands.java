package io.github.marketlite;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** 唯一的 Root 命令入口。命令固定，不接收网络、剪贴板或用户输入作为 shell 参数。 */
final class RootCommands {
    static final String QUICK_PACKAGE = "com.miui.hybrid";

    enum Operation {
        CHECK("id -u", 20),
        STOP_MARKET("am force-stop com.xiaomi.market", 20),
        UNINSTALL_QUICK("pm uninstall -k --user 0 " + QUICK_PACKAGE, 30),
        RESTORE_QUICK("cmd package install-existing --user 0 " + QUICK_PACKAGE, 30);

        final String command;
        final int timeoutSeconds;
        Operation(String command, int timeoutSeconds) { this.command=command; this.timeoutSeconds=timeoutSeconds; }
    }

    static final class Result {
        final int exitCode;
        final boolean timedOut;
        final String output;
        Result(int exitCode, boolean timedOut, String output) { this.exitCode=exitCode;this.timedOut=timedOut;this.output=output; }
        boolean success() { return !timedOut && exitCode==0; }
        boolean isRoot() { if(!success())return false;for(String line:output.split("\\n"))if("0".equals(line.trim()))return true;return false; }
        String details(Operation op) { return "操作："+op.name()+"\n退出状态："+(timedOut?"超时":exitCode)+"\n输出：\n"+output; }
    }

    private RootCommands() {}

    /** 仅在后台线程调用。持续排空输出；超时销毁进程，防止 Root 授权或命令卡住 UI。 */
    static Result run(Operation operation) throws Exception {
        Process process=new ProcessBuilder("su", "-c", operation.command).redirectErrorStream(true).start();
        StringBuilder output=new StringBuilder();
        Thread reader=new Thread(()->{
            try(BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){
                for(String line;(line=in.readLine())!=null;)synchronized(output){if(output.length()<2048)output.append(line,0,Math.min(line.length(),2048-output.length())).append('\n');}
            }catch(Exception ignored){}
        },"marketlite-command-output");
        reader.setDaemon(true);reader.start();
        try{
            // API 24/25 不依赖较新的 Process.waitFor(timeout) 实现。
            long deadline=android.os.SystemClock.elapsedRealtime()+operation.timeoutSeconds*1000L;
            while(true){
                try{int code=process.exitValue();reader.join(800);synchronized(output){return new Result(code,false,output.toString().trim());}}
                catch(IllegalThreadStateException running){if(android.os.SystemClock.elapsedRealtime()>=deadline){process.destroy();synchronized(output){return new Result(-1,true,output.toString().trim());}}Thread.sleep(80);}
            }
        }finally{process.destroy();}
    }
}
