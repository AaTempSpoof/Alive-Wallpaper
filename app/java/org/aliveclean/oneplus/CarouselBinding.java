package org.aliveclean.oneplus;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Loaded beside the original OnePlus classes so AndroidX types stay isolated. */
public final class CarouselBinding {
    public static View create(Context context,IntFunction<View> cards,int count,int spacing,Runnable changed,IntConsumer settled)throws Exception{
        ClassLoader loader=context.getClassLoader();
        RecyclerView view=(RecyclerView)loader.loadClass("androidx.recyclerview.widget.COUIRecyclerView").getConstructor(Context.class).newInstance(context);
        Object manager=loader.loadClass("com.oplus.wallpapers.business.personalize.ui.WallpaperCarouselLayoutManager")
            .getConstructor(RecyclerView.class,int.class,int.class,boolean.class).newInstance(view,3,spacing,true);
        manager.getClass().getMethod("k0").invoke(manager);
        view.setLayoutManager((RecyclerView.LayoutManager)manager);view.setItemAnimator(null);
        view.setClipChildren(false);view.setClipToPadding(false);view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setAdapter(new RecyclerView.Adapter<Holder>(){
            public int getItemCount(){return count;}
            public Holder onCreateViewHolder(ViewGroup parent,int type){
                android.widget.FrameLayout shell=new android.widget.FrameLayout(context);
                shell.setLayoutParams(new RecyclerView.LayoutParams(-2,-2));return new Holder(shell);
            }
            public void onBindViewHolder(Holder holder,int position){
                View content=cards.apply(position);ViewGroup old=(ViewGroup)content.getParent();if(old!=null)old.removeView(content);
                ViewGroup shell=(ViewGroup)holder.itemView;shell.removeAllViews();
                shell.addView(content,new ViewGroup.LayoutParams(content.getLayoutParams().width,content.getLayoutParams().height));
            }
        });
        view.addOnScrollListener(new RecyclerView.OnScrollListener(){
            @Override public void onScrolled(RecyclerView source,int dx,int dy){changed.run();}
            @Override public void onScrollStateChanged(RecyclerView source,int state){
                changed.run();
                if(state==0)source.post(()->snapNearest(source,manager,settled));
            }
        });
        final boolean[] initialised={false};
        view.getViewTreeObserver().addOnGlobalLayoutListener(()->{
            changed.run();
            if(!initialised[0]&&view.getChildCount()>0){initialised[0]=true;snapNearest(view,manager,settled);}
        });
        return view;
    }
    private static void snapNearest(RecyclerView view,Object manager,IntConsumer settled){
        int center=view.getWidth()/2,nearest=-1,distance=Integer.MAX_VALUE;
        for(int i=0;i<view.getChildCount();i++){
            View child=view.getChildAt(i);int position=childPosition(view,child);
            int childCenter=(child.getLeft()+child.getRight())/2;int current=Math.abs(childCenter-center);
            if(position>=0&&current<distance){distance=current;nearest=position;}
        }
        if(nearest<0)return;
        android.util.Log.i("AliveClean","Carousel centered position="+nearest+" distance="+distance);
        if(distance<=8){settled.accept(nearest);return;}
        try{manager.getClass().getMethod("z0",RecyclerView.class,int.class).invoke(manager,view,nearest);}
        catch(Exception error){android.util.Log.w("AliveClean","Carousel snap unavailable",error);}
    }
    private static int childPosition(RecyclerView view,View child){
        try{return (Integer)view.getClass().getMethod("getChildAdapterPosition",View.class).invoke(view,child);}
        catch(Exception error){return -1;}
    }
    private static final class Holder extends RecyclerView.ViewHolder {Holder(View view){super(view);}}
}
