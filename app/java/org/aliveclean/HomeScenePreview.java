package org.aliveclean;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.FrameLayout;

/** A persistent preview surface controlled by the selected card and preview mode. */
final class HomeScenePreview extends FrameLayout implements TextureView.SurfaceTextureListener {
    private static final int REVERSE_DURATION_MS=1400;
    private static final int FREEZE_DELAY_MS=180;
    private final TextureView texture;
    private final int cosmicVariant;
    private final int aodStyle;
    private RenderLoop renderer;
    private Surface surface;
    private boolean active;
    private boolean selected;
    private boolean awake;
    private int previewMode=1;

    private final Runnable wake=()->startWake();
    private final Runnable freeze=()->{
        if((!active||!selected)&&renderer!=null)renderer.visible(false);
    };

    HomeScenePreview(Context context,int cosmicVariant,int aodStyle){
        super(context);this.cosmicVariant=cosmicVariant;this.aodStyle=aodStyle;
        setBackgroundColor(Color.BLACK);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View view,Outline outline){outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),dp(24));}});
        texture=new TextureView(context);texture.setOpaque(true);texture.setSurfaceTextureListener(this);addView(texture,new LayoutParams(-1,-1));
    }

    void setPreviewMode(int mode){
        int next=Math.max(0,Math.min(2,mode));
        if(previewMode==next)return;
        previewMode=next;
        if(selected&&renderer!=null){
            removeCallbacks(wake);renderer.visible(true);
            if(previewMode==0){renderer.mode(0,true);awake=false;}
            else if(awake)renderer.mode(previewMode,true);
            else startWake();
        }
    }

    private void startWake(){
        if(!active||!selected||renderer==null||previewMode==0)return;
        renderer.visible(true);
        // The card was resting on AOD. Snap that state first, then run the
        // official wake transition every time this card becomes selected.
        renderer.mode(0,false);
        renderer.mode(previewMode,true);
        awake=true;
    }

    void update(boolean visible,boolean isSelected){
        boolean nowSelected=visible&&isSelected;
        boolean becameSelected=nowSelected&&!selected;
        active=visible;selected=nowSelected;
        removeCallbacks(wake);removeCallbacks(freeze);
        if(renderer==null){if(visible&&texture.isAvailable())startRenderer();return;}
        if(!visible){renderer.visible(false);return;}
        renderer.visible(true);
        if(selected){
            if(previewMode==0){renderer.mode(0,true);awake=false;}
            else if(becameSelected||!awake)startWake();
        }else{
            boolean reversing=awake;
            if(reversing){renderer.mode(0,true);awake=false;}
            postDelayed(freeze,reversing?REVERSE_DURATION_MS:FREEZE_DELAY_MS);
        }
    }

    private void startRenderer(){
        if(renderer!=null||!texture.isAvailable())return;
        surface=new Surface(texture.getSurfaceTexture());
        renderer=aodStyle>=0?new RenderLoop(getContext(),0,aodStyle):cosmicVariant>=0?new RenderLoop(getContext(),cosmicVariant):new RenderLoop(getContext());
        renderer.mode(0,false);renderer.attach(surface,texture.getWidth(),texture.getHeight());renderer.visible(true);awake=false;
        if(selected&&previewMode!=0)startWake();else postDelayed(freeze,FREEZE_DELAY_MS);
    }

    void release(){removeCallbacks(wake);removeCallbacks(freeze);if(renderer!=null){renderer.close();renderer=null;}if(surface!=null){surface.release();surface=null;}}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    public void onSurfaceTextureAvailable(SurfaceTexture value,int width,int height){startRenderer();}
    public void onSurfaceTextureSizeChanged(SurfaceTexture value,int width,int height){release();startRenderer();}
    public boolean onSurfaceTextureDestroyed(SurfaceTexture value){release();return true;}
    public void onSurfaceTextureUpdated(SurfaceTexture value){}
}
