package com.personal_project.image_download.support;

import android.graphics.drawable.Drawable;

import androidx.core.graphics.drawable.DrawableCompat;

public class Color {

    private Drawable drawable;
    private int color;

    public Color(Drawable drawable, int color)
    {
        this.drawable = drawable;
        this.color = color;
    }

    public Drawable change()
    {
        Drawable ch_drawable = DrawableCompat.wrap(this.drawable);
        DrawableCompat.setTint(drawable, color);
        return ch_drawable;
    }
}
