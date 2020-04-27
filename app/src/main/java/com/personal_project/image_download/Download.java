package com.personal_project.image_download;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.personal_project.image_download.support.Crawling;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

@SuppressLint("Registered")
public class Download extends AppCompatActivity implements View.OnClickListener{

    private ImageView dowmload_back_arrow;
    private String htmlpageURL = "";
    private ArrayList<String> image_list;
    private Thread thread;
    private Crawling crawling;
    private Crawling_Handler handler;
    private TextView one;

    private WebView webView;

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
    private void js_setting()
    {
        // 자바스크립트인터페이스 연결
        webView.getSettings().setJavaScriptEnabled(true);
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
        one = findViewById(R.id.one);


        image_list = new ArrayList<String>();
        webView = new WebView(this);
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
        js_setting();
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


    public class MyJavascriptInterface {
        @JavascriptInterface
        public void getHtml(String html) {
            //위 자바스크립트가 호출되면 여기로 html이 반환됨

            System.out.println("실행 중");

//            System.out.println("source");
//            System.out.println(source);

            try {
                Document doc = Jsoup.parse(html);

                for (Element e : doc.select("img")) {

                    if (e.attr("src") != null) {
                        image_list.add(e.attr("src"));
                    }

                }
            }catch (Exception e) {
                e.printStackTrace();
            }

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
                        image_list.add(e.attr("src"));
                    }

                }



            } catch (Exception e) {
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

                    for(int i =0;i< image_list.size();i++) {
                        empty += image_list.get(i);
                        System.out.println(image_list.get(i));
                    }

                    one.setText(empty);

                }

            }
    }


}