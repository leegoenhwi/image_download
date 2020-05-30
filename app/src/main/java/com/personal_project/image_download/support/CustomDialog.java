package com.personal_project.image_download.support;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.personal_project.image_download.MainActivity;
import com.personal_project.image_download.R;

public class CustomDialog extends Dialog implements View.OnClickListener {

    private Context mContext;



    private TextView btn_ok;



    public CustomDialog(@NonNull Context context) {

        super(context);

        mContext = context;

    }



    @Override

    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.dialog);





        btn_ok = (TextView) findViewById(R.id.btn_ok);




        btn_ok.setOnClickListener(this);

    }





    @Override

    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.btn_ok:

                dismiss();

                break;

        }

    }

}
