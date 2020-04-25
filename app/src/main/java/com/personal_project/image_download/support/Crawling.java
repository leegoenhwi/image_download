package com.personal_project.image_download.support;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;

import com.personal_project.image_download.Download;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.lang.reflect.Array;
import java.util.ArrayList;

public class Crawling implements Runnable {

    private String htmlpageURL;
    private ArrayList<String> image_list = new ArrayList<String>();
    private Download download = new Download();
//    private Download.crawling_handler handler = new Download.crawling_handler();;

    public void setURL(String URL)
    {
        this.htmlpageURL = URL;
    }

    @Override
    public void run() {
        System.out.println("Crawling 실행");



        try {

            System.out.println("run 실행");

            Document doc = (Document) Jsoup.connect(htmlpageURL).get();


            for (Element e : doc.select("img")) {
                if(e.attr("src") != null) {
                    image_list.add( e.attr("src"));
                }
            }




        } catch (Exception e) {
            e.printStackTrace();
        }

//
//        handler.setArray(image_list);
//        handler.sendEmptyMessage(0);

    }




}

