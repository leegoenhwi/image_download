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
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.personal_project.image_download.support.Crawling;
import com.personal_project.image_download.support.ListAdapter;
import com.personal_project.image_download.support.list;

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

    private Thread thread;
    private Crawling crawling;
    private Crawling_Handler handler;
    private TextView one;

    private FrameLayout nestedScrollView;
    private WebView webView;
    private WebSettings settings;
    private ProgressBar progressBar;

    private boolean once = false;

    private RecyclerView recyclerView;
    private LinearLayout linearLayout;

    private ListAdapter listAdapter;

    private ArrayList<list> mArrayList;
    private RecyclerView.LayoutManager mLayoutManager;

    private int js_count = 0;


    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download);

        initui();

        thread.start();
//
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
        recyclerView = new RecyclerView(this);
        nestedScrollView = findViewById(R.id.ned);

        progressBar = findViewById(R.id.circularProgressbar);
        webView = new WebView(this);
        settings = webView.getSettings();
        handler = new Crawling_Handler();
        crawling = new Crawling();

        linearLayout = new LinearLayout(this);

        mArrayList = new ArrayList<list>();
        listAdapter = new ListAdapter(mArrayList);
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
        listview_setting();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId())
        {
            case R.id.download_back_arrow:
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

            System.out.println("js 실행");

            if(js_count >= 2 || once)
            {
                return;
            }


            try {
                Document doc = Jsoup.parse(html);

                for (Element e : doc.select("img")) {

                    if (e.attr("src") != null) {
//                        image_arrayList.add(e.attr("src"));
                        listAdapter.addItem(e.attr("src"));
                    }

                }
            }catch (Exception e) {

                Log.d("cheeeeck","js catch");

                e.printStackTrace();
                handler.sendEmptyMessage(1);
            }

            Log.d("cheeeeck","imame_size" + listAdapter.getItemCount());

            js_count++;
            handler.sendEmptyMessage(0);



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
//                        image_arrayList.add(e.attr("src"));
                        listAdapter.addItem(e.attr("src"));
                    }

                }



            } catch (Exception e) {

                Log.d("cheeeeck","thread catch");

                e.printStackTrace();
//                handler.sendEmptyMessage(1);
            }

            //handler.sendEmptyMessage(0);
        }


    }

    @SuppressLint("HandlerLeak")
    class Crawling_Handler extends Handler{
//             String empty = "";

            @SuppressLint("HandlerLeak")
            @Override
            public void handleMessage(@NonNull Message msg) {

                if(msg.what == 0){   // Message id 가 0 이면

                    Log.d("cheeeeck","handle : 0");

                    if(listAdapter.getItemCount() == 0)
                    {
                        data_process2();
                        one.setText("no image of please wait \n (이미지 소스가 없거나 잠시만 기다려주세요)");
                    }
                    else{
                        data_process();
                    }

                }

                if(msg.what == 1)
                {
                    Log.d("cheeeeck","handle : 1");

                    data_process2();
                    one.setText("error \n (잘못된 주소 또는 인터넷 열결확인)");
                }

            }
    }

    private void data_process()
    {



        LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT);
        lparams.gravity =Gravity.TOP;

        progressBar.setVisibility(View.GONE);

        if(!once) {

            recyclerView.setLayoutParams(lparams);
            linearLayout.setLayoutParams(lparams);
            nestedScrollView.removeAllViews();
            nestedScrollView.addView(linearLayout);
            linearLayout.addView(recyclerView);

            recyclerView.setAdapter(listAdapter);
            once = true;
        }

    }


    private void data_process2()
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

    private void listview_setting()
    {
        recyclerView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        mLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(mLayoutManager);
//        listView.setFastScrollEnabled(true);
//        listView.setSmoothScrollbarEnabled(true);
//        listView.setFastScrollAlwaysVisible(true);

        //        listView.setSelector();
    }

}