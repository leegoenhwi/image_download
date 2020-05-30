package com.personal_project.image_download;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.personal_project.image_download.support.CustomDialog;


import java.util.Objects;

public class MainActivity extends AppCompatActivity  {

    private DrawerLayout drawer;
    private Toolbar  toolbar;
    private CustomDialog mEndDialog;
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        find_id();
        init_UI();
    }



    private void init_UI()
    {
        toolbar_init();
        setmainfragment();
        select_navigation();
    }

    private void toolbar_init()
    {
        setSupportActionBar(toolbar);


        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);

        drawer.addDrawerListener(toggle);
        toggle.syncState();
    }

    private void find_id()
    {
        toolbar = findViewById(R.id.toolbar_main);
        drawer = findViewById(R.id.DrawerLayout);
        navigationView = findViewById(R.id.nav_view);
    }


    private void select_navigation()
    {
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {

                        switch (menuItem.getItemId()){
                            case R.id.tutorial:
                                mEndDialog = new CustomDialog(MainActivity.this);
                                mEndDialog.setCancelable(false);
                                drawer.closeDrawers();
                                mEndDialog.show();
                                break;
                            case R.id.setting:
                                Intent setting_intent = new Intent(getBaseContext(),setting.class);
                                startActivity(setting_intent);
                                break;
                            case R.id.info:
                                Intent info_intent = new Intent(getBaseContext(),info.class);
                                startActivity(info_intent);
                                break;
                            case R.id.mail:
//                                Intent email = new Intent(Intent.ACTION_SENDTO);
//                                email.setData(Uri.parse("mailto:"));
//                                email.putExtra(Intent.EXTRA_EMAIL, getString(R.string.email));
//                                email.putExtra(Intent.EXTRA_SUBJECT, "<" + getString(R.string.app_name) +"_Questions"+ ">");
//
//                                startActivity(email);

                                String[] TO = { getString(R.string.email)};
                                Uri uri = Uri.parse("mailto:10131751z@gmail.com")
                                        .buildUpon()
                                        .build();
                                Intent emailIntent = new Intent(Intent.ACTION_SENDTO, uri);
                                emailIntent.putExtra(Intent.EXTRA_EMAIL, TO);
                                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "<" + "image download" +"_Questions"+ ">");
                                startActivity(Intent.createChooser(emailIntent, "Send mail..."));
                                break;

                        }

                        if(drawer.isEnabled()) {
                            drawer.closeDrawers();
                        }
                        return true;
                    }
                }
        );
    }

    private void setmainfragment()
    {
        MainFragment mainFragment = new MainFragment();
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.main_fragment,mainFragment).commit();
    }

}
