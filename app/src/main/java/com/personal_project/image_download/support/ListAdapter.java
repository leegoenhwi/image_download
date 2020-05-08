package com.personal_project.image_download.support;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.personal_project.image_download.R;

import java.util.ArrayList;

public class ListAdapter  extends BaseAdapter {

    private ArrayList <list> arrayList = new ArrayList<list>();
//    private Context context;
//    private  ImageView download_icon;

    public ListAdapter() {

    }

    @Override
    public int getCount() {
        return arrayList.size();
    }

    @Override
    public String getItem(int pos) {
        return arrayList.get(pos).toString();
    }

    @Override
    public long getItemId(int pos) {
        return pos;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        final int pos = position;
        Context context = parent.getContext();

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.list, parent, false);
        }


        list profile = (list)arrayList.get(pos);

        ImageView photo = (ImageView)convertView.findViewById(R.id.photo);
        TextView name = (TextView)convertView.findViewById(R.id.name);
        ImageView download_icon = (ImageView)convertView.findViewById(R.id.download_icon);

        download_icon.setTag(pos); // 포지션 태그 셋

        Glide.with(context).load(profile.getName()).override(200, 200).error(R.drawable.ic_do_not_disturb_alt_black_24dp).into(photo);

        name.setText(profile.getName());

        if(photo.getImageAlpha() == R.drawable.ic_do_not_disturb_alt_black_24dp)
        {
            name.setText("x");
        }



        download_icon.setOnClickListener(new ImageView.OnClickListener(){
            @Override
            public void onClick(View view) {
                Log.d("aaaaaaaaa",view.getTag().toString());
            }
        });


//        if ( profile.getName().endsWith(".gif")) {
//            try {
//                Glide.with(_inflater.getContext())
//                        .asGif()
//                        .load(profile.getName())
//                        .apply(new RequestOptions().transforms(new CenterCrop(), new RoundedCorners(10)))
//                        .into(photo);
//            } catch (Exception ignored) {
//            }
//        } else {
//            try {
//                Glide.with(_inflater.getContext())
//                        .asBitmap()
//                        .apply(new RequestOptions().transforms(new CenterCrop(), new RoundedCorners(10)))
//                        .load(profile.getName())
//                        .into(photo);
//            } catch (Exception ignored) {
//            }
//        }




        return convertView;
    }

    // 아이템 데이터 추가를 위한 함수. 개발자가 원하는대로 작성 가능.
    public void addItem(String title) {

        list item = new list();

        item.setName(title);


        arrayList.add(item);
    }


}