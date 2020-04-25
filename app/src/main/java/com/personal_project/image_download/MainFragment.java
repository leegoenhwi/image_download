package com.personal_project.image_download;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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

                break;
        }
    }
}
