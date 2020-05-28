package com.personal_project.image_download.support;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.personal_project.image_download.R;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.Locale;

//CustomAdapter.CustomViewHolder
public class ListAdapter  extends RecyclerView.Adapter<ListAdapter.CustomViewHolder>  {

    private ArrayList<list> mList;
    private imagedownload_Handler imagedownload_handler;

    private imagedownload imagedownload;

    private Context context;



    public class CustomViewHolder extends RecyclerView.ViewHolder{

        protected ImageView photo;
        protected TextView name;
        protected ImageView download_icon;
        protected ProgressBar progressBar;

        private Drawable mDrawable;


        public CustomViewHolder(View view) {
            super(view);
            this.photo = (ImageView) view.findViewById(R.id.photo);
            this.name = (TextView) view.findViewById(R.id.name);
            this.download_icon = (ImageView) view.findViewById(R.id.download_icon);
            this.progressBar = view.findViewById(R.id.progress);

            imagedownload_handler = new imagedownload_Handler();

            imagedownload = new imagedownload();


        }


    }


    public ListAdapter(Context c,ArrayList<list> list) {
        this.mList = list;
        this.context = c;
    }



    @NonNull
    @Override
    public CustomViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {

        Log.d("bbbbbbbbbbbbbbbbbb","onCreateViewHolder");

        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.list, viewGroup, false);

        CustomViewHolder viewHolder = new CustomViewHolder(view);

        return viewHolder;
    }




    @SuppressLint("ResourceType")
    @Override
    public void onBindViewHolder(@NonNull final CustomViewHolder viewholder, final int position) {

        Log.d("bbbbbbbbbbbbbbbbbb", "onBindViewHolder");


        Glide.with(context).load(mList.get(position).getName()).override(200, 200).error(R.drawable.ic_do_not_disturb_alt_black_24dp).into(viewholder.photo);

        viewholder.name.setText(mList.get(position).getName());

        final Drawable mDrawable = context.getResources().getDrawable(R.drawable.download);
        mDrawable.setColorFilter(ContextCompat.getColor(context, R.color.colorcilipboard), PorterDuff.Mode.MULTIPLY);


        if(!mList.get(position).getclicked())
        {

            viewholder.download_icon.setImageDrawable(mDrawable);

            viewholder.download_icon.setClickable(true);

            viewholder.progressBar.setVisibility(View.GONE);

        }

        else{


            Drawable dDrawable = context.getResources().getDrawable(R.drawable.check);
            dDrawable.setColorFilter(ContextCompat.getColor(context, R.color.colormain), PorterDuff.Mode.MULTIPLY);

            viewholder.download_icon.setImageDrawable(dDrawable);

            viewholder.progressBar.setProgress(100);
        }

        viewholder.download_icon.setOnClickListener(new ImageView.OnClickListener(){

            @Override
            public void onClick(View v) {

                if(viewholder.download_icon.getDrawable() == mDrawable)
                {


                        // TODO : use pos.
                        mList.get(position).setclicked(true);
                        System.out.println("아이템 포지션 : " + position);

                        Thread thread = new Thread(imagedownload);
                        thread.setDaemon(true);
                        imagedownload.setposition(position,viewholder.download_icon,viewholder.progressBar);
                        imagedownload.seturl(mList.get(position));

                        thread.start();

                }
            }
        });

    }

    @Override
    public int getItemCount() {
        return (null != mList ? mList.size() : 0);
    }

    public void addItem(String title) {
        // 외부에서 item을 추가시킬 함수입니다.

        list item = new list();


        item.setName(title);

        mList.add(item);

        notifyDataSetChanged();
    }




    class imagedownload implements Runnable{

        private String fileName;

        private String fileUrl;

        String savePath =  Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_PICTURES ) + "/image_download";

        public void setposition(int pos,ImageView imageView,ProgressBar progressBar)
        {
           imagedownload_handler.set(pos,imageView,progressBar);
        }


        public void seturl(list url)
        {
            this.fileUrl = url.getName();
            Log.d("aaaaaaaaa",url.getName());
        }

        @Override
        public void run() {

            //프로그레스 바 애니메이션
            imagedownload_handler.sendEmptyMessage(2);

            Log.d("Runnable", "savePath : " + savePath);

            Log.d("Runnable", " imagedownload start");

            File dir = new File(savePath);
            //상위 디렉토리가 존재하지 않을 경우 생성
            if (!dir.exists()) {

                dir.mkdirs();
                Log.d("Runnable", " 폴더 생성");
            }

            //파일 이름 :날짜_시간
            Date day = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA);
            fileName = String.valueOf(sdf.format(day));


            String localPath = savePath +"/"+ fileName + "."+  fileNameWithoutExtn(fileUrl);

            Log.d("Runnable","localpath : "+ localPath);

            try {

                Log.d("Runnable", " imagedownload try");

                URL imgUrl = new URL(fileUrl);
                //서버와 접속하는 클라이언트 객체 생성
                HttpURLConnection conn = (HttpURLConnection)imgUrl.openConnection();
                int len = conn.getContentLength();
                byte[] tmpByte = new byte[len];

                Log.d("Runnable", " imagedownload 1");
                //입력 스트림을 구한다
                InputStream is = conn.getInputStream();
                File file = new File(localPath);
                //파일 저장 스트림 생성

                Log.d("Runnable", " imagedownload 2");

                imagedownload_handler.sendEmptyMessage(3);

                FileOutputStream fos = new FileOutputStream(file);
                int read;
                //입력 스트림을 파일로 저장
                Log.d("Runnable", " imagedownload 3");
                for (;;) {
                    read = is.read(tmpByte);
                    if (read <= 0) {
                        break;
                    }
                    fos.write(tmpByte, 0, read); //file 생성
                }

                imagedownload_handler.sendEmptyMessage(4);

                is.close();
                fos.close();
                conn.disconnect();

                Log.d("Runnable", " imagedownload 4");



            }

            catch (Exception e) {

                Log.d("Runnable","thread catch");
                imagedownload_handler.sendEmptyMessage(1);
                return;

            }


            imagedownload_handler.sendEmptyMessage(0);


        }


    }

    @SuppressLint("HandlerLeak")
    class imagedownload_Handler extends Handler {


        private int pos;

        private ImageView imageView;

        private ProgressBar progressBar;

        public void set(int pos,ImageView imageView,ProgressBar progressBar)
        {
            this.pos = pos;
            this.imageView = imageView;
            this.progressBar = progressBar;
        }


        @RequiresApi(api = Build.VERSION_CODES.N)
        @SuppressLint("HandlerLeak")
        @Override
        public void handleMessage(@NonNull Message msg) {

            if(msg.what == 0){   // Message id 가 0 이면

                Log.d("imagedownload_Handler","완료");

            }

            else if(msg.what == 1)
            {
                Log.d("imagedownload_Handler","오류");

            }

            if(msg.what == 2)
            {
                progressBar.setVisibility(View.VISIBLE);
            }

            else if(msg.what == 3)
            {
                for(int i = 0;i<=50;i++) {
                    progressBar.setProgress(i);
                }
            }

            else if(msg.what == 4)
            {
                for(int i = 50;i<=100;i++) {
                    progressBar.setProgress(i);
                }

                Toast myToast = Toast.makeText(context, "download complete!!!", Toast.LENGTH_SHORT);
                myToast.show();

                final Drawable dDrawable = context.getResources().getDrawable(R.drawable.check);
                dDrawable.setColorFilter(ContextCompat.getColor(context, R.color.colormain), PorterDuff.Mode.MULTIPLY);

                imageView.setImageDrawable(dDrawable);

            }

        }
    }


    private String fileNameWithoutExtn(String url)
    {
        if(url.contains(".jpg"))
        {
            return "jpg";
        }
        else if(url.contains(".gif"))
        {
            return "gif";
        }
        else if(url.contains(".BMP"))
        {
            return "BMP";
        }
        else if(url.contains(".RLE"))
        {
            return "RLE";
        }
        else if(url.contains(".DIB"))
        {
            return "DIB";
        }
        else if(url.contains(".png"))
        {
            return "png";
        }
        else if(url.contains(".TIF"))
        {
            return "TIF";
        }
        else if(url.contains(".TIFF"))
        {
            return "TIFF";
        }
        else if(url.contains(".raw"))
        {
            return "raw";
        }
        else
        {
            return "jpg";
        }
    }


}