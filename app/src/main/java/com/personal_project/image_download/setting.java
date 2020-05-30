package com.personal_project.image_download;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

@SuppressLint("Registered")
public class setting extends AppCompatActivity implements View.OnClickListener {

    ImageView back_arrow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setting);

        find_id();
        init_UI();
    }

    private void init_UI() {
        back_arrow.setOnClickListener(this);
    }

    private void find_id() {
        back_arrow = findViewById(R.id.setting_back_arrow);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId())
        {
            case R.id.setting_back_arrow:
                finish();
                break;
        }
    }
}
