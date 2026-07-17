package com.virtuallab.admin.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.feature.AuditLog;
import com.virtuallab.admin.ui.list.SimpleLineAdapter;
import com.virtuallab.admin.ui.views.EdgeToEdge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AuditLogActivity extends AppCompatActivity {
    private final SimpleLineAdapter adapter = new SimpleLineAdapter();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);
        EdgeToEdge.enable(this, findViewById(R.id.root), true, true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Audit Log");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnLongClickListener(v -> {
            confirmClear();
            return true;
        });

        TextView hint = findViewById(R.id.hintText);
        hint.setText("Long-press title bar to clear local audit log.");
        hint.setVisibility(View.VISIBLE);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        reload();
    }

    private void reload() {
        List<AuditLog.Entry> rows = AuditLog.read(this);
        Collections.reverse(rows);
        List<String> lines = new ArrayList<>();
        for (AuditLog.Entry e : rows) {
            lines.add(e.tsLabel() + " | " + e.actor + " | " + e.action + " | " + e.detail);
        }
        adapter.submit(lines);
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear audit log?")
                .setMessage("This clears locally stored audit events.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (d, which) -> {
                    AuditLog.clear(this);
                    reload();
                })
                .show();
    }
}
