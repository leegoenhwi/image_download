package com.personal_project.image_download.support;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.personal_project.image_download.R;

import java.util.ArrayList;
import java.util.Dictionary;
//CustomAdapter.CustomViewHolder
public class ListAdapter  extends RecyclerView.Adapter<ListAdapter.CustomViewHolder> {

    private ArrayList<list> mList;

    public class CustomViewHolder extends RecyclerView.ViewHolder {

        protected ImageView photo;
        protected TextView name;
        protected ImageView download_icon;


        public CustomViewHolder(View view) {
            super(view);
            this.photo = (ImageView) view.findViewById(R.id.photo);
            this.name = (TextView) view.findViewById(R.id.name);
            this.download_icon = (ImageView) view.findViewById(R.id.download_icon);
        }
    }


    public ListAdapter(ArrayList<list> list) {
        this.mList = list;
    }



    @Override
    public CustomViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {

        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.list, viewGroup, false);

        CustomViewHolder viewHolder = new CustomViewHolder(view);

        return viewHolder;
    }




    @Override
    public void onBindViewHolder(@NonNull CustomViewHolder viewholder, int position) {

        viewholder.download_icon.setTag(position); // 포지션 태그 셋

        Glide.with(viewholder.itemView.getContext()).load(mList.get(position).getName()).override(200, 200).error(R.drawable.ic_do_not_disturb_alt_black_24dp).into(viewholder.photo);

        viewholder.name.setText(mList.get(position).getName());

        if(viewholder.photo.getImageAlpha() == R.drawable.ic_do_not_disturb_alt_black_24dp)
        {
            viewholder.name.setText("x");
        }

        viewholder.download_icon.setOnClickListener(new ImageView.OnClickListener(){
            @Override
            public void onClick(View view) {
                Log.d("aaaaaaaaa",view.getTag().toString());
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
    };
}