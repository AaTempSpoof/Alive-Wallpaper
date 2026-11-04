package org.aliveclean;
import android.app.Activity;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.ArrayList;
/** OnePlus-style wallpaper home with a controlled card preview. */
public final class HomeActivity extends Activity {
    private static final int BG=0xff17181a;
    private static final int BLUE=0xff4c8dff;
    private static final int CARD_WIDTH_DP=200,CARD_HEIGHT_DP=350,CARD_GAP_DP=12;
    private static final int[] STYLES={0,1,2,3,4,5,101};
    private static final String[] NAMES={"启航","留光","星月","轻启","山脉","银河","全屏 AOD"};
    private static final String[] MODE_NAMES={"息屏","锁屏","桌面"};
    private final ArrayList<HomeScenePreview> previews=new ArrayList<>();
    private final ArrayList<View> cards=new ArrayList<>();
    private final ArrayList<View> borders=new ArrayList<>();
    private ModeSlider modeSlider;
    private boolean resumed;
    private int selectedCard=0,previewMode=1;
    private boolean awaitingWallpaperApply,pickerResultReliable;
    private int previousWallpaperId,previousLockId;
    private String pendingStyleName;
    private View carousel;private TextView styleTitle;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setGravity(Gravity.CENTER_HORIZONTAL);page.setBackgroundColor(BG);setContentView(page);
        page.addView(new Space(this),new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout host=new FrameLayout(this);page.addView(host,new LinearLayout.LayoutParams(-1,dp(CARD_HEIGHT_DP)));
        for(int i=0;i<STYLES.length;i++)cards.add(card(i));
        try{
            OnePlusUi ui=new OnePlusUi(this);
            carousel=ui.carousel(index->cards.get(index),cards.size(),dp(CARD_WIDTH_DP+CARD_GAP_DP),this::updatePreviews,this::onCardSelected);
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,dp(CARD_HEIGHT_DP),Gravity.CENTER);host.addView(carousel,lp);
        }catch(Exception|LinkageError error){
            android.util.Log.e("AliveClean","Original OnePlus carousel failed",error);
            TextView message=new TextView(this);message.setText("一加原版轮播加载失败，请保留日志重试");message.setTextColor(Color.WHITE);message.setGravity(Gravity.CENTER);host.addView(message,new FrameLayout.LayoutParams(-1,-1));
        }
        styleTitle=new TextView(this);styleTitle.setTextColor(0xffd7dce5);styleTitle.setTextSize(18);styleTitle.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);styleTitle.setGravity(Gravity.CENTER);styleTitle.setSingleLine(true);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,dp(26));titleLp.setMargins(dp(20),dp(6),dp(20),0);
        page.addView(styleTitle,titleLp);
        LinearLayout.LayoutParams tabLp=new LinearLayout.LayoutParams(-1,dp(46));tabLp.setMargins(dp(20),dp(8),dp(20),0);
        page.addView(previewTabs(),tabLp);
        LinearLayout.LayoutParams controlsLp=new LinearLayout.LayoutParams(-1,-2);controlsLp.setMargins(0,dp(10),0,0);
        page.addView(controls(),controlsLp);
        page.addView(new Space(this),new LinearLayout.LayoutParams(-1,0,1));
        setContinuousAod(true);updateSelectionUi();allowClockStartup();
    }
    private View previewTabs(){return modeSlider=new ModeSlider(this);}
    private void updateTabs(){if(modeSlider!=null)modeSlider.setSelectedMode(previewMode,true);}
    /** Animated draggable three-way mode switch with matching radii and equal insets. */
    private final class ModeSlider extends FrameLayout {
        private static final int CORNER_DP=19;
        private static final int INSET_DP=4;
        private static final int ANIMATION_MS=220;
        private final TextView[] labels=new TextView[3];
        private final View thumb;
        private final int inset;
        private final int touchSlop;
        private int selected;
        private float downX;
        private boolean dragging;
        ModeSlider(Context context){
            super(context);
            inset=dp(INSET_DP);
            touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();
            setPadding(inset,inset,inset,inset);
            setClickable(true);
            setFocusable(true);
            GradientDrawable background=new GradientDrawable();
            background.setColor(0xff34363b);
            background.setCornerRadius(dp(CORNER_DP));
            setBackground(background);
            thumb=new View(context);
            GradientDrawable pill=new GradientDrawable();
            pill.setColor(0xff5b5e66);
            pill.setCornerRadius(dp(CORNER_DP));
            thumb.setBackground(pill);
            addView(thumb,new FrameLayout.LayoutParams(0,-1));
            LinearLayout row=new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClickable(false);
            row.setFocusable(false);
            for(int i=0;i<3;i++){
                TextView label=new TextView(context);
                label.setText(MODE_NAMES[i]);
                label.setGravity(Gravity.CENTER);
                label.setTextSize(15);
                label.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
                label.setClickable(false);
                label.setFocusable(false);
                label.setContentDescription(MODE_NAMES[i]);
                labels[i]=label;
                row.addView(label,new LinearLayout.LayoutParams(0,-1,1));
            }
            addView(row,new FrameLayout.LayoutParams(-1,-1));
            updateTextColors(0);
        }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
            super.onSizeChanged(w,h,oldw,oldh);
            int segment=segmentWidth();
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)thumb.getLayoutParams();
            lp.width=Math.max(1,segment);
            lp.height=Math.max(1,h-getPaddingTop()-getPaddingBottom());
            thumb.setLayoutParams(lp);
            thumb.setX((float)segment*selected);
        }
        void setSelectedMode(int index,boolean animate){
            int next=Math.max(0,Math.min(2,index));
            boolean changed=selected!=next;
            selected=next;
            updateTextColors(selected);
            if(changed)moveThumb(next,animate);
        }
        private int segmentWidth(){
            int width=getWidth()-getPaddingLeft()-getPaddingRight();
            return width<=0?0:width/3;
        }
        private void moveThumb(int index,boolean animate){
            int segment=segmentWidth();
            if(segment<=0)return;
            float target=(float)segment*index;
            if(animate){
                thumb.animate().x(target).setDuration(ANIMATION_MS)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            }else{
                thumb.animate().cancel();
                thumb.setX(target);
            }
        }
        private int indexAt(float x){
            int segment=segmentWidth();
            if(segment<=0)return selected;
            return Math.max(0,Math.min(2,(int)((x-getPaddingLeft())/segment)));
        }
        private void updateTextColors(int highlight){
            for(int i=0;i<labels.length;i++)labels[i].setTextColor(i==highlight?Color.WHITE:0xffa4a7ae);
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downX=event.getX();
                    dragging=false;
                    thumb.animate().cancel();
                    if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if(Math.abs(event.getX()-downX)>touchSlop)dragging=true;
                    if(dragging){
                        int segment=segmentWidth();
                        float target=event.getX()-getPaddingLeft()-segment/2f;
                        target=Math.max(0,Math.min(segment*2f,target));
                        thumb.setX(target);
                        updateTextColors(indexAt(event.getX()));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    int next=indexAt(event.getX());
                    boolean changed=next!=selected;
                    selected=next;
                    updateTextColors(selected);
                    moveThumb(next,true);
                    if(changed)HomeActivity.this.setPreviewMode(next);
                    performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    updateTextColors(selected);
                    moveThumb(selected,true);
                    return true;
            }
            return super.onTouchEvent(event);
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private View controls(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(20),0,dp(20),0);panel.setBackgroundColor(0xff121417);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setBaselineAligned(false);
        Button lock=wallpaperButton("锁屏壁纸",1),home=wallpaperButton("桌面壁纸",2);row.addView(lock,new LinearLayout.LayoutParams(0,dp(42),1));row.addView(new View(this),new LinearLayout.LayoutParams(dp(10),1));row.addView(home,new LinearLayout.LayoutParams(0,dp(42),1));
        panel.addView(row,new LinearLayout.LayoutParams(-1,dp(42)));
        Button apply=new Button(this);apply.setText("应用当前风格");apply.setAllCaps(false);apply.setTextColor(Color.WHITE);apply.setTextSize(16);apply.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);GradientDrawable blue=new GradientDrawable();blue.setColor(BLUE);blue.setCornerRadius(dp(22));apply.setBackground(blue);apply.setPadding(dp(18),0,dp(18),0);apply.setOnClickListener(v->applySelectedStyle());
        LinearLayout.LayoutParams applyLp=new LinearLayout.LayoutParams(-1,dp(46));applyLp.topMargin=dp(10);panel.addView(apply,applyLp);
        return panel;
    }
    private Button wallpaperButton(String text,int target){Button button=new Button(this);button.setText(text);button.setAllCaps(false);button.setTextColor(Color.WHITE);button.setTextSize(14);GradientDrawable bg=new GradientDrawable();bg.setColor(0xff343a46);bg.setCornerRadius(dp(20));button.setBackground(bg);button.setOnClickListener(v->pickWallpaper(target));return button;}
    private void setPreviewMode(int mode){previewMode=Math.max(0,Math.min(2,mode));previews.get(selectedCard).setPreviewMode(previewMode);updateTabs();}
    private void applySelectedStyle(){
        SharedPreferences applied=getSharedPreferences(SceneOptions.APPLIED,0),draft=getSharedPreferences(SceneOptions.DRAFT,0);
        new SceneOptions(applied).save(draft);
        pendingStyleName=NAMES[selectedCard];
        draft.edit().putInt("aod",STYLES[selectedCard]).putInt("cosmic",0).putInt("lock",0).putInt("home",6).apply();
        openWallpaperPicker();
    }
    private boolean applyDirectly(){
        try{
            RootBridge.applyWallpaperComponent(this);
            WallpaperManager manager=WallpaperManager.getInstance(this);
            WallpaperInfo info=manager.getWallpaperInfo();
            return info!=null&&new ComponentName(this,CleanWallpaper.class).equals(info.getComponent());
        }catch(Exception error){
            android.util.Log.w("AliveClean","Direct wallpaper apply failed",error);
            return false;
        }
    }
    private void openWallpaperPicker(){
        try{
            WallpaperManager manager=WallpaperManager.getInstance(this);
            previousWallpaperId=manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM);
            previousLockId=manager.getWallpaperId(WallpaperManager.FLAG_LOCK);
            awaitingWallpaperApply=true;
            pickerResultReliable=WallpaperPicker.open(this,new ComponentName(this,CleanWallpaper.class),20);
        }catch(RuntimeException error){
            awaitingWallpaperApply=false;
            android.util.Log.w("AliveClean","Wallpaper picker unavailable, trying direct apply",error);
            applyDirectlyOrReport();
        }
    }
    private void applyDirectlyOrReport(){
        if(applyDirectly())commitSelectedStyle();
        else Toast.makeText(this,"系统未切换到此壁纸，应用没有成功",Toast.LENGTH_LONG).show();
    }
    private void verifyPickerOrFallback(final int result){
        final WallpaperManager manager=WallpaperManager.getInstance(this);
        if(WallpaperPicker.applied(manager,new ComponentName(this,CleanWallpaper.class),result,pickerResultReliable,previousWallpaperId,previousLockId)){
            commitSelectedStyle();
            return;
        }
        if(result!=RESULT_OK){
            Toast.makeText(this,"未应用，已保留当前设置",Toast.LENGTH_LONG).show();
            return;
        }
        // Some OEM pickers return RESULT_OK before WallpaperManagerService
        // has exposed the new component. Recheck once before using the root fallback.
        new android.os.Handler(getMainLooper()).postDelayed(()->{
            if(isFinishing()||isDestroyed())return;
            if(WallpaperPicker.applied(manager,new ComponentName(this,CleanWallpaper.class),result,pickerResultReliable,previousWallpaperId,previousLockId))
                commitSelectedStyle();
            else applyDirectlyOrReport();
        },900);
    }
    private void commitSelectedStyle(){
        SharedPreferences applied=getSharedPreferences(SceneOptions.APPLIED,0),draft=getSharedPreferences(SceneOptions.DRAFT,0);
        if(new SceneOptions(draft).save(applied)){
            SceneProvider.changed(this);
            Toast.makeText(this,"已应用 "+(pendingStyleName==null?NAMES[selectedCard]:pendingStyleName),Toast.LENGTH_SHORT).show();
        }else Toast.makeText(this,"应用失败",Toast.LENGTH_SHORT).show();
    }
    private void setContinuousAod(boolean checked){SharedPreferences applied=getSharedPreferences(SceneOptions.APPLIED,0),draft=getSharedPreferences(SceneOptions.DRAFT,0);for(SharedPreferences p:new SharedPreferences[]{applied,draft})p.edit().putBoolean("sail_continuous_aod",checked).putBoolean("stereo_aod_animation",checked).putBoolean("cosmic_continuous_aod",checked).apply();SceneProvider.changed(this);}
    private void pickWallpaper(int target){
        int request=target==1?41:42;
        Intent intent=new Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI);intent.setType("image/*");
        for(android.content.pm.ResolveInfo app:getPackageManager().queryIntentActivities(intent,0)){
            String pkg=app.activityInfo.packageName;
            if(pkg.equals("com.coloros.gallery3d")||pkg.equals("com.oplus.gallery")||pkg.equals("com.google.android.apps.photos")){intent.setPackage(pkg);break;}
        }
        try{startActivityForResult(intent,request);return;}catch(ActivityNotFoundException ignored){}
        intent.setPackage(null);
        try{startActivityForResult(intent,request);return;}catch(ActivityNotFoundException e){
            Intent fallback=new Intent(Intent.ACTION_OPEN_DOCUMENT);fallback.addCategory(Intent.CATEGORY_OPENABLE);fallback.setType("image/*");
            try{startActivityForResult(fallback,request);}catch(ActivityNotFoundException ignored){Toast.makeText(this,"未找到相册",Toast.LENGTH_SHORT).show();}
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==20&&awaitingWallpaperApply){
            awaitingWallpaperApply=false;
            verifyPickerOrFallback(result);
            return;
        }
        if((request==41||request==42)&&result==RESULT_OK&&data!=null&&data.getData()!=null)pickWallpaperImage(data.getData(),request==41?1:2);
    }
    private void pickWallpaperImage(Uri uri,int target){
        new Thread(()->{File file=null;try{file=File.createTempFile("wallpaper-", ".image",getFilesDir());try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(file)){if(in==null)throw new IOException("无法读取图片");byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}if(BitmapFactory.decodeFile(file.getPath())==null)throw new IOException("图片格式不支持");File picked=file;runOnUiThread(()->commitWallpaper(picked,target));}catch(Exception error){if(file!=null)file.delete();runOnUiThread(()->Toast.makeText(this,"壁纸导入失败："+error.getMessage(),Toast.LENGTH_LONG).show());}}, "WallpaperImport").start();
    }
    private void commitWallpaper(File file,int target){SharedPreferences applied=getSharedPreferences(SceneOptions.APPLIED,0),draft=getSharedPreferences(SceneOptions.DRAFT,0);new SceneOptions(applied).save(draft);SharedPreferences.Editor edit=draft.edit().putInt("aod",STYLES[selectedCard]).putInt("cosmic",0).putInt("lock",0).putInt("home",6);if(target==1)edit.putString("photo",file.getName());else edit.putString("home_photo",file.getName()).putBoolean("home_follow_lock",false);edit.apply();if(new SceneOptions(draft).save(applied)){SceneProvider.changed(this);Toast.makeText(this,target==1?"已设置锁屏壁纸":"已设置桌面壁纸",Toast.LENGTH_SHORT).show();}}
    private View card(int position){FrameLayout shell=new FrameLayout(this);shell.setLayoutParams(new ViewGroup.LayoutParams(dp(CARD_WIDTH_DP),dp(CARD_HEIGHT_DP)));shell.setClipToOutline(true);shell.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View view,android.graphics.Outline outline){outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),dp(44));}});shell.setBackgroundColor(0xff101216);HomeScenePreview preview=new HomeScenePreview(this,0,STYLES[position]);previews.add(preview);shell.addView(preview,new FrameLayout.LayoutParams(-1,-1));View border=new View(this);GradientDrawable outline=new GradientDrawable();outline.setColor(Color.TRANSPARENT);outline.setStroke(dp(3),BLUE);outline.setCornerRadius(dp(44));border.setBackground(outline);border.setVisibility(View.INVISIBLE);shell.addView(border,new FrameLayout.LayoutParams(-1,-1));borders.add(border);shell.setClickable(false);shell.setFocusable(false);shell.setContentDescription(NAMES[position]+"动态息屏预览");return shell;}
    private void onCardSelected(int position){if(position<0||position>=previews.size())return;selectedCard=position;previews.get(position).setPreviewMode(previewMode);updatePreviews();updateSelectionUi();}
    private void updateSelectionUi(){if(styleTitle!=null)styleTitle.setText(NAMES[Math.max(0,Math.min(selectedCard,NAMES.length-1))]);for(int i=0;i<borders.size();i++)borders.get(i).setVisibility(i==selectedCard?View.VISIBLE:View.INVISIBLE);updateTabs();}
    private void updatePreviews(){Rect bounds=new Rect();for(int i=0;i<cards.size();i++){View card=cards.get(i);boolean visible=resumed&&card.isShown()&&card.getGlobalVisibleRect(bounds)&&bounds.width()>dp(12);HomeScenePreview preview=previews.get(i);boolean selected=i==selectedCard;preview.update(visible,selected);}}
    private void allowClockStartup(){if(getSharedPreferences("native_clock_startup",0).getBoolean("allowed",false))return;new Thread(()->{try{RootBridge.allowClockStartup(this);getSharedPreferences("native_clock_startup",0).edit().putBoolean("allowed",true).apply();}catch(Exception error){android.util.Log.w("OpenAliveClock","Clock startup setup unavailable",error);}},"OpenAliveClockStartup").start();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onResume(){super.onResume();resumed=true;if(carousel!=null)carousel.post(this::updatePreviews);}
    @Override protected void onPause(){resumed=false;for(HomeScenePreview preview:previews)preview.update(false,false);super.onPause();}
    @Override protected void onDestroy(){for(HomeScenePreview preview:previews)preview.release();super.onDestroy();}
}
