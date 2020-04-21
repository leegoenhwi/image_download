package com.personal_project.image_download;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

public class MainFragment extends Fragment implements View.OnClickListener{

    private TextView download_button;
    private TextView clipboard_button;
    private View view;

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
    }

    private void initui()
    {
        download_button.setOnClickListener(this);
        clipboard_button.setOnClickListener(this);

    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.download_button:
                Intent Download_intent = new Intent(view.getContext(),Download.class);
                Download_intent.putExtra("USERNAME_KEY","jizard"); //키 - 보낼 값(밸류)
                Download_intent.putExtra("BIRTHDAY_KEY",119);
                startActivity(Download_intent);

                break;
            case R.id.clipboard_button:

                break;
        }
    }
}
