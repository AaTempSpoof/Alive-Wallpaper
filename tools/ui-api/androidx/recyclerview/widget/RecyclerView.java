package androidx.recyclerview.widget;

/** Compile-only signatures. The APK runs the unchanged ROM implementation. */
public abstract class RecyclerView extends android.view.ViewGroup {
    public RecyclerView(android.content.Context c){super(c);}
    public abstract void setAdapter(Adapter adapter);
    public abstract void setLayoutManager(LayoutManager manager);
    public abstract void setItemAnimator(ItemAnimator animator);
    public abstract void addOnScrollListener(OnScrollListener listener);
    public abstract LayoutManager getLayoutManager();
    public abstract void scrollToPosition(int position);
    public static class OnScrollListener {
        public void onScrolled(RecyclerView view,int dx,int dy){}
        public void onScrollStateChanged(RecyclerView view,int state){}
    }
    public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams {
        public LayoutParams(int width,int height){super(width,height);}
    }
    public static abstract class ItemAnimator {}
    public static abstract class LayoutManager {}
    public static abstract class ViewHolder {
        public final android.view.View itemView;
        public ViewHolder(android.view.View view){itemView=view;}
    }
    public static abstract class Adapter<VH extends ViewHolder> {
        public abstract VH onCreateViewHolder(android.view.ViewGroup parent,int type);
        public abstract void onBindViewHolder(VH holder,int position);
        public abstract int getItemCount();
        public final void notifyDataSetChanged(){}
    }
}
