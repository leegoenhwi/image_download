package com.personal_project.image_download.support;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.personal_project.image_download.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ListAdapter extends RecyclerView.Adapter<ListAdapter.CustomViewHolder> {

    private final ArrayList<list> mList;
    private final Set<String> known = new HashSet<String>();
    private final Context context;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(3);

    private String referer;

    public ListAdapter(Context c, ArrayList<list> list) {
        this.mList = list;
        this.context = c;
    }

    public class CustomViewHolder extends RecyclerView.ViewHolder {
        protected ImageView photo;
        protected TextView name;
        protected ImageView download_icon;
        protected ProgressBar progressBar;

        public CustomViewHolder(View view) {
            super(view);
            this.photo = (ImageView) view.findViewById(R.id.photo);
            this.name = (TextView) view.findViewById(R.id.name);
            this.download_icon = (ImageView) view.findViewById(R.id.download_icon);
            this.progressBar = view.findViewById(R.id.progress);
        }
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    @NonNull
    @Override
    public CustomViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.list, viewGroup, false);
        return new CustomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final CustomViewHolder holder, int position) {
        final list item = mList.get(position);

        Object model;
        if (item.getName().startsWith("data:")) {
            model = item.getName();   // 페이지에 박힌 base64 이미지
        } else {
            // 일부 사이트는 Referer/쿠키 없이는 이미지 로드를 막으므로 헤더를 함께 보낸다
            LazyHeaders.Builder headers = new LazyHeaders.Builder().addHeader("User-Agent", ImageSaver.USER_AGENT);
            if (referer != null) headers.addHeader("Referer", referer);
            String cookie = ImageSaver.cookieFor(item.getName());
            if (cookie != null && !cookie.isEmpty()) headers.addHeader("Cookie", cookie);
            model = new GlideUrl(item.getName(), headers.build());
        }
        Glide.with(context)
                .load(model)
                .override(200, 200)
                .error(R.drawable.ic_do_not_disturb_alt_black_24dp)
                .into(holder.photo);

        String name = item.getName();
        holder.name.setText(name.startsWith("data:") ? "inline image (" + name.length() / 1366 + " KB)" : name);
        render(holder, item);

        holder.download_icon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                download(item);
            }
        });
    }

    private void render(CustomViewHolder holder, list item) {
        Drawable icon;
        switch (item.getState()) {
            case list.DONE:
                icon = ContextCompat.getDrawable(context, R.drawable.check).mutate();
                icon.setColorFilter(ContextCompat.getColor(context, R.color.colormain), PorterDuff.Mode.MULTIPLY);
                holder.progressBar.setVisibility(View.VISIBLE);
                holder.progressBar.setProgress(100);
                break;
            case list.DOWNLOADING:
                icon = ContextCompat.getDrawable(context, R.drawable.download).mutate();
                icon.setColorFilter(ContextCompat.getColor(context, R.color.colorcilipboard), PorterDuff.Mode.MULTIPLY);
                holder.progressBar.setVisibility(View.VISIBLE);
                holder.progressBar.setProgress(item.getProgress());
                break;
            case list.FAILED:
                icon = ContextCompat.getDrawable(context, R.drawable.ic_do_not_disturb_alt_black_24dp);
                holder.progressBar.setVisibility(View.GONE);
                break;
            default:
                icon = ContextCompat.getDrawable(context, R.drawable.download).mutate();
                icon.setColorFilter(ContextCompat.getColor(context, R.color.colorcilipboard), PorterDuff.Mode.MULTIPLY);
                holder.progressBar.setVisibility(View.GONE);
        }
        holder.download_icon.setImageDrawable(icon);
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    /** 중복 주소는 무시한다. UI 스레드에서만 호출. 새로 추가되면 true. */
    public boolean addItem(String url) {
        if (url == null || !known.add(url)) return false;
        list item = new list();
        item.setName(url);
        mList.add(item);
        notifyItemInserted(mList.size() - 1);
        return true;
    }

    /** 아직 받지 않은 모든 이미지를 순서대로 다운로드한다. */
    public void downloadAll() {
        int count = 0;
        for (list item : mList) {
            if (item.getState() == list.IDLE || item.getState() == list.FAILED) {
                download(item);
                count++;
            }
        }
        Toast.makeText(context, count + " images downloading...", Toast.LENGTH_SHORT).show();
    }

    private void download(final list item) {
        if (item.getState() == list.DOWNLOADING || item.getState() == list.DONE) return;
        item.setState(list.DOWNLOADING);
        item.setProgress(0);
        update(item);

        pool.execute(new Runnable() {
            @Override
            public void run() {
                int result = list.DONE;
                try {
                    ImageSaver.save(context, item.getName(), referer, new ImageSaver.Progress() {
                        @Override
                        public void onProgress(final int percent) {
                            ui.post(new Runnable() {
                                @Override
                                public void run() {
                                    item.setProgress(percent);
                                    update(item);
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    result = list.FAILED;
                }
                final int state = result;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        item.setState(state);
                        update(item);
                        if (state == list.FAILED) {
                            Toast.makeText(context, "download failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    private void update(list item) {
        int pos = mList.indexOf(item);
        if (pos >= 0) notifyItemChanged(pos);
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
