package com.personal_project.image_download.support;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.personal_project.image_download.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 결과 화면의 썸네일 그리드.
 * - 누르면 선택, 길게 누르면 미리보기, 저장에 실패한 칸을 누르면 다시 시도
 * - 본문 이미지가 먼저, 로고/아이콘 등은 "로고·아이콘 등" 제목 아래 뒤쪽에
 */
public class ListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_IMAGE = 0;
    private static final int TYPE_HEADER = 1;
    /** 다운로드 상태/선택만 바뀔 때 썸네일은 다시 그리지 않도록 쓰는 표시 */
    private static final Object PAYLOAD_STATE = new Object();

    /** 저장 권한 확인 후 실행 (권한이 없으면 요청하고, 허용되면 action 실행) */
    public interface DownloadGate {
        void runWithPermission(Runnable action);
    }

    public interface Listener {
        /** 목록/선택/다운로드 상태가 바뀜 (상단 제목, 하단 버튼 갱신용) */
        void onListChanged();

        /** 길게 눌러 미리보기 */
        void onPreview(list item);

        /** 저장이 끝남. single 은 미리보기 등에서 한 장만 저장한 경우 */
        void onSaved(int saved, int failed, boolean single);
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
    /** "모두 저장" 후에 새로 찾은 이미지도 자동으로 받는다 */
    private boolean autoDownload = false;
    private int batchTotal = 0, batchDone = 0, batchFailed = 0;

    public ListAdapter(Context c, ArrayList<list> list) {
        this.mList = list;
        this.context = c;
    }

    static class ImageHolder extends RecyclerView.ViewHolder {
        final ImageView photo;
        final View overlay;
        final ImageView check;
        final TextView badge;
        final ProgressBar progress;

        ImageHolder(View view) {
            super(view);
            photo = (ImageView) view.findViewById(R.id.photo);
            overlay = view.findViewById(R.id.selected_overlay);
            check = (ImageView) view.findViewById(R.id.check);
            badge = (TextView) view.findViewById(R.id.badge);
            progress = (ProgressBar) view.findViewById(R.id.progress);
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView text;

        HeaderHolder(View view) {
            super(view);
            text = (TextView) view.findViewById(R.id.header_text);
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

    // ---- 위치 계산 (본문 이미지 → [로고·아이콘 제목] → 장식 이미지) ----------------------------

    private boolean hasHeader() {
        return minorCount > 0 && minorCount < mList.size();
    }

    private int mainCount() {
        return mList.size() - minorCount;
    }

    /** 그리드에서 한 줄 전체를 차지하는 제목 칸인지 */
    public boolean isHeader(int position) {
        return hasHeader() && position == mainCount();
    }

    private int toIndex(int position) {
        if (!hasHeader() || position < mainCount()) return position;
        return position == mainCount() ? -1 : position - 1;
    }

    private int toPosition(int index) {
        return !hasHeader() || index < mainCount() ? index : index + 1;
    }

    @Override
    public int getItemCount() {
        return mList.size() + (hasHeader() ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        return isHeader(position) ? TYPE_HEADER : TYPE_IMAGE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_header, parent, false));
        }
        return new ImageHolder(inflater.inflate(R.layout.item_image, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (holder instanceof ImageHolder && !payloads.isEmpty()) {
            int index = toIndex(position);
            if (index >= 0) render((ImageHolder) holder, mList.get(index));   // 상태/선택만 갱신
            return;
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).text.setText(context.getString(R.string.minor_header, minorCount));
            return;
        }
        final ImageHolder h = (ImageHolder) holder;
        final list item = mList.get(toIndex(position));

        Glide.with(context)
                .load(glideModel(item.getName()))
                .override(360, 360)
                .centerCrop()
                .placeholder(R.color.colorthumb)
                .error(R.drawable.ic_broken_image_24)
                .into(h.photo);
        render(h, item);

        h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (item.getState() == list.FAILED) {
                    saveOne(item);   // 실패한 칸을 누르면 다시 시도
                } else if (item.getState() == list.IDLE) {
                    item.setSelected(!item.isSelected());
                    update(item);
                    changed();
                }
            }
        });
        h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (listener != null) listener.onPreview(item);
                return true;
            }
        });
    }

    /** Glide 로 읽을 대상. 사이트가 Referer/쿠키 없이는 이미지를 막는 경우가 있어 헤더를 함께 보낸다. */
    public Object glideModel(String url) {
        if (url.startsWith("data:")) return url;   // 페이지에 박힌 base64 이미지
        LazyHeaders.Builder headers = new LazyHeaders.Builder().addHeader("User-Agent", ImageSaver.USER_AGENT);
        if (referer != null) headers.addHeader("Referer", referer);
        String cookie = ImageSaver.cookieFor(url);
        if (cookie != null && !cookie.isEmpty()) headers.addHeader("Cookie", cookie);
        return new GlideUrl(url, headers.build());
    }

    private void render(ImageHolder h, list item) {
        int state = item.getState();
        boolean selectable = state == list.IDLE || state == list.FAILED;
        boolean selected = selectable && item.isSelected();
        h.overlay.setVisibility(selected ? View.VISIBLE : View.GONE);
        h.check.setVisibility(state == list.IDLE ? View.VISIBLE : View.GONE);
        h.check.setBackgroundResource(selected ? R.drawable.bg_check_on : R.drawable.bg_check_off);
        h.check.setImageResource(selected ? R.drawable.ic_check_16 : 0);
        h.progress.setVisibility(state == list.DOWNLOADING ? View.VISIBLE : View.GONE);
        h.progress.setProgress(item.getProgress());
        if (state == list.DONE) {
            h.badge.setVisibility(View.VISIBLE);
            h.badge.setBackgroundResource(R.drawable.bg_badge_done);
            h.badge.setText(R.string.badge_saved);
        } else if (state == list.FAILED) {
            h.badge.setVisibility(View.VISIBLE);
            h.badge.setBackgroundResource(R.drawable.bg_badge_failed);
            h.badge.setText(R.string.badge_failed);
        } else {
            h.badge.setVisibility(View.GONE);
        }
    }

    // ---- 목록 ---------------------------------------------------------------------------------

    /**
     * 찾은 이미지 추가 (UI 스레드). 새로 추가된 개수를 돌려준다.
     * - 이미 있는 이미지의 더 큰 버전이면 아직 받기 전인 항목의 주소를 큰 쪽으로 바꾼다
     * - 본문 이미지는 로고/아이콘보다 앞에 넣는다
     */
    public int addItems(Collection<String> urls) {
        if (urls == null) return 0;
        List<list> added = new ArrayList<list>();
        boolean replaced = false;
        for (String url : urls) {
            if (url == null) continue;
            String key = ImageVariants.key(url);
            list existing = byKey.get(key);
            if (existing != null) {
                if ((existing.getState() == list.IDLE || existing.getState() == list.FAILED)
                        && ImageVariants.isBigger(url, existing.getName())) {
                    existing.setName(url);
                    replaced = true;
                }
                continue;
            }
            list item = new list();
            item.setName(url);
            byKey.put(key, item);
            if (ImageExtractor.isMinor(url)) {
                mList.add(item);
                minorCount++;
            } else {
                mList.add(mainCount(), item);
            }
            added.add(item);
        }
        if (!added.isEmpty() || replaced) {
            notifyDataSetChanged();
            if (autoDownload) {
                for (list item : added) download(item, true);
            }
            changed();
        }
        return added.size();
    }

    public int getImageCount() {
        return mList.size();
    }

    public int getSelectedCount() {
        int n = 0;
        for (list item : mList) {
            if (item.isSelected() && item.getState() == list.IDLE) n++;
        }
        return n;
    }

    /** 아직 저장하지 않은(다시 시도할 수 있는) 이미지 수 */
    public int getPendingCount() {
        int n = 0;
        for (list item : mList) {
            if (item.getState() == list.IDLE || item.getState() == list.FAILED) n++;
        }
        return n;
    }

    public int getDoneCount() {
        int n = 0;
        for (list item : mList) {
            if (item.getState() == list.DONE) n++;
        }
        return n;
    }

    /** 진행 중인 묶음 저장: {끝난 수, 전체 수}. 진행 중이 아니면 null */
    public int[] getBatchProgress() {
        return batchTotal > 0 ? new int[]{batchDone, batchTotal} : null;
    }

    public boolean isAllSelected() {
        int selectable = 0;
        for (list item : mList) {
            if (item.getState() == list.IDLE) {
                selectable++;
                if (!item.isSelected()) return false;
            }
        }
        return selectable > 0;
    }

    public void setAllSelected(boolean selected) {
        for (list item : mList) {
            if (item.getState() == list.IDLE) item.setSelected(selected);
        }
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_STATE);
        changed();
    }

    // ---- 저장 ---------------------------------------------------------------------------------

    /** 선택한 이미지를 저장. 선택이 없으면 전부 저장하고, 이후 새로 찾은 이미지도 자동으로 받는다. */
    public void saveSelectedOrAll() {
        withPermission(new Runnable() {
            @Override
            public void run() {
                List<list> targets = new ArrayList<list>();
                for (list item : mList) {
                    if (item.isSelected() && item.getState() == list.IDLE) targets.add(item);
                }
                if (targets.isEmpty()) {
                    autoDownload = true;
                    for (list item : mList) {
                        if (item.getState() == list.IDLE || item.getState() == list.FAILED) targets.add(item);
                    }
                }
                for (list item : targets) {
                    item.setSelected(false);
                    download(item, true);
                }
                changed();
            }
        });
    }

    /** 한 장만 저장 (미리보기의 저장 버튼, 실패한 칸 다시 시도) */
    public void saveOne(final list item) {
        withPermission(new Runnable() {
            @Override
            public void run() {
                item.setSelected(false);
                download(item, false);
                changed();
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
                        if (!batch) {
                            changed();
                            if (listener != null) listener.onSaved(state == list.DONE ? 1 : 0, state == list.FAILED ? 1 : 0, true);
                            return;
                        }
                        // 여러 장을 받을 때는 하나씩 알리지 않고 끝나면 한 번에 요약
                        batchDone++;
                        if (state == list.FAILED) batchFailed++;
                        if (batchDone == batchTotal) {
                            int saved = batchDone - batchFailed, failed = batchFailed;
                            batchTotal = batchDone = batchFailed = 0;
                            changed();
                            if (listener != null) listener.onSaved(saved, failed, false);
                        } else {
                            changed();
                        }
                    }
                });
            }
        });
    }

    private void update(list item) {
        int index = mList.indexOf(item);
        if (index >= 0) notifyItemChanged(toPosition(index), PAYLOAD_STATE);
    }

    private void changed() {
        if (listener != null) listener.onListChanged();
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
