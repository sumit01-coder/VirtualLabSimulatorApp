package com.virtuallab.admin.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.feature.PermissionMatrix;
import com.virtuallab.admin.ui.list.SimpleLineAdapter;
import com.virtuallab.admin.ui.views.EdgeToEdge;

public final class PermissionMatrixActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);
        EdgeToEdge.enable(this, findViewById(R.id.root), true, true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Permissions Matrix");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        TokenStore s = new TokenStore(this);
        String role = s.getRole();
        TextView hint = findViewById(R.id.hintText);
        hint.setText("Current role: " + (role == null || role.trim().isEmpty() ? "(none)" : role));
        hint.setVisibility(android.view.View.VISIBLE);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        SimpleLineAdapter a = new SimpleLineAdapter();
        list.setAdapter(a);
        a.submit(PermissionMatrix.readableForRole(role));
    }
}
