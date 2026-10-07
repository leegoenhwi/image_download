package com.personal_project.image_download;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.personal_project.image_download.support.ImageExtractor;
import com.personal_project.image_download.support.ImageSaver;
import com.personal_project.image_download.support.ListAdapter;
import com.personal_project.image_download.support.list;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 입력된 주소에서 이미지를 찾아 목록으로 보여준다.
 * 두 가지 방법을 함께 사용한다.
 *  1) Jsoup 으로 원본 HTML 파싱 (빠름)
 *  2) WebView 로 JavaScript 까지 실행한 뒤의 HTML 파싱 (동적으로 생성되는 이미지 대응)
 * 두 결과는 합쳐지고 중복은 제거된다.
 */
public class Download extends AppCompatActivity implements View.OnClickListener {

    private static final int SOURCES = 2;

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

    private final Handler ui = new Handler(Looper.getMainLooper());

    private String htmlpageURL;
    private ImageView back_arrow;
    private TextView downloadAll;
    private TextView message;
    private FrameLayout container;
    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private WebView webView;

    private ListAdapter listAdapter;

    private int finishedSources = 0;
    private boolean listShown = false;
    private boolean webCaptured = false;
    private boolean scrollStarted = false;
    private boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download);

        back_arrow = findViewById(R.id.download_back_arrow);
        downloadAll = findViewById(R.id.download_all);
        container = findViewById(R.id.ned);
        progressBar = findViewById(R.id.circularProgressbar);
        back_arrow.setOnClickListener(this);
        downloadAll.setOnClickListener(this);

        message = new TextView(this);
        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        listAdapter = new ListAdapter(this, new ArrayList<list>());
        recyclerView.setAdapter(listAdapter);

        htmlpageURL = ImageExtractor.normalizeUrl(getIntent().getStringExtra("URL_KEY"));
        if (htmlpageURL == null) {
            showMessage("error \n (잘못된 주소입니다. 주소를 확인해주세요)");
            return;
        }
        listAdapter.setReferer(htmlpageURL);

        startJsoup();
        startWebView();
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
        settings.setBlockNetworkImage(true);   // 이미지 자체는 받을 필요 없음 (주소만 필요)
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
            for (String u : urls) {
                listAdapter.addItem(u);
            }
        }

        if (listAdapter.getItemCount() > 0) {
            showList();
        } else if (finishedSources >= SOURCES) {
            if (anySucceeded) {
                showMessage("no image \n (이미지를 찾을 수 없습니다)");
            } else {
                showMessage("error \n (잘못된 주소 또는 인터넷 연결 확인)");
            }
        }
    }

    private void showList() {
        progressBar.setVisibility(View.GONE);
        downloadAll.setVisibility(View.VISIBLE);
        if (!listShown) {
            listShown = true;
            container.removeAllViews();
            container.addView(recyclerView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
    }

    private void showMessage(String text) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.CENTER;
        message.setLayoutParams(params);
        message.setTextColor(Color.BLACK);
        message.setGravity(Gravity.CENTER);
        message.setText(text);
        progressBar.setVisibility(View.GONE);
        container.removeAllViews();
        container.addView(message);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.download_back_arrow) {
            finish();
        } else if (id == R.id.download_all) {
            listAdapter.downloadAll();
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
