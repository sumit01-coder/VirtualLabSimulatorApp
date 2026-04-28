package com.virtuallab.admin.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.feature.ApiHealthChecker;
import com.virtuallab.admin.ui.list.SimpleLineAdapter;
import com.virtuallab.admin.ui.views.EdgeToEdge;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public final class ApiHealthActivity extends AppCompatActivity {
    private final SimpleLineAdapter adapter = new SimpleLineAdapter();
    private TextView hint;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);
        EdgeToEdge.enable(this, findViewById(R.id.root), true, true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("API Health");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            load();
            return true;
        });

        hint = findViewById(R.id.hintText);
        hint.setText("Checking...");
        hint.setVisibility(View.VISIBLE);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        load();
    }

    private void load() {
        hint.setText("Checking endpoints...");
        Executors.newSingleThreadExecutor().execute(() -> {
            List<ApiHealthChecker.Row> rows = ApiHealthChecker.run();
            List<String> lines = new ArrayList<>();
            int ok = 0;
            for (ApiHealthChecker.Row r : rows) {
                if (r.ok) ok++;
                lines.add((r.ok ? "OK " : "ERR ")
                        + r.endpoint
                        + " | code=" + r.code
                        + " | " + r.latencyMs + "ms");
            }
            final int okCount = ok;
            runOnUiThread(() -> {
                adapter.submit(lines);
                hint.setText("Healthy endpoints: " + okCount + "/" + rows.size());
            });
        });
    }
}
