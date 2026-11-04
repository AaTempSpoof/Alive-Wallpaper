package org.aliveclean;

import android.content.Context;
import java.io.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Invoke only after a user applies this wallpaper. Never execute shell text from a preference. */
final class RootBridge {
    static void allowXiaomiWallpaperChange(Context context)throws Exception {
        if(!PlatformSupport.isXiaomiFamily()||PlatformSupport.hasColorOsAod(context))return;
        String command="cmd appops set "+quote(context.getPackageName())+" 10045 allow";
        Process process;
        try{process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();}
        catch(IOException error){throw new IOException("无法启动 su，请先在 KernelSU 中允许 OpenAlive",error);}
        StringBuilder output=new StringBuilder();
        Thread reader=new Thread(()->{try(BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"))){for(String line;(line=in.readLine())!=null;){synchronized(output){if(output.length()<4096)output.append(line).append('\n');}}}catch(IOException ignored){}});
        reader.start();
        if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("等待 KernelSU 授权超时，请允许 OpenAlive 后重试");}
        reader.join(1000);String result;synchronized(output){result=output.toString().trim();}
        if(process.exitValue()!=0)throw new IOException("HyperOS 壁纸权限设置失败"+(result.isEmpty()?"":"："+result));
    }

    static void allowClockStartup(Context context)throws Exception {
        if(android.os.Process.myUid()/100000!=0)throw new IOException("Clock startup setup supports only the primary user");
        String command="CLASSPATH="+quote(context.getApplicationInfo().sourceDir)
                +" /system/bin/app_process /system/bin org.aliveclean.PlatformApply clock-startup";
        Process process=new ProcessBuilder("su","1000","-c",command).redirectErrorStream(true).start();
        StringBuilder output=new StringBuilder();
        Thread reader=new Thread(()->{
            try(BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"))){
                for(String line;(line=in.readLine())!=null;){
                    synchronized(output){if(output.length()<4096)output.append(line).append('\n');}
                }
            }catch(IOException ignored){}
        },"OpenAliveClockStartupOutput");
        reader.start();
        if(!process.waitFor(20,TimeUnit.SECONDS)){
            process.destroyForcibly();throw new IOException("ColorOS clock startup setup timed out");
        }
        reader.join(1000);
        String result; synchronized(output){result=output.toString();}
        if(process.exitValue()!=0||!result.contains("\"clockStartupAllowed\":true"))
            throw new IOException("ColorOS clock startup setup failed: "+result.trim());
    }

    static void applyWallpaperComponent(Context context)throws Exception {
        if(android.os.Process.myUid()/100000!=0)throw new IOException("一键应用壁纸仅支持主用户");
        String command="CLASSPATH="+quote(context.getApplicationInfo().sourceDir)
                +" /system/bin/app_process /system/bin org.aliveclean.PlatformApply set-wallpaper";
        Process process=new ProcessBuilder("su","1000","-c",command).redirectErrorStream(true).start();
        StringBuilder output=new StringBuilder();
        Thread reader=new Thread(()->{
            try(BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"))){
                for(String line;(line=in.readLine())!=null;){
                    synchronized(output){if(output.length()<8192)output.append(line).append('\n');}
                }
            }catch(IOException ignored){}
        },"AliveWallpaperApplyOutput");
        reader.start();
        if(!process.waitFor(25,TimeUnit.SECONDS)){
            process.destroyForcibly();throw new IOException("等待 Root 授权超时，请允许 OpenAlive 后重试");
        }
        reader.join(1000);
        String result;synchronized(output){result=output.toString();}
        for(String line:result.split("\n"))if(line.startsWith("ALIVE_RESULT ")){
            JSONObject json=new JSONObject(line.substring(13));
            if(process.exitValue()==0&&json.optBoolean("ok"))return;
            throw new IOException("设置动态壁纸失败（"+json.optString("stage","unknown")+"）："+json.optString("error","未知错误"));
        }
        String detail=result.trim();if(detail.length()>400)detail=detail.substring(detail.length()-400);
        throw new IOException("壁纸应用进程未返回结果（退出码 "+process.exitValue()+"）"+(detail.isEmpty()?"":"："+detail));
    }

    static String prepareAod(Context context)throws Exception {
        if(PlatformSupport.isXiaomiFamily()&&!PlatformSupport.hasColorOsAod(context)){
            int enabled;
            try{enabled=android.provider.Settings.Secure.getInt(context.getContentResolver(),"doze_always_on",-1);}
            catch(RuntimeException unavailable){enabled=-1;}
            if(enabled!=1){runRootCommand("settings put secure doze_always_on 1","开启 HyperOS 息屏显示失败");enabled=1;}
            return enabled==1?"壁纸已应用；已开启 HyperOS 原生息屏显示":"壁纸已应用；HyperOS 息屏显示状态未确认";
        }
        if(android.os.Process.myUid()/100000!=0)throw new IOException("目前仅支持主用户的 ColorOS 息屏配置");
        if(context.getPackageManager().resolveContentProvider("com.oplus.aod.AodMachineHelperProvider",0)==null)throw new IOException("此系统未提供已适配的 ColorOS 息屏接口，请在系统设置中配置");
        String apk=context.getApplicationInfo().sourceDir;
        String command="CLASSPATH="+quote(apk)+" /system/bin/app_process /system/bin org.aliveclean.PlatformApply panoramic-aod";
        Process process;
        try{process=new ProcessBuilder("su","1000","-c",command).redirectErrorStream(true).start();}
        catch(IOException error){throw new IOException("无法启动 su，请检查 Root 是否可用",error);}
        StringBuilder output=new StringBuilder();
        Thread reader=new Thread(()->{
            try(BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"))){for(String line;(line=in.readLine())!=null;){synchronized(output){if(output.length()<65536)output.append(line).append('\n');}}}catch(IOException ignored){}
        },"AliveApplyOutput");reader.start();
        if(!process.waitFor(30,TimeUnit.SECONDS)){
            process.destroyForcibly();
            boolean started;synchronized(output){started=output.indexOf("ALIVE_ADAPTER_STARTED")>=0;}
            throw new IOException(started?"已获得 Root，但系统息屏接口响应超时":"su 等待超时，请查看超级用户授权提示");
        }
        reader.join(1000);String result; synchronized(output){result=output.toString();}
        for(String line:result.split("\n"))if(line.startsWith("ALIVE_RESULT ")){
            JSONObject json=new JSONObject(line.substring(13));
            if(process.exitValue()==0&&json.optBoolean("ok")){
                return json.optBoolean("backgroundAllowed")
                    ?"壁纸已应用，已开启全景息屏并允许后台运行"
                    :"壁纸已应用；后台运行设置未确认，请在系统电池设置中选择完全允许后台行为";
            }
            String stage=json.optString("stage","unknown");
            String detail=json.optString("error","系统拒绝切换息屏模式");
            throw new IOException("系统息屏接口失败（"+stage+"）："+detail);
        }
        android.util.Log.e("AliveClean","Adapter exit="+process.exitValue()+" output="+result);
        String detail=result.trim();if(detail.length()>600)detail=detail.substring(detail.length()-600);
        throw new IOException("息屏辅助进程未返回结果（退出码 "+process.exitValue()+"）"+(detail.isEmpty()?"，请检查超级用户授权":"："+detail));
    }
    private static void runRootCommand(String command,String failure)throws Exception{
        Process process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
        StringBuilder output=new StringBuilder();
        Thread reader=new Thread(()->{try(BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"))){for(String line;(line=in.readLine())!=null;){synchronized(output){if(output.length()<4096)output.append(line).append('\n');}}}catch(IOException ignored){}});
        reader.start();
        if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException(failure+"：Root 命令超时");}
        reader.join(1000);if(process.exitValue()!=0){String result;synchronized(output){result=output.toString().trim();}throw new IOException(failure+(result.isEmpty()?"":"："+result));}
    }
    private static String quote(String value){return "'"+value.replace("'","'\"'\"'")+"'";}
}
