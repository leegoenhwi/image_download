package com.personal_project.image_download.support;

import android.graphics.drawable.Drawable;

public class list {
    private int _photo;
    private String _name;
    private int _download_icon;

    public int getPhoto() {
        return _photo;
    }

    public String getName() {
        return _name;
    }

    public int getdownloadicon() {

        return _download_icon;
    }

    public void setdownloadicon(int icon) {
        _download_icon = icon ;
    }
    public void setName(String name) {
        _name = name ;
    }
    public void setPhoto(int photo) {
       _photo = photo ;
    }
}
