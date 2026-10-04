package com.personal_project.image_download;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
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
                    Document doc = Jsoup.connect(htmlpageURL)
                            .userAgent(ImageSaver.USER_AGENT)
                            .referrer(htmlpageURL)
                            .timeout(15000)
                            .followRedirects(true)
                            .maxBodySize(0)
                            .get();
                    found = ImageExtractor.extract(doc, doc.location());
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

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void startWebView() {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(ImageSaver.USER_AGENT);
        settings.setBlockNetworkImage(true);   // 이미지 자체는 받을 필요 없음 (주소만 필요)
        webView.addJavascriptInterface(new HtmlBridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(final WebView view, String url) {
                super.onPageFinished(view, url);
                if (webCaptured) return;
                // lazy-load 이미지를 위해 끝까지 스크롤한 뒤 잠시 기다렸다가 HTML 을 가져온다
                view.loadUrl("javascript:(function(){window.scrollTo(0,document.body.scrollHeight);})()");
                ui.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed || webCaptured) return;
                        webCaptured = true;
                        view.loadUrl("javascript:window.Android.getHtml(location.href,"
                                + "document.documentElement.outerHTML);");
                    }
                }, 1500);
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

        // 안전장치: 20초 안에 WebView 결과가 없으면 포기하고 계속 진행
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!destroyed && !webCaptured) {
                    webCaptured = true;
                    sourceFinished(null);
                }
            }
        }, 20000);

        webView.loadUrl(htmlpageURL);
    }

    public class HtmlBridge {
        @JavascriptInterface
        public void getHtml(final String pageUrl, final String html) {
            Set<String> found = null;
            try {
                found = ImageExtractor.extract(Jsoup.parse(html, pageUrl), pageUrl);
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
