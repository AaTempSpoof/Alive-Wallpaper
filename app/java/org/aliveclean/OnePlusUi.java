package org.aliveclean;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.view.*;
import dalvik.system.DexClassLoader;
import java.io.*;
import java.security.MessageDigest;
import java.util.function.IntFunction;
import java.util.function.IntConsumer;

/** The bundled original runtime works without an installed Oplus wallpaper app. */
final class OnePlusUi extends ContextWrapper {
    private final Resources resources;
    private final Resources.Theme theme;
    private final ClassLoader loader,bridge;
    OnePlusUi(Context base)throws Exception{
        super(base);
        String digest=AssetGl.text(base.getAssets(),"ui/oneplus-ui.sha256").trim();
        if(!digest.matches("[a-f0-9]{64}"))throw new IOException("Invalid OnePlus UI checksum");
        File folder=new File(base.getCodeCacheDir(),"oneplus-ui");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("UI cache unavailable");
        File apk=new File(folder,digest+".apk");
        if(!apk.isFile()){
            File part=File.createTempFile("ui-",".tmp",folder);
            try{
                MessageDigest hash=MessageDigest.getInstance("SHA-256");
                try(InputStream in=base.getAssets().open("ui/oneplus-ui.apk");FileOutputStream out=new FileOutputStream(part)){
                    if(!part.setReadOnly())throw new IOException("Cannot protect OnePlus code");
                    byte[] bytes=new byte[65536];for(int n;(n=in.read(bytes))!=-1;){out.write(bytes,0,n);hash.update(bytes,0,n);}out.getFD().sync();
                }
                StringBuilder actual=new StringBuilder();for(byte b:hash.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                if(!digest.contentEquals(actual))throw new IOException("OnePlus UI checksum mismatch");
                if(!part.renameTo(apk))throw new IOException("Cannot store OnePlus UI");
            }finally{if(part.exists())part.delete();}
        }
        loader=new DexClassLoader(apk.getPath(),base.getCodeCacheDir().getPath(),null,Context.class.getClassLoader());
        bridge=new DexClassLoader(base.getApplicationInfo().sourceDir,base.getCodeCacheDir().getPath(),null,loader);
        ApplicationInfo info=new ApplicationInfo();info.packageName="com.oplus.wallpapers";info.uid=android.os.Process.myUid();
        info.sourceDir=info.publicSourceDir=apk.getPath();info.targetSdkVersion=35;
        resources=base.getPackageManager().getResourcesForApplication(info);theme=resources.newTheme();
        int style=resources.getIdentifier("HomeTheme","style",info.packageName);theme.applyStyle(style,true);
    }
    View carousel(IntFunction<View> cards,int count,int spacing,Runnable changed,IntConsumer settled)throws Exception{
        return (View)bridge.loadClass("org.aliveclean.oneplus.CarouselBinding").getMethod("create",Context.class,IntFunction.class,int.class,int.class,Runnable.class,IntConsumer.class).invoke(null,this,cards,count,spacing,changed,settled);
    }
    @Override public Resources getResources(){return resources;}
    @Override public android.content.res.AssetManager getAssets(){return resources.getAssets();}
    @Override public Resources.Theme getTheme(){return theme;}
    @Override public ClassLoader getClassLoader(){return loader;}
    @Override public Context getApplicationContext(){return this;}
    @Override public Object getSystemService(String name){if(LAYOUT_INFLATER_SERVICE.equals(name))return LayoutInflater.from(getBaseContext()).cloneInContext(this);return super.getSystemService(name);}
}
