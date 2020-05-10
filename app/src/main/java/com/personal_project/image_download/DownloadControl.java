package com.personal_project.image_download;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class DownloadControl extends AppCompatActivity implements View.OnClickListener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.downloadcontrol);

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
}
