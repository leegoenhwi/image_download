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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ListAdapter extends RecyclerView.Adapter<ListAdapter.CustomViewHolder> {

    /** 다운로드 상태만 바뀔 때 썸네일은 다시 그리지 않도록 쓰는 표시 */
    private static final Object PAYLOAD_STATE = new Object();

    /** 저장 권한 확인 후 실행 (권한이 없으면 요청하고, 허용되면 action 실행) */
    public interface DownloadGate {
        void runWithPermission(Runnable action);
    }

    /** 목록/다운로드 상태가 바뀌면 호출 (상단 제목 갱신용) */
    public interface Listener {
        void onListChanged();
    }

    private final ArrayList<list> mList;
    /** 같은 이미지(크기만 다른 주소)를 하나로 합치기 위한 key → 항목 */
    private final Map<String, list> byKey = new HashMap<String, list>();
    private final Context context;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(3);

    private String referer;
    private DownloadGate gate;
    private Listener listener;
    /** 목록 끝쪽의 로고/아이콘 등 장식 이미지 개수 (본문 이미지는 이들보다 앞에 넣는다) */
    private int minorCount = 0;
    /** "전체 다운로드" 후에 새로 찾은 이미지도 자동으로 받는다 */
    private boolean autoDownload = false;
    private int batchTotal = 0, batchDone = 0, batchFailed = 0;

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

    public void setDownloadGate(DownloadGate gate) {
        this.gate = gate;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public CustomViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.list, viewGroup, false);
        return new CustomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CustomViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
        } else {
            render(holder, mList.get(position));   // 진행률/상태만 갱신
        }
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
                withPermission(new Runnable() {
                    @Override
                    public void run() {
                        download(item, false);
                    }
                });
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

    public int getDoneCount() {
        int n = 0;
        for (list item : mList) {
            if (item.getState() == list.DONE) n++;
        }
        return n;
    }

    /**
     * 이미지 추가 (UI 스레드에서만 호출). 새 항목이면 true.
     * - 이미 있는 이미지의 더 큰 버전이면 아직 받기 전인 항목의 주소를 큰 쪽으로 바꾼다
     * - 본문 이미지는 목록 끝의 로고/아이콘보다 앞에 넣는다
     */
    public boolean addItem(String url) {
        if (url == null) return false;
        String key = ImageVariants.key(url);
        list existing = byKey.get(key);
        if (existing != null) {
            if ((existing.getState() == list.IDLE || existing.getState() == list.FAILED)
                    && ImageVariants.isBigger(url, existing.getName())) {
                existing.setName(url);
                int pos = mList.indexOf(existing);
                if (pos >= 0) notifyItemChanged(pos);
            }
            return false;
        }
        list item = new list();
        item.setName(url);
        byKey.put(key, item);
        int pos;
        if (ImageExtractor.isMinor(url)) {
            pos = mList.size();
            minorCount++;
        } else {
            pos = mList.size() - minorCount;
        }
        mList.add(pos, item);
        notifyItemInserted(pos);
        if (autoDownload) download(item, true);
        changed();
        return true;
    }

    /** 아직 받지 않은 모든 이미지를 받는다. 이후 새로 찾은 이미지도 자동으로 받는다. */
    public void downloadAll() {
        withPermission(new Runnable() {
            @Override
            public void run() {
                autoDownload = true;
                int count = 0;
                for (list item : mList) {
                    if (item.getState() == list.IDLE || item.getState() == list.FAILED) {
                        download(item, true);
                        count++;
                    }
                }
                Toast.makeText(context, count + " images downloading...", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void withPermission(Runnable action) {
        if (gate != null) gate.runWithPermission(action);
        else action.run();
    }

    private void download(final list item, final boolean batch) {
        if (item.getState() == list.DOWNLOADING || item.getState() == list.DONE) return;
        item.setState(list.DOWNLOADING);
        item.setProgress(0);
        update(item);
        if (batch) batchTotal++;

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
                        changed();
                        if (!batch) {
                            if (state == list.FAILED) Toast.makeText(context, "download failed", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // 여러 장을 받을 때는 실패 알림을 하나씩 띄우지 않고 끝나면 한 번에 요약
                        batchDone++;
                        if (state == list.FAILED) batchFailed++;
                        if (batchDone == batchTotal) {
                            String msg = (batchDone - batchFailed) + "장 저장"
                                    + (batchFailed > 0 ? ", " + batchFailed + "장 실패 (실패한 항목을 누르면 다시 시도)" : "");
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
                            batchTotal = batchDone = batchFailed = 0;
                        }
                    }
                });
            }
        });
    }

    private void update(list item) {
        int pos = mList.indexOf(item);
        if (pos >= 0) notifyItemChanged(pos, PAYLOAD_STATE);
    }

    private void changed() {
        if (listener != null) listener.onListChanged();
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
