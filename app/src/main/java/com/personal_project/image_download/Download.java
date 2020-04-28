package com.personal_project.image_download;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.personal_project.image_download.support.Crawling;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

@SuppressLint("Registered")
public class Download extends AppCompatActivity implements View.OnClickListener{

    private ImageView dowmload_back_arrow;
    private String htmlpageURL = "";
    private ArrayList<String> image_arrayList;
    private Thread thread;
    private Crawling crawling;
    private Crawling_Handler handler;
    private TextView one;
    private NestedScrollView nestedScrollView;

    private WebView webView;
    private WebSettings settings;
    private ProgressBar progressBar;


    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download);

        initui();

        thread.start();
        //지정한 URL을 웹 뷰로 접근하기
        webView.loadUrl(htmlpageURL);


    }

    @SuppressLint("SetJavaScriptEnabled")
    private void webview_setting()
    {


        // 자바스크립트인터페이스 연결
        settings.setJavaScriptEnabled(true);
        // 이걸 통해 자바스크립트 내에서 자바함수에 접근할 수 있음.
        webView.addJavascriptInterface(new MyJavascriptInterface(), "Android");
        // 페이지가 모두 로드되었을 때, 작업 정의
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 자바스크립트 인터페이스로 연결되어 있는 getHTML를 실행
                // 자바스크립트 기본 메소드로 html 소스를 통째로 지정해서 인자로 넘김
                view.loadUrl("javascript:window.Android.getHtml(document.getElementsByTagName('body')[0].innerHTML);");
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                switch (errorCode) {
                    case ERROR_AUTHENTICATION:
                        break;           // 서버에서 사용자 인증 실패
                    case ERROR_BAD_URL:
                        break;                         // 잘못된 URL
                    case ERROR_CONNECT:
                        break;                        // 서버로 연결 실패
                    case ERROR_FAILED_SSL_HANDSHAKE:
                        break;  // SSL handshake 수행 실패
                    case ERROR_FILE:
                        break;                                // 일반 파일 오류
                    case ERROR_FILE_NOT_FOUND:
                        break;             // 파일을 찾을 수 없습니다
                    case ERROR_HOST_LOOKUP:
                        break;          // 서버 또는 프록시 호스트 이름 조회 실패
                    case ERROR_IO:
                        break;                             // 서버에서 읽거나 서버로 쓰기 실패
                    case ERROR_PROXY_AUTHENTICATION:
                        break;// 프록시에서 사용자 인증 실패
                    case ERROR_REDIRECT_LOOP:
                        break;              // 너무 많은 리디렉션
                    case ERROR_TIMEOUT:
                        break;                         // 연결 시간 초과
                    case ERROR_TOO_MANY_REQUESTS:
                        break;    // 페이지 로드중 너무 많은 요청 발생
                    case ERROR_UNKNOWN:
                        break;                        // 일반 오류
                    case ERROR_UNSUPPORTED_AUTH_SCHEME:
                        break;
                        // 지원되지 않는 인증 체계
                    case ERROR_UNSUPPORTED_SCHEME:
                        break;
                    default:
                        break;
                }

            }
        });

    }



    private void getURL()
    {
        Intent intent = getIntent(); //이 액티비티를 부른 인텐트를 받는다.
        htmlpageURL = intent.getStringExtra("URL_KEY"); //"jizard"문자 받아옴

    }

    private void find_id()
    {
        dowmload_back_arrow = findViewById(R.id.download_back_arrow);
        one = new TextView(this);
        nestedScrollView = findViewById(R.id.ned);

        progressBar = findViewById(R.id.circularProgressbar);
        image_arrayList = new ArrayList<String>();
        webView = new WebView(this);
        settings = webView.getSettings();
        handler = new Crawling_Handler();
        crawling = new Crawling();
    }

    private void thread_setting()
    {
        thread = new Thread(crawling);
        thread.setDaemon(true);
    }

    private void initui()
    {
        find_id();
        dowmload_back_arrow.setOnClickListener(this);
        thread_setting();
        getURL();
        webview_setting();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId())
        {
            case R.id.download_back_arrow:
                image_arrayList.clear();
                finish();
                break;
        }
    }

    @Override
    protected void onResume() {

//        thread.start();
        super.onResume();

    }

    @Override
    protected void onPause() {

        super.onPause();

    }


    public class MyJavascriptInterface {
        @JavascriptInterface
        public void getHtml(String html) {
            //위 자바스크립트가 호출되면 여기로 html이 반환됨

            System.out.println("실행 중");

            try {
                Document doc = Jsoup.parse(html);

                for (Element e : doc.select("img")) {

                    if (e.attr("src") != null) {
                        image_arrayList.add(e.attr("src"));
                    }

                }
            }catch (Exception e) {

                Log.d("cheeeeck","js catch");

                e.printStackTrace();
                handler.sendEmptyMessage(1);
            }

            Log.d("cheeeeck","imame_size" + image_arrayList.size());

            if(!image_arrayList.isEmpty()) {
                handler.sendEmptyMessage(0);
            }
            else {
                handler.sendEmptyMessage(1);
            }

        }
    }



    class Crawling implements Runnable{

        @Override
        public void run() {

            System.out.println("Crawling 실행");

            try {

                System.out.println("run 실행");

                Document doc = (Document) Jsoup.connect(htmlpageURL).get();

                for (Element e : doc.select("img")) {

                    if( e.attr("src") != null){
                        image_arrayList.add(e.attr("src"));
                    }

                }



            } catch (Exception e) {

                Log.d("cheeeeck","thread catch");

                e.printStackTrace();
            }

            //handler.sendEmptyMessage(0);
        }


    }

    @SuppressLint("HandlerLeak")
    class Crawling_Handler extends Handler{
             String empty = "";

            @SuppressLint("HandlerLeak")
            @Override
            public void handleMessage(@NonNull Message msg) {


                if(msg.what == 0){   // Message id 가 0 이면

                    empty = "";
                    Log.d("cheeeeck","handle : 0");

                    for(int i =0;i< image_arrayList.size();i++) {
                        empty += image_arrayList.get(i);
                        System.out.println(image_arrayList.get(i));
                    }

                    dismiss_progress();


                    one.setText(empty);


                }

                if(msg.what == 1)
                {
                    Log.d("cheeeeck","handle : 1");

                    dismiss_progress2();
                    one.setText("error \n (잘못된 주소 또는 인터넷 열결확인)");
                }

            }
    }

    private void dismiss_progress()
    {
        LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        one.setTextColor(Color.BLACK);
        one.setGravity(Gravity.CENTER);
        one.setLayoutParams(lparams);
        progressBar.setVisibility(View.GONE);
        nestedScrollView.removeAllViews();
        nestedScrollView.addView(one);
    }


    private void dismiss_progress2()
    {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams
                (FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT);

        params.gravity = Gravity.CENTER;

        one.setLayoutParams(params);
        one.setTextColor(Color.BLACK);
        one.setGravity(Gravity.CENTER);

        progressBar.setVisibility(View.GONE);
        nestedScrollView.removeAllViews();
        nestedScrollView.addView(one);
    }


}