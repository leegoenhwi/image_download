package com.personal_project.image_download.support;

public class list {

    public static final int IDLE = 0;
    public static final int DOWNLOADING = 1;
    public static final int DONE = 2;
    public static final int FAILED = 3;

    private String _name;
    private int _state = IDLE;
    private int _progress = 0;
    private boolean _selected = false;

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public int getState() {
        return _state;
    }

    public void setState(int state) {
        _state = state;
    }

    public int getProgress() {
        return _progress;
    }

    public void setProgress(int progress) {
        _progress = progress;
    }

    public boolean isSelected() {
        return _selected;
    }

    public void setSelected(boolean selected) {
        _selected = selected;
    }

    public boolean getclicked() {
        return _state != IDLE && _state != FAILED;
    }
}
