package com.virtuallab.admin.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.sumit.virtuallabadmin.v28.R;
import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.ui.fragments.UsersFragment;
import com.virtuallab.admin.ui.views.EdgeToEdge;

public final class UsersActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_users);
        EdgeToEdge.enable(this, findViewById(R.id.root), true, true);

        TokenStore store = new TokenStore(this);
        if (!store.hasToken()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Users");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, new UsersFragment())
                    .commit();
        }
    }
}

