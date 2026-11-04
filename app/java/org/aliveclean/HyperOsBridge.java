package org.aliveclean;

import android.content.Context;
import android.content.Intent;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.os.Build;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import de.robv.android.xposed.*;

/** Event-driven SystemUI adapter for HyperOS 3 wallpaper transitions. */
final class HyperOsBridge {
    private static final String DOZE_WALLPAPER_STATE="com.android.systemui.doze.DozeWallpaperState";
    private static final String MIUI_DOZE_WALLPAPER_STATE="com.miui.aod.doze.DozeWallpaperState";
    private static final String MIUI_DOZE_SERVICE="com.android.keyguard.doze.MiuiDozeService";
    private static final String MIUI_DOZE_PLUGIN="com.miui.aod.doze.DozeServicePluginImpl";
    private static final String FULL_AOD_MANAGER="com.android.keyguard.fullaod.MiuiFullAodManager";
    private static final String DOZE_SERVICE_HOST="com.android.keyguard.injector.DozeServiceHostInjector";
    private static final String KEYGUARD_STATE_CONTROLLER="com.android.systemui.statusbar.policy.KeyguardStateControllerImpl";
    private static final String AOD_APPLICATION="com.miui.aod.AODApplication";
    private static final String AOD_SETTINGS="com.miui.aod.widget.AODSettings";
    private static final String AOD_VIEW="com.miui.aod.AODView";
    private static final String AOD_UTILS="com.miui.aod.Utils";
    private static final String WALLPAPER_UTILS="com.miui.aod.util.WallPaperUtils";
    private static final String DOZE_HOST="com.miui.aod.DozeHost";
    private static final String COMMON_SETTINGS="com.miui.keyguard.KeyguardCommonSettingObserver";
    private static final String KEYGUARD_PANEL="com.android.keyguard.panel.KeyguardPanelViewController";
    private static volatile Boolean ambient;
    private static int lastMode=-1,lastPhase=-1;
    private static long lastEventAt=-1;
    private static boolean installed;
    private static boolean classLoaderHooked;
    private static boolean baseDexClassLoaderHooked;
    private static boolean aodUtilsHooked,wallpaperUtilsHooked,aodSettingsHooked,dozeHostHooked,aodViewHooked;
    private static boolean compatibilityLog;
    private static boolean scanLog,classLoadLog,superStyleLog,ambientModeLog,keepScreenLog,maskSetupLog,fullAodLog,disableWallpaperLog,maskRunLog;
    private static boolean dozeServiceHostHooked,fullAodPreflightLog;
    private static boolean dozeWallpaperHooked,miuiDozeWallpaperHooked,miuiDozeHooked,sceneBinderLog;
    private static boolean miuiDozePluginHooked;
    private static boolean linkageTagLog,aodWallpaperSupportLog,wallpaperRootLog,wallpaperBlackLog,aodRootBackgroundLog,aodRevealMaskLog;
    private static final IBinder SCENE_OWNER=new Binder();
    private static volatile Messenger sceneChannel;
    private static Handler sceneWorker;
    private static boolean sceneConnecting;
    private static Bundle pendingScene;

    static synchronized void install(ClassLoader loader){
        if(installed)return;
        installed=true;
        Class<?> doze=XposedHelpers.findClassIfExists(DOZE_WALLPAPER_STATE,loader);
        if(doze!=null)hookDozeWallpaperState(doze);
        else XposedBridge.log("OpenAlive: HyperOS DozeWallpaperState unavailable");
        Class<?> miuiDozeWallpaper=XposedHelpers.findClassIfExists(MIUI_DOZE_WALLPAPER_STATE,loader);
        if(miuiDozeWallpaper!=null)hookDozeWallpaperState(miuiDozeWallpaper);

        Class<?> miuiDoze=XposedHelpers.findClassIfExists(MIUI_DOZE_SERVICE,loader);
        if(miuiDoze!=null)hookMiuiDoze(miuiDoze);
        Class<?> miuiPlugin=XposedHelpers.findClassIfExists(MIUI_DOZE_PLUGIN,loader);
        if(miuiPlugin!=null)hookMiuiDozePlugin(miuiPlugin);

        Class<?> fullAod=XposedHelpers.findClassIfExists(FULL_AOD_MANAGER,loader);
        if(fullAod!=null){
            XposedBridge.hookAllMethods(fullAod,"updateSettings",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam call){
                    if(call.hasThrowable())return;
                    Context context=android.app.AndroidAppHelper.currentApplication();
                    if(context==null||!ownsWallpaper(context))return;
                    try{
                        XposedHelpers.setBooleanField(call.thisObject,"isWallpaperTypeSupport",true);
                        if(fullAodAnimationEligible(context,call.thisObject)){
                            XposedHelpers.setBooleanField(call.thisObject,"isFullAodSupport",true);
                            XposedHelpers.setIntField(call.thisObject,"mNotSupportReason",0);
                            XposedHelpers.callMethod(call.thisObject,"updateFullAodEnable");
                        }
                    }catch(Throwable error){XposedBridge.log("OpenAlive: HyperOS full AOD adapter failed: "+error);}
                }
            });
            XposedBridge.hookAllMethods(fullAod,"fullAodEnable",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam call){
                    if(call.hasThrowable()||Boolean.TRUE.equals(call.getResult()))return;
                    Context context=android.app.AndroidAppHelper.currentApplication();
                    if(context==null||!ownsWallpaper(context))return;
                    try{
                        if(fullAodAnimationEligible(context,call.thisObject))call.setResult(true);
                    }catch(Throwable error){XposedBridge.log("OpenAlive: HyperOS full AOD state failed: "+error);}
                }
            });
        }

        Class<?> dozeServiceHost=XposedHelpers.findClassIfExists(DOZE_SERVICE_HOST,loader);
        if(dozeServiceHost!=null)hookDozeServiceHost(dozeServiceHost);
        else XposedBridge.log("OpenAlive: HyperOS DozeServiceHostInjector unavailable");

        Class<?> controller=XposedHelpers.findClassIfExists(KEYGUARD_STATE_CONTROLLER,loader);
        if(controller!=null){
            XposedBridge.hookAllMethods(controller,"notifyKeyguardState",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam call){
                    if(call.args.length<2)return;
                    publish(HyperOsScenePolicy.keyguard((Boolean)call.args[0],interactive(),
                            Boolean.TRUE.equals(ambient),false),"keyguard");
                }
            });
            XposedBridge.hookAllMethods(controller,"notifyKeyguardGoingAway",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam call){
                    if(call.args.length<1)return;
                    publish(HyperOsScenePolicy.keyguard(false,interactive(),
                            Boolean.TRUE.equals(ambient),(Boolean)call.args[0]),"goingAway");
                }
            });
        }else XposedBridge.log("OpenAlive: HyperOS KeyguardStateController unavailable");
        Class<?> keyguardPanel=XposedHelpers.findClassIfExists(KEYGUARD_PANEL,loader);
        if(keyguardPanel!=null)hookKeyguardPanel(keyguardPanel);
        Class<?> commonSettings=XposedHelpers.findClassIfExists(COMMON_SETTINGS,loader);
        if(commonSettings!=null){
            XposedBridge.hookAllMethods(commonSettings,"getAodUsingSuperWallpaperStyle",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam call){
                    if(call.hasThrowable()||Boolean.TRUE.equals(call.getResult()))return;
                    Context context=android.app.AndroidAppHelper.currentApplication();
                    if(context==null||!ownsWallpaper(context))return;
                    // SystemUI gates linkageViewAnim on this value even though the
                    // AOD process acknowledges our wallpaper as super_wallpaper.
                    // Keep the user's global setting untouched.
                    call.setResult(true);
                }
            });
        }else XposedBridge.log("OpenAlive: HyperOS KeyguardCommonSettingObserver unavailable");
        installAodWallpaperCompatibility(loader);
        {if(Diagnostics.TRACE)XposedBridge.log("OpenAlive: HyperOS SystemUI bridge installed");}
    }

    /** Install only the native AOD wallpaper bridge in HyperOS' separate AOD process. */
    static void installAodCompatibility(ClassLoader loader){
        installAodWallpaperCompatibility(loader);
    }

    /** Keep the live wallpaper in HyperOS' native AOD layer without changing global AOD state. */
    private static void installAodWallpaperCompatibility(ClassLoader loader){
        try{
            if(!scanLog){scanLog=true;XposedBridge.log("OpenAlive: HyperOS AOD compatibility scan loader="+loader.getClass().getName());}
            Class<?> actualDoze=XposedHelpers.findClassIfExists(MIUI_DOZE_WALLPAPER_STATE,loader);
            if(actualDoze!=null)hookDozeWallpaperState(actualDoze);
            Class<?> actualMiuiDoze=XposedHelpers.findClassIfExists(MIUI_DOZE_SERVICE,loader);
            if(actualMiuiDoze!=null)hookMiuiDoze(actualMiuiDoze);
            if(!classLoaderHooked){
                classLoaderHooked=true;
                XposedBridge.hookAllMethods(ClassLoader.class,"loadClass",new XC_MethodHook(){
                    @Override protected void afterHookedMethod(MethodHookParam call){
                        if(call.hasThrowable()||call.args.length<1||!(call.args[0] instanceof String)||!(call.getResult() instanceof Class))return;
                        String name=(String)call.args[0];
                        if(name.startsWith("com.miui.aod.")||isSceneClass(name))hookAodClass(name,(Class<?>)call.getResult());
                    }
                });
            }
            if(!baseDexClassLoaderHooked){
                Class<?> baseDex=XposedHelpers.findClassIfExists("dalvik.system.BaseDexClassLoader",loader);
                if(baseDex!=null){
                    baseDexClassLoaderHooked=true;
                    XposedBridge.hookAllMethods(baseDex,"findClass",new XC_MethodHook(){
                        @Override protected void afterHookedMethod(MethodHookParam call){
                            if(call.hasThrowable()||!(call.getResult() instanceof Class))return;
                            Class<?> type=(Class<?>)call.getResult();
                            String name=type.getName();
                            if(name.startsWith("com.miui.aod.")||isSceneClass(name))hookAodClass(name,type);
                        }
                    });
                }
            }
            Class<?> utils=XposedHelpers.findClassIfExists(AOD_UTILS,loader);
            if(utils!=null)hookAodUtils(utils);else XposedBridge.log("OpenAlive: HyperOS AOD Utils unavailable");

            Class<?> wallpaperUtils=XposedHelpers.findClassIfExists(WALLPAPER_UTILS,loader);
            if(wallpaperUtils!=null)hookWallpaperUtils(wallpaperUtils,loader);else XposedBridge.log("OpenAlive: HyperOS AOD WallPaperUtils unavailable");

            Class<?> settings=XposedHelpers.findClassIfExists(AOD_SETTINGS,loader);
            if(settings!=null)hookAodSettings(settings,loader);else XposedBridge.log("OpenAlive: HyperOS AOD AODSettings unavailable");

            Class<?> host=XposedHelpers.findClassIfExists(DOZE_HOST,loader);
            if(host!=null)hookDozeHost(host,loader);else XposedBridge.log("OpenAlive: HyperOS DozeHost unavailable");
            Class<?> aodView=XposedHelpers.findClassIfExists(AOD_VIEW,loader);
            if(aodView!=null)hookAodView(aodView,loader);else XposedBridge.log("OpenAlive: HyperOS AODView unavailable");
        }catch(Throwable error){XposedBridge.log("OpenAlive: HyperOS AOD wallpaper compatibility failed: "+error);}
    }

    private static synchronized void hookAodClass(String name,Class<?> type){
        try{
            if(!classLoadLog){classLoadLog=true;XposedBridge.log("OpenAlive: HyperOS AOD class hook "+name);}
            if(DOZE_WALLPAPER_STATE.equals(name)||MIUI_DOZE_WALLPAPER_STATE.equals(name)){hookDozeWallpaperState(type);return;}
            if(MIUI_DOZE_SERVICE.equals(name)){hookMiuiDoze(type);return;}
            if(MIUI_DOZE_PLUGIN.equals(name)){hookMiuiDozePlugin(type);return;}
            if(KEYGUARD_PANEL.equals(name)){hookKeyguardPanel(type);return;}
            if(AOD_UTILS.equals(name))hookAodUtils(type);
            else if(WALLPAPER_UTILS.equals(name))hookWallpaperUtils(type,type.getClassLoader());
            else if(AOD_SETTINGS.equals(name))hookAodSettings(type,type.getClassLoader());
            else if(DOZE_HOST.equals(name))hookDozeHost(type,type.getClassLoader());
            else if(AOD_VIEW.equals(name))hookAodView(type,type.getClassLoader());
        }catch(Throwable error){XposedBridge.log("OpenAlive: HyperOS deferred AOD hook failed for "+name+": "+error);}
    }

    private static boolean isSceneClass(String name){
        return DOZE_WALLPAPER_STATE.equals(name)||MIUI_DOZE_WALLPAPER_STATE.equals(name)||MIUI_DOZE_SERVICE.equals(name)||MIUI_DOZE_PLUGIN.equals(name)||KEYGUARD_PANEL.equals(name);
    }

    /** Keep HyperOS' black-wallpaper pass from covering the live renderer on AOD entry. */
    private static synchronized void hookKeyguardPanel(Class<?> type){
        if(wallpaperBlackLog)return;
        XposedBridge.hookAllMethods(type,"doWallpaperBlackAnim",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam call){
                if(call.args.length<3||!Boolean.FALSE.equals(call.args[2]))return;
                Context context=android.app.AndroidAppHelper.currentApplication();
                if(context==null||!ownsWallpaper(context)||!aodEnabled(context))return;
                // args[2] is HyperOS' show flag.  The false leg is the sleep/AOD
                // blackening pass; the true leg is wake and must stay native so
                // the reverse lens can reveal the wallpaper normally.
                call.setResult(null);
                if(!wallpaperBlackLog){wallpaperBlackLog=true;XposedBridge.log("OpenAlive: HyperOS AOD entry black wallpaper pass suppressed");}
            }
        });
        XposedBridge.log("OpenAlive: HyperOS KeyguardPanel wallpaper hook installed");
    }

    private static synchronized void hookDozeWallpaperState(Class<?> type){
        boolean miui=MIUI_DOZE_WALLPAPER_STATE.equals(type.getName());
        if(miui?miuiDozeWallpaperHooked:dozeWallpaperHooked)return;
        if(miui)miuiDozeWallpaperHooked=true;else dozeWallpaperHooked=true;
        XposedBridge.hookAllMethods(type,"transitionTo",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                boolean now=XposedHelpers.getBooleanField(call.thisObject,"mIsAmbientMode");
                observeAmbient(now,"doze "+(call.args.length>1?call.args[1]:now));
            }
        });
        XposedBridge.log("OpenAlive: HyperOS DozeWallpaperState hook installed "+type.getName());
    }

    private static synchronized void hookMiuiDoze(Class<?> type){
        if(miuiDozeHooked)return;miuiDozeHooked=true;
        XposedBridge.hookAllMethods(type,"onDozingRequested",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                hookLoadedMiuiWallpaperState(call.thisObject);
                if(call.args.length>0)observeAmbient((Boolean)call.args[0],"miui-dozing");
            }
        });
        XposedBridge.hookAllMethods(type,"onDreamingStarted",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                hookLoadedMiuiWallpaperState(call.thisObject);
                ambient=true;
                publish(new HyperOsScenePolicy.Event(0,1,true),"dreaming-started");
            }
        });
        XposedBridge.hookAllMethods(type,"onDreamingStopped",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                hookLoadedMiuiWallpaperState(call.thisObject);
                ambient=false;
                publish(new HyperOsScenePolicy.Event(1,2,true),"dreaming-stopped");
            }
        });
        XposedBridge.log("OpenAlive: HyperOS MiuiDozeService hook installed");
    }

    private static void hookLoadedMiuiWallpaperState(Object host){
        try{
            if(host==null)return;
            ClassLoader loader=host.getClass().getClassLoader();
            Class<?> state=XposedHelpers.findClassIfExists(MIUI_DOZE_WALLPAPER_STATE,loader);
            if(state!=null)hookDozeWallpaperState(state);
        }catch(Throwable error){XposedBridge.log("OpenAlive: HyperOS late DozeWallpaperState lookup failed: "+error);}
    }

    private static synchronized void hookMiuiDozePlugin(Class<?> type){
        if(miuiDozePluginHooked)return;miuiDozePluginHooked=true;
        XposedBridge.hookAllMethods(type,"onDreamingStarted",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam call){
                if(call.hasThrowable()||call.args.length<1||!(call.args[0] instanceof android.view.ViewGroup))return;
                Context context=android.app.AndroidAppHelper.currentApplication();
                if(context==null||!ownsWallpaper(context))return;
                try{
                    android.view.ViewGroup root=(android.view.ViewGroup)call.args[0];
                    // HyperOS carries the linkage/full-AOD decision in two tags on
                    // the host container.  A live wallpaper has no tag of its own,
                    // so the stock plugin otherwise falls back to a black AOD root
                    // even when the wallpaper advertises ambient-mode support.
                    root.setTag(Boolean.TRUE);
                    int id=root.getId();
                    if(id!=android.view.View.NO_ID)root.setTag(id,Boolean.TRUE);
                    if(!linkageTagLog){linkageTagLog=true;XposedBridge.log("OpenAlive: HyperOS AOD linkage/full tags enabled");}
                }catch(Throwable error){XposedBridge.log("OpenAlive: HyperOS AOD linkage tag setup failed: "+error);}
            }
            @Override protected void afterHookedMethod(MethodHookParam call){
                ambient=true;publish(new HyperOsScenePolicy.Event(0,1,true),"plugin-dreaming-started");
            }
        });
        XposedBridge.hookAllMethods(type,"onDreamingStopped",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                ambient=false;publish(new HyperOsScenePolicy.Event(1,2,true),"plugin-dreaming-stopped");
            }
        });
        XposedBridge.log("OpenAlive: HyperOS DozeServicePluginImpl hook installed");
    }

    private static synchronized void hookAodUtils(Class<?> type){
        if(aodUtilsHooked)return;aodUtilsHooked=true;
        XposedBridge.log("OpenAlive: HyperOS AOD Utils hook installed");
        XposedBridge.hookAllMethods(type,"isUsingSuperWallpaperStyle",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                if(call.hasThrowable()||Boolean.TRUE.equals(call.getResult())||call.args.length<1)return;
                Context context=(Context)call.args[0];
                if(context!=null&&ownsWallpaper(context)){
                    call.setResult(true);
                    if(!superStyleLog){superStyleLog=true;XposedBridge.log("OpenAlive: HyperOS AOD super-wallpaper-style override");}
                    compatibilityLog("super-wallpaper-style");
                }
            }
        });
    }

    private static synchronized void hookWallpaperUtils(Class<?> type,ClassLoader loader){
        if(wallpaperUtilsHooked)return;wallpaperUtilsHooked=true;
        XposedBridge.log("OpenAlive: HyperOS AOD WallPaperUtils hook installed");
        XposedBridge.hookAllMethods(type,"isWallpaperSupportsAmbientMode",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                if(call.hasThrowable()||Boolean.TRUE.equals(call.getResult()))return;
                Context context=aodContext(loader);
                boolean owned=context!=null&&ownsWallpaper(context);
                if(!ambientModeLog){ambientModeLog=true;XposedBridge.log("OpenAlive: HyperOS AOD ambient call context="+(context!=null)+" owned="+owned);}
                if(owned){
                    call.setResult(true);compatibilityLog("ambient-wallpaper");
                }
            }
        });
    }

    private static synchronized void hookAodSettings(Class<?> type,ClassLoader loader){
        if(aodSettingsHooked)return;aodSettingsHooked=true;
        XposedBridge.log("OpenAlive: HyperOS AOD AODSettings hook installed");
        XposedBridge.hookAllMethods(type,"isSupportAodWallpaper",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                if(call.hasThrowable()||Boolean.TRUE.equals(call.getResult()))return;
                Context context=aodContext(loader);
                if(context!=null&&ownsWallpaper(context)&&aodEnabled(context)){
                    call.setResult(true);
                    if(!aodWallpaperSupportLog){aodWallpaperSupportLog=true;XposedBridge.log("OpenAlive: HyperOS AOD wallpaper support forced");}
                }
            }
        });
        XposedBridge.hookAllMethods(type,"needKeepScreenOnAtFirst",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                if(call.hasThrowable()||Boolean.TRUE.equals(call.getResult()))return;
                Context context=aodContext(loader);
                boolean owned=context!=null&&ownsWallpaper(context);
                if(!keepScreenLog){keepScreenLog=true;XposedBridge.log("OpenAlive: HyperOS AOD keep-screen call context="+(context!=null)+" owned="+owned);}
                if(owned){
                    call.setResult(true);compatibilityLog("native-aod-window");
                }
            }
        });
    }

    private static synchronized void hookDozeHost(Class<?> type,ClassLoader loader){
        if(dozeHostHooked)return;dozeHostHooked=true;
        XposedBridge.log("OpenAlive: HyperOS DozeHost hook installed");
        XposedBridge.hookAllMethods(type,"showSecurityIdentityViewAt",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                if(call.hasThrowable())return;
                Context context=aodContext(loader);
                if(context==null||!ownsWallpaper(context)||!aodEnabled(context))return;
                try{
                    XposedHelpers.setBooleanField(call.thisObject,"mNeedShowWallpaper",true);
                    Object view=call.getResult();
                    if(view!=null)XposedHelpers.callMethod(view,"setBackgroundColor",0);
                    if(!wallpaperRootLog){wallpaperRootLog=true;XposedBridge.log("OpenAlive: HyperOS AOD wallpaper root retained");}
                }catch(Throwable error){XposedBridge.log("OpenAlive: HyperOS AOD wallpaper root setup failed: "+error);}
            }
        });
        XposedHelpers.findAndHookMethod(type,"prepareAodViewAndShow",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                if(call.hasThrowable())return;
                Context context=aodContext(loader);
                if(context==null||!ownsWallpaper(context)||!aodEnabled(context))return;
                keepAodWallpaperRoot(call.thisObject);
                startAodRevealMask(call.thisObject);
            }
        });
        XposedBridge.hookAllMethods(type,"setShouldUseMaskAnimation",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                if(call.hasThrowable()||call.args.length<1)return;
                if(!maskSetupLog){maskSetupLog=true;XposedBridge.log("OpenAlive: HyperOS AOD mask request="+call.args[0]);}
                if(!Boolean.TRUE.equals(call.args[0]))return;
                Context context=aodContext(loader);
                if(context==null||!ownsWallpaper(context))return;
                try{
                    XposedHelpers.setBooleanField(call.thisObject,"startMaskAnimationIfNeeded",true);
                    compatibilityLog("native-aod-mask");
                }catch(Throwable error){XposedBridge.log("OpenAlive: HyperOS native AOD mask setup failed: "+error);}
            }
        });
        XposedBridge.hookAllMethods(type,"isFullAod",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                if(!call.hasThrowable()&&!fullAodLog){fullAodLog=true;XposedBridge.log("OpenAlive: HyperOS AOD fullAod="+call.getResult());}
            }
        });
        XposedBridge.hookAllMethods(type,"disableShowWallpaper",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                if(!call.hasThrowable()&&!disableWallpaperLog){disableWallpaperLog=true;XposedBridge.log("OpenAlive: HyperOS AOD disableShowWallpaper called");}
            }
        });
        XposedBridge.hookAllMethods(type,"startMaskAnimationIfNeeded",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                if(!call.hasThrowable()&&!maskRunLog){maskRunLog=true;XposedBridge.log("OpenAlive: HyperOS AOD mask entry called");}
            }
        });
    }

    private static synchronized void hookAodView(Class<?> type,ClassLoader loader){
        if(aodViewHooked)return;aodViewHooked=true;
        XposedBridge.hookAllMethods(type,"makeNormalPanel",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                if(call.hasThrowable())return;
                Context context=aodContext(loader);
                if(context==null||!ownsWallpaper(context)||!aodEnabled(context))return;
                try{XposedHelpers.callMethod(call.thisObject,"setBackgroundColor",0);}
                catch(Throwable error){XposedBridge.log("OpenAlive: HyperOS AODView transparent background failed: "+error);}
            }
        });
        XposedBridge.log("OpenAlive: HyperOS AODView background hook installed");
    }

    private static void keepAodWallpaperRoot(Object host){
        try{
            XposedHelpers.setBooleanField(host,"mNeedShowWallpaper",true);
            Object view=XposedHelpers.getObjectField(host,"mAODView");
            if(view!=null)XposedHelpers.callMethod(view,"setBackgroundColor",0);
            if(!aodRootBackgroundLog){aodRootBackgroundLog=true;XposedBridge.log("OpenAlive: HyperOS AOD black root background suppressed");}
        }catch(Throwable error){XposedBridge.log("OpenAlive: HyperOS AOD root background fix failed: "+error);}
    }

    private static void startAodRevealMask(Object host){
        try{
            Object view=XposedHelpers.getObjectField(host,"mAODView");
            if(view==null)return;
            XposedHelpers.callMethod(view,"initMaskView");
            XposedHelpers.callMethod(view,"startMaskAnimation");
            if(!aodRevealMaskLog){aodRevealMaskLog=true;XposedBridge.log("OpenAlive: HyperOS AOD reveal mask animation started");}
        }catch(Throwable error){XposedBridge.log("OpenAlive: HyperOS AOD reveal mask start failed: "+error);}
    }

    private static synchronized void hookDozeServiceHost(Class<?> type){
        if(dozeServiceHostHooked)return;dozeServiceHostHooked=true;
        XposedBridge.hookAllMethods(type,"updateScreenOffNeedLinkageAnimStateInternal",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam call){
                Context context=android.app.AndroidAppHelper.currentApplication();
                if(context==null||!ownsWallpaper(context)||!aodEnabled(context))return;
                try{
                    if(!XposedHelpers.getBooleanField(call.thisObject,"mSupportAod")||
                            !XposedHelpers.getBooleanField(call.thisObject,"mAodEnable"))return;
                    // SystemUI caches MiuiFullAodManager state and uses it to choose
                    // between the black fade and wallpaper-to-AOD scale animation.
                    XposedHelpers.setBooleanField(call.thisObject,"mFullAodEnable",true);
                    XposedHelpers.setBooleanField(call.thisObject,"mAodUsingSuperWallpaperStyle",true);
                    if(!fullAodPreflightLog){
                        fullAodPreflightLog=true;
                        XposedBridge.log("OpenAlive: HyperOS native AOD preflight synchronized");
                    }
                }catch(Throwable error){XposedBridge.log("OpenAlive: HyperOS native AOD preflight failed: "+error);}
            }
        });
    }

    private static boolean fullAodAnimationEligible(Context context,Object manager){
        return HyperOsScenePolicy.fullAodAnimationEligible(
                ownsWallpaper(context),
                aodEnabled(context),
                XposedHelpers.getBooleanField(manager,"isDeviceSupport"),
                XposedHelpers.getBooleanField(manager,"mAodFullScreenEnable"));
    }

    private static boolean aodEnabled(Context context){
        return Settings.Secure.getInt(context.getContentResolver(),"doze_always_on",0)==1;
    }

    private static synchronized void compatibilityLog(String stage){
        if(compatibilityLog)return;compatibilityLog=true;
        android.util.Log.i("OpenAliveHyperOS","AOD wallpaper compatibility active: "+stage);
    }

    private static Context aodContext(ClassLoader loader){
        Context context=android.app.AndroidAppHelper.currentApplication();
        if(context!=null)return context;
        try{
            Class<?> application=XposedHelpers.findClassIfExists(AOD_APPLICATION,loader);
            return application==null?null:(Context)XposedHelpers.getStaticObjectField(application,"sInstance");
        }catch(Throwable ignored){return null;}
    }

    private static synchronized void observeAmbient(boolean now,String reason){
        HyperOsScenePolicy.Event event=HyperOsScenePolicy.ambient(ambient,now);
        ambient=now;
        publish(event,reason);
    }

    private static boolean interactive(){
        Context context=android.app.AndroidAppHelper.currentApplication();
        return context==null||context.getSystemService(android.os.PowerManager.class).isInteractive();
    }

    private static boolean ownsWallpaper(Context context){
        WallpaperManager manager=WallpaperManager.getInstance(context);
        if(Build.VERSION.SDK_INT>=34&&owned(manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)))return true;
        return owned(manager.getWallpaperInfo());
    }

    private static boolean owned(WallpaperInfo info){
        return info!=null&&"org.aliveclean".equals(info.getPackageName());
    }

    private static synchronized void publish(HyperOsScenePolicy.Event event,String reason){
        if(event==null||(event.mode==lastMode&&event.phase==lastPhase))return;
        Context context=android.app.AndroidAppHelper.currentApplication();
        if(context==null)return;
        long now=SystemClock.uptimeMillis();
        lastMode=event.mode;lastPhase=event.phase;lastEventAt=now;
        Bundle scene=new Bundle();scene.putInt("mode",event.mode);scene.putBoolean("animate",event.animate);
        scene.putLong("time",now);scene.putInt("phase",event.phase);
        sendSceneChannel(context,scene);
        XposedBridge.log("OpenAlive: HyperOS scene publish mode="+event.mode+" phase="+event.phase+" source="+reason);
        Intent update=new Intent(ColorOsBridge.ACTION).setPackage("org.aliveclean")
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtras(scene);
        context.sendBroadcast(update);
        {if(Diagnostics.TRACE)android.util.Log.i("OpenAliveHyperOS","scene="+event.mode+" phase="+event.phase+" source="+reason+" latencyMs="+(SystemClock.uptimeMillis()-now));}
    }

    /** Binder delivery survives HyperOS cached-process broadcast throttling. */
    private static synchronized void sendSceneChannel(Context context,Bundle scene){
        pendingScene=new Bundle(scene);
        Messenger target=sceneChannel;
        if(target!=null)sendScene(target,scene);
        if(sceneWorker==null){
            HandlerThread thread=new HandlerThread("OpenAliveHyperOsScene");thread.start();
            sceneWorker=new Handler(thread.getLooper());
        }
        if(sceneConnecting)return;
        sceneConnecting=true;
        sceneWorker.post(()->{
            try{
                Bundle request=new Bundle();request.putBinder("owner",SCENE_OWNER);request.putInt("clock_api",0);
                Bundle reply=context.getContentResolver().call(android.net.Uri.parse("content://org.aliveclean.scene"),"connect",null,request);
                IBinder binder=reply==null?null:reply.getBinder("channel");
                if(binder==null)throw new IllegalStateException("Missing scene channel");
                Messenger connected=new Messenger(binder);Bundle latest;
                synchronized(HyperOsBridge.class){sceneChannel=connected;latest=pendingScene==null?null:new Bundle(pendingScene);}
                if(!sceneBinderLog){sceneBinderLog=true;XposedBridge.log("OpenAlive: HyperOS Binder scene channel connected");}
                if(latest!=null)sendScene(connected,latest);
            }catch(Throwable error){XposedBridge.log("OpenAlive: HyperOS Binder scene channel unavailable: "+error);}
            finally{ synchronized(HyperOsBridge.class){sceneConnecting=false;} }
        });
    }

    private static void sendScene(Messenger target,Bundle scene){
        Message message=Message.obtain();message.what=1;message.setData(new Bundle(scene));
        try{target.send(message);}catch(RemoteException error){synchronized(HyperOsBridge.class){if(sceneChannel==target)sceneChannel=null;}}
    }

    private HyperOsBridge(){}
}
