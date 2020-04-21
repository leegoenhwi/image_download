package com.personal_project.image_download;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

@SuppressLint("Registered")
public class Download extends AppCompatActivity implements View.OnClickListener{

    private ImageView dowmload_back_arrow;
    private String htmlpageURL = "https://gothiczzang.tistory.com/16";
    private String html = "";
    private Crawling crawling;
    private Thread thread;
    private Handler handler;
    private TextView one;

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download);

        find_id();
        initui();

//        Intent intent = getIntent(); //이 액티비티를 부른 인텐트를 받는다.
//        String username = intent.getStringExtra("USERNAME_KEY"); //"jizard"문자 받아옴
//        int birthday = intent.getIntExtra("BIRTHDAY_KEY",0); //119 받아옴

         one = findViewById(R.id.one);

        thread_setting();


        handler = new Handler(){
            @SuppressLint("HandlerLeak")
            @Override
            public void handleMessage(@NonNull Message msg) {
                if(msg.what == 0){   // Message id 가 0 이면
                    one.setText(html);

                }
            }
        };

    }

    private void find_id()
    {
        dowmload_back_arrow = findViewById(R.id.download_back_arrow);

        crawling = new Crawling();
    }

    private void thread_setting()
    {
        thread = new Thread(crawling);
        thread.setDaemon(true);
    }

    private void initui()
    {
        dowmload_back_arrow.setOnClickListener(this);
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

        thread.start();



        super.onResume();

    }

    class Crawling implements Runnable{

        @Override
        public void run() {

            String src = "";

            System.out.println("Crawling 실행");

            try {

                System.out.println("run 실행");

                Document doc = (Document) Jsoup.connect(htmlpageURL).get();


                for (Element e : doc.select("img")) {
                   src += e.attr("src") + "\n";
                }

                html = src;

                System.out.println("html");

                System.out.println(html);


            } catch (Exception e) {
                e.printStackTrace();
            }

            handler.sendEmptyMessage(0);
        }


    }

}
