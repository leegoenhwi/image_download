package com.personal_project.image_download;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.personal_project.image_download.support.ImageExtractor;
import com.personal_project.image_download.support.ImageSaver;
import com.personal_project.image_download.support.ListAdapter;
import com.personal_project.image_download.support.list;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 입력된 주소(또는 브라우저에서 "공유" 로 받은 글)에서 이미지를 찾아 썸네일 그리드로 보여준다.
 * 두 가지 방법을 함께 사용한다.
 *  1) Jsoup 으로 원본 HTML 파싱 (빠름, 먼저 목록을 보여줌)
 *  2) WebView 로 JavaScript 까지 실행한 뒤의 HTML 파싱 (동적으로 생성되는 이미지 대응)
 * 두 결과는 합쳐지고, 같은 이미지의 크기만 다른 주소는 큰 쪽 하나로 정리된다.
 */
public class Download extends AppCompatActivity implements View.OnClickListener {

    private static final int SOURCES = 2;
    private static final int GRID_COLUMNS = 3;
    private static final int RESULT_BAR_MS = 6000;

    /**
     * WebView 안에서 실행하는 스크립트.
     * 1) 0.12초마다 0.9화면씩, 처음 페이지 높이까지(최대 40화면) 스크롤해서 lazy 이미지를 깨운다.
     * 2) 0.8초 기다린 뒤 수집:
     *    - 같은 도메인 iframe 과 Shadow DOM 의 HTML → Android.addFrame(주소, html)
     *    - 외부 CSS 파일까지 적용된 배경 이미지(::before/::after 포함, 48px 초과 요소만)
     *    - 메인 HTML → Android.getHtml(주소, html, 배경이미지목록)
     */
    static final String SCROLL_AND_CAPTURE_JS = "javascript:(function(){"
            + "if(window.__imgdl)return;window.__imgdl=1;"
            + "var y=0,h=document.documentElement.scrollHeight,step=Math.max(window.innerHeight*0.9,300),n=0;"
            + "var t=setInterval(function(){y+=step;window.scrollTo(0,y);n++;"
            + "if(y>=h||n>=40){clearInterval(t);setTimeout(collect,800);}},120);"
            + "function collect(){"
            + "var extra=[],seen={},count=0;"
            + "function push(u){if(u&&!seen[u]){seen[u]=1;extra.push(u);}}"
            + "function bg(el,pseudo){try{var w=el.ownerDocument.defaultView,s=w.getComputedStyle(el,pseudo).backgroundImage;"
            + "if(!s||s==='none'||s.indexOf('url(')<0)return;var r=el.getBoundingClientRect();if(r.width<=48||r.height<=48)return;"
            + "var re=/url\\([\"']?([^\"')]+)[\"']?\\)/g,m;while((m=re.exec(s)))push(m[1]);}catch(e){}}"
            + "function walk(root,base){var els=root.querySelectorAll('*');"
            + "for(var i=0;i<els.length&&count<30000;i++,count++){var el=els[i];bg(el,null);bg(el,'::before');bg(el,'::after');"
            + "if(el.shadowRoot){try{window.Android.addFrame(base,el.shadowRoot.innerHTML);}catch(e){}walk(el.shadowRoot,base);}"
            + "if(el.tagName==='IFRAME'||el.tagName==='FRAME'){try{var d=el.contentDocument;"
            + "if(d&&d.documentElement){window.Android.addFrame(d.location.href,d.documentElement.outerHTML);walk(d,d.location.href);}}catch(e){}}}}"
            + "walk(document,location.href);"
            + "window.Android.getHtml(location.href,document.documentElement.outerHTML,extra.join('\\n'));}"
            + "})()";

    /** 이미지가 하나도 없는 캡처는 봇 확인/리다이렉트 중간 페이지일 수 있으므로 이만큼 더 기다린다 */
    private static final int EMPTY_CAPTURE_GRACE_MS = 6000;
    /** onPageFinished 가 늦게 오는 페이지(광고/느린 스크립트)는 이 시간이 지나면 그냥 수집 시작 */
    private static final int FORCE_CAPTURE_MS = 10000;

    private static final int REQUEST_STORAGE = 7;

    /**
     * WebView 의 이미지 요청에 실제 이미지 대신 돌려주는 1x1 투명 PNG.
     * 이미지를 막아버리면 "이미지가 로드된 뒤 다음 이미지를 넣는" 페이지 스크립트가 멈추므로,
     * 막지 않고 즉시 이 작은 이미지로 응답한다 (데이터는 쓰지 않으면서 페이지는 정상 진행).
     */
    private static final byte[] PIXEL_PNG = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAXpeqz8AAAAASUVORK5CYII=", Base64.DEFAULT);
    private static final Pattern IMAGE_PATH = Pattern.compile(
            "\\.(jpe?g|png|gif|webp|avif|bmp|svg|ico)$", Pattern.CASE_INSENSITIVE);

    private final Handler ui = new Handler(Looper.getMainLooper());

    private String htmlpageURL;
    /** 저장 권한을 요청하는 동안 기다리는 다운로드 */
    private Runnable pendingDownload;

    private TextView title;
    private TextView subtitle;
    private TextView selectAllButton;
    private ProgressBar searchProgress;
    private RecyclerView recyclerView;
    private View loadingView;
    private View stateView;
    private ImageView stateIcon;
    private TextView stateTitle;
    private TextView stateMessage;
    private View bottomBar;
    private TextView bottomInfo;
    private TextView saveButton;
    private View resultBar;
    private TextView resultText;
    private WebView webView;

    private ListAdapter listAdapter;

    private int finishedSources = 0;
    private boolean webCaptured = false;
    private boolean scrollStarted = false;
    private boolean destroyed = false;

    private final Runnable hideResultBar = new Runnable() {
        @Override
        public void run() {
            resultBar.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download);

        title = findViewById(R.id.download_title);
        subtitle = findViewById(R.id.download_subtitle);
        selectAllButton = findViewById(R.id.select_all_button);
        searchProgress = findViewById(R.id.search_progress);
        recyclerView = findViewById(R.id.recycler);
        loadingView = findViewById(R.id.loading_view);
        stateView = findViewById(R.id.state_view);
        stateIcon = findViewById(R.id.state_icon);
        stateTitle = findViewById(R.id.state_title);
        stateMessage = findViewById(R.id.state_message);
        bottomBar = findViewById(R.id.bottom_bar);
        bottomInfo = findViewById(R.id.bottom_info);
        saveButton = findViewById(R.id.save_button);
        resultBar = findViewById(R.id.result_bar);
        resultText = findViewById(R.id.result_text);

        findViewById(R.id.download_back_arrow).setOnClickListener(this);
        findViewById(R.id.state_retry).setOnClickListener(this);
        findViewById(R.id.result_action).setOnClickListener(this);
        selectAllButton.setOnClickListener(this);
        saveButton.setOnClickListener(this);

        listAdapter = new ListAdapter(this, new ArrayList<list>());
        final GridLayoutManager grid = new GridLayoutManager(this, GRID_COLUMNS);
        grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return listAdapter.isHeader(position) ? GRID_COLUMNS : 1;   // "로고·아이콘 등" 제목은 한 줄 전체
            }
        });
        recyclerView.setLayoutManager(grid);
        recyclerView.setAdapter(listAdapter);
        listAdapter.setListener(new ListAdapter.Listener() {
            @Override
            public void onListChanged() {
                updateTitle();
                updateBottomBar();
            }

            @Override
            public void onPreview(list item) {
                showPreview(item);
            }

            @Override
            public void onSaved(int saved, int failed, boolean single) {
                onImagesSaved(saved, failed, single);
            }
        });
        listAdapter.setDownloadGate(new ListAdapter.DownloadGate() {
            @Override
            public void runWithPermission(Runnable action) {
                runWithStoragePermission(action);
            }
        });

        htmlpageURL = ImageExtractor.normalizeUrl(urlFromIntent(getIntent()));
        if (htmlpageURL == null) {
            showState(R.drawable.ic_error_48, R.string.invalid_title, R.string.invalid_url, false);
            return;
        }
        listAdapter.setReferer(htmlpageURL);
        String host = Uri.parse(htmlpageURL).getHost();
        subtitle.setText(host != null ? host : htmlpageURL);
        subtitle.setVisibility(View.VISIBLE);
        title.setText(R.string.searching);

        startJsoup();
        startWebView();
    }

    /** 앱 첫 화면에서 넘어온 주소, 또는 브라우저 "공유" 로 받은 글(제목 + 주소) */
    private static String urlFromIntent(Intent intent) {
        if (intent == null) return null;
        String url = intent.getStringExtra("URL_KEY");
        if (url == null && Intent.ACTION_SEND.equals(intent.getAction())) {
            url = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (url == null) url = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        }
        if (url == null && intent.getData() != null) url = intent.getData().toString();
        return url;
    }

    // ---- 저장 권한 (Android 6~9 만 필요. 10 이상은 MediaStore 로 저장해서 권한 불필요) ----------

    private void runWithStoragePermission(Runnable action) {
        if (Build.VERSION.SDK_INT < 23 || Build.VERSION.SDK_INT >= 29
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            action.run();
            return;
        }
        pendingDownload = action;
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_STORAGE) return;
        Runnable action = pendingDownload;
        pendingDownload = null;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (action != null) action.run();
        } else {
            Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show();
        }
    }

    // ---- 1) Jsoup ----------------------------------------------------------------------------

    private void startJsoup() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                Set<String> found = null;
                try {
                    found = ImageExtractor.fromUrl(htmlpageURL, ImageSaver.USER_AGENT);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                final Set<String> result = found;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        sourceFinished(result);
                    }
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ---- 2) WebView --------------------------------------------------------------------------

    /** 페이지가 바뀔 때마다 증가. 이전 페이지에서 늦게 도착한 결과/타이머를 무시하는 데 사용 */
    private int pageGeneration = 0;
    private Set<String> pendingEmptyResult = null;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void startWebView() {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(ImageSaver.USER_AGENT);
        settings.setLoadsImagesAutomatically(true);   // 이미지 요청은 아래 shouldInterceptRequest 가 1x1 로 대신 응답
        webView.addJavascriptInterface(bridge, "Android");

        // 봇 확인/로그인 쿠키가 필요한 이미지도 받을 수 있도록 WebView 쿠키를 다운로드에 사용
        final CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        ImageSaver.cookies = new ImageSaver.CookieSource() {
            @Override
            public String cookieFor(String url) {
                return cookieManager.getCookie(url);
            }
        };

        webView.setWebViewClient(new WebViewClient() {
            @TargetApi(21)
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request.isForMainFrame()) return null;   // 이미지 주소를 바로 연 경우는 그대로 둔다
                String accept = null;
                Map<String, String> headers = request.getRequestHeaders();
                if (headers != null) {
                    for (Map.Entry<String, String> h : headers.entrySet()) {
                        if ("Accept".equalsIgnoreCase(h.getKey())) accept = h.getValue();
                    }
                }
                return isImageRequest(request.getUrl().toString(), accept) ? pixelResponse() : null;
            }

            @SuppressWarnings("deprecation")
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {   // Android 4.4
                return isImageRequest(url, null) ? pixelResponse() : null;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // JS 리다이렉트로 새 페이지가 열리면 처음부터 다시 수집
                pageGeneration++;
                scrollStarted = false;
                bridge.reset();
                scheduleForcedCapture(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                startCapture(view);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                // 메인 페이지 로드가 실패한 경우에만 이 소스를 종료 처리 (하위 리소스 오류는 무시)
                if (failingUrl != null && failingUrl.equals(htmlpageURL) && !webCaptured) {
                    webCaptured = true;
                    sourceFinished(null);
                }
            }
        });

        // 안전장치: 25초 안에 WebView 결과가 없으면 지금까지 받은 것으로 마무리
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!destroyed && !webCaptured) {
                    webCaptured = true;
                    sourceFinished(pendingEmptyResult);
                }
            }
        }, 25000);

        webView.loadUrl(htmlpageURL);
    }

    /** 이미지 요청인지: Accept 헤더(Android 5.0+) 또는 주소 확장자(4.4) 로 판단. 페이지 자체 요청은 제외 */
    private static boolean isImageRequest(String url, String accept) {
        if (url == null || url.startsWith("data:")) return false;
        if (accept != null) return accept.toLowerCase(Locale.ROOT).startsWith("image/");
        return IMAGE_PATH.matcher(url.split("[?#]")[0]).find();
    }

    private static WebResourceResponse pixelResponse() {
        return new WebResourceResponse("image/png", null, new ByteArrayInputStream(PIXEL_PNG));
    }

    private void startCapture(WebView view) {
        if (webCaptured || scrollStarted || destroyed) return;
        scrollStarted = true;
        // 처음 페이지 높이까지만 내려가므로 무한 스크롤로 계속 불러오지는 않는다.
        view.loadUrl(SCROLL_AND_CAPTURE_JS);
    }

    private void scheduleForcedCapture(final WebView view) {
        final int gen = pageGeneration;
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (gen == pageGeneration) startCapture(view);
            }
        }, FORCE_CAPTURE_MS);
    }

    private void onWebResult(final int gen, Set<String> result) {
        if (webCaptured || destroyed || gen != pageGeneration) return;
        if (result != null && !result.isEmpty()) {
            webCaptured = true;
            sourceFinished(result);
            return;
        }
        // 이미지가 없으면 봇 확인 후 이동하는 중간 페이지일 수 있으니 조금 더 기다린다
        pendingEmptyResult = result;
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!webCaptured && !destroyed && gen == pageGeneration) {
                    webCaptured = true;
                    sourceFinished(pendingEmptyResult);
                }
            }
        }, EMPTY_CAPTURE_GRACE_MS);
    }

    private final HtmlBridge bridge = new HtmlBridge();

    public class HtmlBridge {
        private final List<String[]> frames = new ArrayList<String[]>();

        void reset() {
            synchronized (frames) {
                frames.clear();
            }
        }

        @JavascriptInterface
        public void addFrame(String url, String html) {
            if (url == null || html == null) return;
            synchronized (frames) {
                frames.add(new String[]{url, html});
            }
        }

        @JavascriptInterface
        public void getHtml(final String pageUrl, final String html, final String extras) {
            List<String[]> copy;
            synchronized (frames) {
                copy = new ArrayList<String[]>(frames);
                frames.clear();
            }
            Set<String> found = null;
            try {
                found = ImageExtractor.fromRendered(pageUrl, html, copy, extras);
            } catch (Exception e) {
                e.printStackTrace();
            }
            final Set<String> result = found;
            ui.post(new Runnable() {
                @Override
                public void run() {
                    onWebResult(pageGeneration, result);
                }
            });
        }
    }

    // ---- 결과 처리 ---------------------------------------------------------------------------

    private boolean anySucceeded = false;

    private void sourceFinished(Set<String> urls) {
        if (destroyed) return;
        finishedSources++;
        if (urls != null) {
            anySucceeded = true;
            listAdapter.addItems(urls);
        }
        if (listAdapter.getImageCount() > 0) {
            showList();
        } else if (finishedSources >= SOURCES) {
            if (anySucceeded) {
                showState(R.drawable.ic_broken_image_48, R.string.empty_title, R.string.empty_message, true);
            } else {
                showState(R.drawable.ic_error_48, R.string.error_title, R.string.error_message, true);
            }
        }
        updateTitle();
        updateBottomBar();
    }

    private boolean searching() {
        return finishedSources < SOURCES;
    }

    /** 상단 제목: "이미지를 찾는 중…" → "이미지 12개 · 더 찾는 중…" → "이미지 12개" */
    private void updateTitle() {
        if (destroyed || htmlpageURL == null) return;
        int total = listAdapter.getImageCount();
        if (total == 0) {
            if (searching()) title.setText(R.string.searching);
        } else {
            title.setText(getString(searching() ? R.string.found_count_searching : R.string.found_count, total));
        }
        // 목록이 보인 뒤에도 WebView 가 더 찾는 동안은 상단에 얇은 진행 표시
        searchProgress.setVisibility(searching() && total > 0 ? View.VISIBLE : View.INVISIBLE);
    }

    /** 하단 바: "누르면 선택 · 길게 누르면 크게 보기 / 모두 저장 (24)" 등 */
    private void updateBottomBar() {
        if (destroyed) return;
        int total = listAdapter.getImageCount();
        int selected = listAdapter.getSelectedCount();
        int pending = listAdapter.getPendingCount();
        int[] batch = listAdapter.getBatchProgress();

        if (batch != null) {
            bottomInfo.setText(getString(R.string.saving, batch[0], batch[1]));
        } else if (selected > 0) {
            bottomInfo.setText(getString(R.string.selected_count, selected, total));
        } else if (total > 0 && pending == 0) {
            bottomInfo.setText(R.string.all_saved);
        } else {
            bottomInfo.setText(R.string.bottom_hint);
        }
        saveButton.setText(selected > 0 ? getString(R.string.save_selected, selected) : getString(R.string.save_all, pending));
        saveButton.setEnabled(selected > 0 || pending > 0);

        selectAllButton.setVisibility(total > 0 && pending > 0 ? View.VISIBLE : View.GONE);
        selectAllButton.setText(listAdapter.isAllSelected() ? R.string.deselect_all : R.string.select_all);
    }

    private void showList() {
        loadingView.setVisibility(View.GONE);
        stateView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
    }

    private void showState(int icon, int titleRes, int messageRes, boolean canRetry) {
        loadingView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
        searchProgress.setVisibility(View.INVISIBLE);
        stateView.setVisibility(View.VISIBLE);
        stateIcon.setImageResource(icon);
        stateTitle.setText(titleRes);
        stateMessage.setText(messageRes);
        findViewById(R.id.state_retry).setVisibility(canRetry ? View.VISIBLE : View.GONE);
        title.setText(R.string.download);
    }

    // ---- 저장 결과 / 미리보기 -------------------------------------------------------------------

    private void onImagesSaved(int saved, int failed, boolean single) {
        if (destroyed) return;
        String text;
        if (single) {
            text = getString(saved > 0 ? R.string.saved_one : R.string.save_failed);
        } else if (failed > 0) {
            text = getString(R.string.saved_summary_failed, saved, failed);
        } else {
            text = getString(R.string.saved_summary, saved);
        }
        resultText.setText(text);
        findViewById(R.id.result_action).setVisibility(saved > 0 ? View.VISIBLE : View.GONE);
        resultBar.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideResultBar);
        ui.postDelayed(hideResultBar, RESULT_BAR_MS);
    }

    private void openGallery() {
        try {
            startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
            } catch (ActivityNotFoundException ignored) {
            }
        }
    }

    /** 길게 누르면: 큰 이미지 + 주소 복사 / 저장 */
    private void showPreview(final list item) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_preview);
        dialog.setCanceledOnTouchOutside(true);

        ImageView image = dialog.findViewById(R.id.preview_image);
        Glide.with(this).load(listAdapter.glideModel(item.getName())).override(1600, 1600).fitCenter()
                .error(R.drawable.ic_broken_image_48).into(image);

        final String url = item.getName();
        TextView urlView = dialog.findViewById(R.id.preview_url);
        urlView.setText(url.startsWith("data:") ? getString(R.string.inline_image, url.length() * 3 / 4 / 1024) : url);

        View copy = dialog.findViewById(R.id.preview_copy);
        copy.setVisibility(url.startsWith("data:") ? View.GONE : View.VISIBLE);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("image url", url));
                Toast.makeText(Download.this, R.string.copied, Toast.LENGTH_SHORT).show();
            }
        });
        TextView save = dialog.findViewById(R.id.preview_save);
        boolean canSave = item.getState() == list.IDLE || item.getState() == list.FAILED;
        save.setEnabled(canSave);
        if (item.getState() == list.DONE) save.setText(R.string.badge_saved);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listAdapter.saveOne(item);
                dialog.dismiss();
            }
        });

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.download_back_arrow) {
            finish();
        } else if (id == R.id.save_button) {
            listAdapter.saveSelectedOrAll();
        } else if (id == R.id.select_all_button) {
            listAdapter.setAllSelected(!listAdapter.isAllSelected());
        } else if (id == R.id.state_retry) {
            recreate();
        } else if (id == R.id.result_action) {
            openGallery();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        ui.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        listAdapter.shutdown();
        super.onDestroy();
    }
}
