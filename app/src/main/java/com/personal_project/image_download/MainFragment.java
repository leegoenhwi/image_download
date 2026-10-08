package com.personal_project.image_download;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.personal_project.image_download.support.ImageExtractor;
import com.personal_project.image_download.support.RecentUrls;

/** 첫 화면: 주소 입력(붙여넣기/지우기), 복사한 주소 제안, 최근 주소, 사용 팁 */
public class MainFragment extends Fragment implements View.OnClickListener {

    private View view;
    private EditText text_input;
    private View clearButton;
    private TextView urlError;
    private View clipboardCard;
    private TextView clipboardUrl;
    private View recentSection;
    private LinearLayout recentList;

    /** 클립보드 제안에 쓸 주소 */
    private String clipboardSuggestion;
    /** 같은 복사 내용을 여러 번 읽지 않기 위한 표시 (Android 12+ 는 읽을 때마다 시스템 알림이 뜬다) */
    private long lastClipTimestamp = -1;
    private String lastClipLabel;

    private final ViewTreeObserver.OnWindowFocusChangeListener focusListener = new ViewTreeObserver.OnWindowFocusChangeListener() {
        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
            // Android 10+ 는 앱이 포커스를 가진 뒤에만 클립보드를 읽을 수 있다
            if (hasFocus) checkClipboard();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.main_fragment, container, false);
        text_input = view.findViewById(R.id.text_input);
        clearButton = view.findViewById(R.id.clear_button);
        urlError = view.findViewById(R.id.url_error);
        clipboardCard = view.findViewById(R.id.clipboard_card);
        clipboardUrl = view.findViewById(R.id.clipboard_url);
        recentSection = view.findViewById(R.id.recent_section);
        recentList = view.findViewById(R.id.recent_list);

        view.findViewById(R.id.find_button).setOnClickListener(this);
        view.findViewById(R.id.paste_button).setOnClickListener(this);
        view.findViewById(R.id.clipboard_use).setOnClickListener(this);
        view.findViewById(R.id.recent_clear).setOnClickListener(this);
        clearButton.setOnClickListener(this);

        text_input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clearButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                urlError.setVisibility(View.GONE);
            }
        });
        text_input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN;
                if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                    search();
                    return true;
                }
                return false;
            }
        });
        view.getViewTreeObserver().addOnWindowFocusChangeListener(focusListener);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        showRecent();
        if (view.hasWindowFocus()) checkClipboard();
    }

    @Override
    public void onDestroyView() {
        view.getViewTreeObserver().removeOnWindowFocusChangeListener(focusListener);
        super.onDestroyView();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.find_button) {
            search();
        } else if (id == R.id.paste_button) {
            paste();
        } else if (id == R.id.clear_button) {
            text_input.setText("");
            text_input.requestFocus();
        } else if (id == R.id.clipboard_use) {
            if (clipboardSuggestion != null) {
                text_input.setText(clipboardSuggestion);
                clipboardCard.setVisibility(View.GONE);
                search();
            }
        } else if (id == R.id.recent_clear) {
            RecentUrls.clear(getContext());
            showRecent();
        }
    }

    /** 입력한 글에서 주소를 꺼내 결과 화면으로 */
    private void search() {
        String url = ImageExtractor.normalizeUrl(text_input.getText().toString());
        if (url == null) {
            urlError.setVisibility(View.VISIBLE);
            return;
        }
        RecentUrls.add(getContext(), url);
        hideKeyboard();
        Intent intent = new Intent(getContext(), Download.class);
        intent.putExtra("URL_KEY", url);
        startActivity(intent);
    }

    private void paste() {
        CharSequence text = readClipboard();
        if (text == null || text.toString().trim().isEmpty()) {
            Toast.makeText(getContext(), R.string.no_clipboard, Toast.LENGTH_SHORT).show();
            return;
        }
        String url = ImageExtractor.normalizeUrl(text.toString());
        text_input.setText(url != null ? url : text.toString().trim());
        text_input.setSelection(text_input.getText().length());
        clipboardCard.setVisibility(View.GONE);
    }

    /** 새로 복사한 주소가 있으면 "복사한 주소 · 사용" 카드를 보여준다 */
    private void checkClipboard() {
        ClipboardManager cm = clipboard();
        if (cm == null || !cm.hasPrimaryClip()) {
            clipboardCard.setVisibility(View.GONE);
            return;
        }
        ClipDescription desc = cm.getPrimaryClipDescription();
        if (desc == null || !desc.hasMimeType("text/*")) return;
        long stamp = Build.VERSION.SDK_INT >= 26 ? desc.getTimestamp() : -1;
        String label = String.valueOf(desc.getLabel());
        if (stamp != -1 && stamp == lastClipTimestamp && label.equals(lastClipLabel)) return;   // 이미 확인한 복사 내용
        lastClipTimestamp = stamp;
        lastClipLabel = label;

        CharSequence text = readClipboard();
        String url = text == null ? null : ImageExtractor.normalizeUrl(text.toString());
        String typed = ImageExtractor.normalizeUrl(text_input.getText().toString());
        if (url == null || url.equals(typed) || !text.toString().contains("://")) {
            clipboardCard.setVisibility(View.GONE);
            return;
        }
        clipboardSuggestion = url;
        clipboardUrl.setText(url);
        clipboardCard.setVisibility(View.VISIBLE);
    }

    private CharSequence readClipboard() {
        ClipboardManager cm = clipboard();
        if (cm == null || !cm.hasPrimaryClip()) return null;
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return null;
        return clip.getItemAt(0).coerceToText(getContext());
    }

    private ClipboardManager clipboard() {
        Context c = getContext();
        return c == null ? null : (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    /** 최근 주소 (누르면 바로 다시 연다) */
    private void showRecent() {
        Context c = getContext();
        if (c == null) return;
        recentList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(c);
        for (final String url : RecentUrls.get(c)) {
            View row = inflater.inflate(R.layout.item_recent, recentList, false);
            String host = Uri.parse(url).getHost();
            ((TextView) row.findViewById(R.id.recent_host)).setText(host != null ? host.replaceFirst("^www\\.", "") : url);
            ((TextView) row.findViewById(R.id.recent_url)).setText(url);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    text_input.setText(url);
                    search();
                }
            });
            recentList.addView(row);
        }
        recentSection.setVisibility(recentList.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void hideKeyboard() {
        Context c = getContext();
        InputMethodManager imm = c == null ? null : (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(text_input.getWindowToken(), 0);
    }
}
