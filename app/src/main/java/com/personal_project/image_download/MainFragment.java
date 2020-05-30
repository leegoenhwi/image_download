package com.personal_project.image_download;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import static androidx.core.content.ContextCompat.getSystemService;

public class MainFragment extends Fragment implements View.OnClickListener{

    private TextView download_button;
    private TextView clipboard_button;
    private View view;
    private EditText text_input;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.main_fragment, container, false);

        find_id();
        initui();
        return view;
    }

    private void find_id()
    {
        download_button = view.findViewById(R.id.download_button);
        clipboard_button = view.findViewById(R.id.clipboard_button);
        text_input = view.findViewById(R.id.text_input);
    }

    private void initui()
    {
        download_button.setOnClickListener(this);
        clipboard_button.setOnClickListener(this);
        edit_text();
        grantExternalStoragePermission();
        text_input.requestFocus();
    }

    private void edit_text()
    {

        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);

        if (imm != null) {
            imm.showSoftInput(text_input,0);
        }
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.download_button:
                Intent Download_intent = new Intent(view.getContext(),Download.class);
                Download_intent.putExtra("URL_KEY",text_input.getText().toString()); //키 - 보낼 값(밸류)
                startActivity(Download_intent);

                break;
            case R.id.clipboard_button:
                clipboard_paste();
                break;
        }
    }

    private void clipboard_paste()
    {

        ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (!(clipboardManager.hasPrimaryClip()))
        {
            Toast.makeText(getContext(),"no copy data", Toast.LENGTH_SHORT).show();
        }
        else {
            ClipData.Item item = clipboardManager.getPrimaryClip().getItemAt(0);;

            text_input.setText( item.getText().toString());
        }
    }

    private boolean grantExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23) {

            if (ContextCompat.checkSelfPermission(getContext(),Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                Log.d("TAG", "Permission is granted");
                return true;
            }
            else{
                Log.d("TAG","Permission is revoked");
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

                return false;
            }
        }else{
            Toast.makeText(getContext(), "External Storage Permission is Grant", Toast.LENGTH_SHORT).show();
            Log.d("TAG", "External Storage Permission is Grant ");
            return true;
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (Build.VERSION.SDK_INT >= 23) {
            if(grantResults[0]== PackageManager.PERMISSION_GRANTED){
                Log.d("TAG","Permission: "+permissions[0]+ "was "+grantResults[0]);
                //resume tasks needing this permission
            }
        }
    }
}
