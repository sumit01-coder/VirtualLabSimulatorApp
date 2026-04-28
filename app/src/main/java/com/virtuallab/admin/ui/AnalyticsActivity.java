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
import com.virtuallab.admin.feature.AuditLog;
import com.virtuallab.admin.ui.list.SimpleLineAdapter;
import com.virtuallab.admin.ui.views.EdgeToEdge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AnalyticsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);
        EdgeToEdge.enable(this, findViewById(R.id.root), true, true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Analytics Trends");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView hint = findViewById(R.id.hintText);
        hint.setText("Local trend summary from audit events.");
        hint.setVisibility(View.VISIBLE);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        SimpleLineAdapter adapter = new SimpleLineAdapter();
        list.setAdapter(adapter);
        adapter.submit(buildTrendLines());
    }

    private List<String> buildTrendLines() {
        List<AuditLog.Entry> entries = AuditLog.read(this);
        long now = System.currentTimeMillis();
        long d1 = now - 24L * 60L * 60L * 1000L;
        long d7 = now - 7L * 24L * 60L * 60L * 1000L;

        int total = entries.size();
        int last24 = 0;
        int last7 = 0;
        int ticketOps = 0;
        int securityOps = 0;
        int settingsOps = 0;

        for (AuditLog.Entry e : entries) {
            if (e.ts >= d1) last24++;
            if (e.ts >= d7) last7++;
            String a = e.action == null ? "" : e.action.toLowerCase(Locale.US);
            if (a.contains("ticket")) ticketOps++;
            if (a.contains("ddos") || a.contains("security")) securityOps++;
            if (a.contains("setting")) settingsOps++;
        }

        List<String> out = new ArrayList<>();
        out.add("Total events: " + total);
        out.add("Last 24h events: " + last24);
        out.add("Last 7d events: " + last7);
        out.add("Ticket operations: " + ticketOps);
        out.add("Security operations: " + securityOps);
        out.add("Settings operations: " + settingsOps);
        out.add("Tip: Use this as quick operational trend until server analytics API is added.");
        return out;
    }
}
