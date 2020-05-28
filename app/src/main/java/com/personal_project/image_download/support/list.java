package com.personal_project.image_download.support;

import android.graphics.drawable.Drawable;
import android.widget.ProgressBar;

public class list {

    private String _name;
    private Drawable _download_icon;
    private ProgressBar _progressBar;

    private boolean _clicked = false;

    public String getName() {
        return _name;
    }

    public Drawable getdownloadicon() {

        return _download_icon;
    }

    public boolean getclicked()
    {
        return _clicked;
    }

    public ProgressBar getProgressBar()
    {
        return _progressBar;
    }

    public void setdownloadicon(Drawable icon) {
        _download_icon = icon ;
    }
    public void setName(String name) {
        _name = name ;
    }

    private void set_progressBar(ProgressBar progressBar)
    {
        _progressBar = progressBar;
    }

    public void setclicked(boolean clicked)
    {
        _clicked = clicked;
    }
}
