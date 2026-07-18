package com.virtuallab.admin.ui.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.virtuallab.admin.ui.utils.CustomAlertUtils;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.api.ApiClient;
import com.virtuallab.admin.api.ApiService;
import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.feature.AuditLog;
import com.virtuallab.admin.feature.FeaturePrefs;
import com.virtuallab.admin.model.ApiResponse;
import com.virtuallab.admin.model.DdosBlockedIp;
import com.virtuallab.admin.model.DdosOverview;
import com.virtuallab.admin.model.DdosRatePoint;
import com.virtuallab.admin.model.DdosRecentRequest;
import com.virtuallab.admin.model.DdosTopIp;
import com.virtuallab.admin.ui.list.DdosBlockedAdapter;
import com.virtuallab.admin.ui.list.DdosRecentAdapter;
import com.virtuallab.admin.ui.list.DdosTopIpsAdapter;
import com.virtuallab.admin.ui.views.DdosRateChartView;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class DdosFragment extends BaseAuthedFragment
        implements DdosTopIpsAdapter.Listener, DdosBlockedAdapter.Listener, DdosRecentAdapter.Listener {
    private static final int DEFAULT_AUTO_REFRESH_SECONDS = 5;
    private static final String ARG_SELECTED_IP = "selected_ip";

    public static DdosFragment newInstance(@Nullable String selectedIp) {
        DdosFragment fragment = new DdosFragment();
        Bundle args = new Bundle();
        if (selectedIp != null) {
            args.putString(ARG_SELECTED_IP, selectedIp);
        }
        fragment.setArguments(args);
        return fragment;
    }

    private ApiService api;
    private TokenStore store;

    private SwipeRefreshLayout swipe;
    private SwitchMaterial autoRefreshSwitch;
    private TextView lastUpdated;
    private View liveDot;
    private TextView statusTitle;
    private TextView statusMeta;
    private TextView statusRiskBadge;
    private TextView heroTrafficChip;
    private TextView heroBlockedChip;

    private TextView statReq5m;
    private TextView statReq60s;
    private TextView statReq10s;
    private TextView statBlocked;
    private TextView statUnique;
    private TextView statErrors;
    private TextView statErrorsTrend;
    private TextView statUniqueTrend;

    private DdosRateChartView rateChart;

    private RecyclerView blockedList;
    private RecyclerView topIpsList;
    private RecyclerView recentList;
    private TextView blockedEmpty;
    private TextView topEmpty;
    private TextView recentEmpty;
    private MaterialButton manualBlockBtn;
    private MaterialButton manualUnblockBtn;
    private MaterialButton presetRelaxedBtn;
    private MaterialButton presetNormalBtn;
    private MaterialButton presetStrictBtn;

    private DdosBlockedAdapter blockedAdapter;
    private DdosTopIpsAdapter topIpsAdapter;
    private DdosRecentAdapter recentAdapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int autoRefreshSeconds = DEFAULT_AUTO_REFRESH_SECONDS;
    private int countdown = DEFAULT_AUTO_REFRESH_SECONDS;
    private int inFlightLoads = 0;
    private String selectedIp = "";

    private Call<ApiResponse<DdosOverview>> overviewCall;
    private Call<ApiResponse<List<DdosBlockedIp>>> blockedCall;
    private Call<ApiResponse<List<DdosTopIp>>> topIpsCall;
    private Call<ApiResponse<List<DdosRecentRequest>>> recentCall;
    private Call<ApiResponse<List<DdosRatePoint>>> rateCall;
    private Call<ApiResponse<Map<String, Object>>> actionCall;

    private final Runnable autoTick = new Runnable() {
        @Override
        public void run() {
            if (!isAdded() || autoRefreshSwitch == null || !autoRefreshSwitch.isChecked()) {
                return;
            }
            countdown--;
            if (countdown <= 0) {
                countdown = autoRefreshSeconds;
                loadAll();
            }
            pulseLiveIndicator();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ddos, container, false);
        store = new TokenStore(requireContext());
        api = ApiClient.get(store);

        swipe = view.findViewById(R.id.swipe);
        autoRefreshSwitch = view.findViewById(R.id.autoRefreshSwitch);
        lastUpdated = view.findViewById(R.id.lastUpdated);
        liveDot = view.findViewById(R.id.liveDot);
        statusTitle = view.findViewById(R.id.statusTitle);
        statusMeta = view.findViewById(R.id.statusMeta);
        statusRiskBadge = view.findViewById(R.id.statusRiskBadge);
        heroTrafficChip = view.findViewById(R.id.heroTrafficChip);
        heroBlockedChip = view.findViewById(R.id.heroBlockedChip);

        statReq5m = view.findViewById(R.id.statReq5m);
        statReq60s = view.findViewById(R.id.statReq60s);
        statReq10s = view.findViewById(R.id.statReq10s);
        statBlocked = view.findViewById(R.id.statBlocked);
        statUnique = view.findViewById(R.id.statUnique);
        statErrors = view.findViewById(R.id.statErrors);
        statErrorsTrend = view.findViewById(R.id.statErrorsTrend);
        statUniqueTrend = view.findViewById(R.id.statUniqueTrend);
        rateChart = view.findViewById(R.id.rateChart);

        blockedList = view.findViewById(R.id.blockedList);
        topIpsList = view.findViewById(R.id.topIpsList);
        recentList = view.findViewById(R.id.recentList);
        blockedEmpty = view.findViewById(R.id.blockedEmpty);
        topEmpty = view.findViewById(R.id.topEmpty);
        recentEmpty = view.findViewById(R.id.recentEmpty);

        blockedAdapter = new DdosBlockedAdapter(this);
        topIpsAdapter = new DdosTopIpsAdapter(this);
        recentAdapter = new DdosRecentAdapter(this);

        blockedList.setLayoutManager(new LinearLayoutManager(getContext()));
        blockedList.setAdapter(blockedAdapter);
        topIpsList.setLayoutManager(new LinearLayoutManager(getContext()));
        topIpsList.setAdapter(topIpsAdapter);
        recentList.setLayoutManager(new LinearLayoutManager(getContext()));
        recentList.setAdapter(recentAdapter);

        if (getArguments() != null) {
            String argIp = getArguments().getString(ARG_SELECTED_IP, "");
            if (argIp != null && !argIp.trim().isEmpty()) {
                setSelectedIp(argIp.trim());
            }
        }

        manualBlockBtn = view.findViewById(R.id.blockBtn);
        if (manualBlockBtn != null) {
            manualBlockBtn.setOnClickListener(v -> showManualBlockDialog(selectedIp));
        }
        manualUnblockBtn = view.findViewById(R.id.unblockBtn);
        if (manualUnblockBtn != null) {
            manualUnblockBtn.setOnClickListener(v -> showManualUnblockDialog(selectedIp));
        }

        presetRelaxedBtn = view.findViewById(R.id.presetRelaxedBtn);
        presetNormalBtn = view.findViewById(R.id.presetNormalBtn);
        presetStrictBtn = view.findViewById(R.id.presetStrictBtn);
        if (presetRelaxedBtn != null) {
            presetRelaxedBtn.setOnClickListener(v -> applyPreset("relaxed"));
        }
        if (presetNormalBtn != null) {
            presetNormalBtn.setOnClickListener(v -> applyPreset("normal"));
        }
        if (presetStrictBtn != null) {
            presetStrictBtn.setOnClickListener(v -> applyPreset("strict"));
        }

        applyPreset(FeaturePrefs.getDdosPreset(requireContext()));
        updateManualBlockLabel();

        if (autoRefreshSwitch != null) {
            autoRefreshSwitch.setChecked(true);
            autoRefreshSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                countdown = autoRefreshSeconds;
                handler.removeCallbacks(autoTick);
                if (isChecked) {
                    handler.postDelayed(autoTick, 1000);
                } else if (liveDot != null) {
                    liveDot.animate().cancel();
                    liveDot.setAlpha(0.45f);
                }
            });
        }

        swipe.setOnRefreshListener(() -> {
            countdown = autoRefreshSeconds;
            loadAll();
        });

        loadAll();
        handler.postDelayed(autoTick, 1000);
        return view;
    }

    private void loadAll() {
        cancelLoads();
        if (swipe != null) {
            swipe.setRefreshing(true);
        }
        inFlightLoads = 5;

        overviewCall = api.ddosOverview("overview");
        overviewCall.enqueue(new Callback<ApiResponse<DdosOverview>>() {
            @Override
            public void onResponse(Call<ApiResponse<DdosOverview>> call, Response<ApiResponse<DdosOverview>> response) {
                if (!isAdded()) {
                    return;
                }
                finishOneLoad();
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                ApiResponse<DdosOverview> body = response.body();
                if (!response.isSuccessful() || body == null || !body.status || body.data == null) {
                    toast("Failed to load overview");
                    return;
                }
                bindOverview(body.data);
            }

            @Override
            public void onFailure(Call<ApiResponse<DdosOverview>> call, Throwable t) {
                if (!isAdded()) {
                    return;
                }
                finishOneLoad();
                if (!call.isCanceled()) {
                    toast("Network error: " + t.getMessage());
                }
            }
        });

        blockedCall = api.ddosBlocked("blocked");
        blockedCall.enqueue(new Callback<ApiResponse<List<DdosBlockedIp>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<DdosBlockedIp>>> call, Response<ApiResponse<List<DdosBlockedIp>>> response) {
                if (!isAdded()) {
                    return;
                }
                finishOneLoad();
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                ApiResponse<List<DdosBlockedIp>> body = response.body();
                if (!response.isSuccessful() || body == null || !body.status) {
                    toast("Failed to load blocked IPs");
                    return;
                }
                List<DdosBlockedIp> rows = body.data;
                blockedAdapter.submit(rows);
                if (blockedList != null) {
                    blockedList.scheduleLayoutAnimation();
                }
                if (blockedEmpty != null) {
                    blockedEmpty.setVisibility(rows == null || rows.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<DdosBlockedIp>>> call, Throwable t) {
                if (!isAdded()) {
                    return;
                }
                finishOneLoad();
                if (!call.isCanceled()) {
                    toast("Network error: " + t.getMessage());
                }
            }
        });

        topIpsCall = api.ddosTopIps("top_ips");
        topIpsCall.enqueue(new Callback<ApiResponse<List<DdosTopIp>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<DdosTopIp>>> call, Response<ApiResponse<List<DdosTopIp>>> response) {
                if (!isAdded()) {
                    return;
                }
                finishOneLoad();
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                ApiResponse<List<DdosTopIp>> body = response.body();
                if (!response.isSuccessful() || body == null || !body.status) {
                    toast("Failed to load top IPs");
                    return;
                }
                List<DdosTopIp> rows = body.data;
                topIpsAdapter.submit(rows);
                if (topIpsList != null) {
                    topIpsList.scheduleLayoutAnimation();
                }
                if (topEmpty != null) {
                    topEmpty.setVisibility(rows == null || rows.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<DdosTopIp>>> call, Throwable t) {
                if (!isAdded()) {
                    return;
                }
                finishOneLoad();
                if (!call.isCanceled()) {
                    toast("Network error: " + t.getMessage());
                }
            }
        });

        recentCall = api.ddosRecent("recent");
        recentCall.enqueue(new Callback<ApiResponse<List<DdosRecentRequest>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<DdosRecentRequest>>> call, Response<ApiResponse<List<DdosRecentRequest>>> response) {
                if (!isAdded()) {
                    return;
                }
                finishOneLoad();
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                ApiResponse<List<DdosRecentRequest>> body = response.body();
                if (!response.isSuccessful() || body == null || !body.status) {
                    toast("Failed to load recent requests");
                    return;
                }
                List<DdosRecentRequest> rows = body.data;
                recentAdapter.submit(rows);
                if (recentList != null) {
                    recentList.scheduleLayoutAnimation();
                }
                if (recentEmpty != null) {
                    recentEmpty.setVisibility(rows == null || rows.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<DdosRecentRequest>>> call, Throwable t) {
                if (!isAdded()) {
                    return;
                }
                finishOneLoad();
                if (!call.isCanceled()) {
                    toast("Network error: " + t.getMessage());
                }
            }
        });

        rateCall = api.ddosRateHistory("rate_history");
        rateCall.enqueue(new Callback<ApiResponse<List<DdosRatePoint>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<DdosRatePoint>>> call, Response<ApiResponse<List<DdosRatePoint>>> response) {
                if (!isAdded()) {
                    return;
                }
                finishOneLoad();
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                ApiResponse<List<DdosRatePoint>> body = response.body();
                if (response.isSuccessful() && body != null && body.status && rateChart != null) {
                    rateChart.setPoints(body.data);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<DdosRatePoint>>> call, Throwable t) {
                if (!isAdded()) {
                    return;
                }
                finishOneLoad();
            }
        });
    }

    private void finishOneLoad() {
        inFlightLoads = Math.max(0, inFlightLoads - 1);
        if (inFlightLoads == 0 && swipe != null) {
            swipe.setRefreshing(false);
        }
    }

    private void bindOverview(DdosOverview overview) {
        if (overview == null) {
            return;
        }
        if (statReq5m != null) {
            statReq5m.setText("5m window " + overview.reqs_5min);
        }
        if (statReq60s != null) {
            statReq60s.setText(String.valueOf(overview.reqs_60s));
        }
        if (statReq10s != null) {
            statReq10s.setText("10s burst " + overview.reqs_10s);
        }
        if (statBlocked != null) {
            statBlocked.setText(String.valueOf(overview.blocked_now));
        }
        if (statUnique != null) {
            statUnique.setText(String.valueOf(overview.unique_ips));
        }
        if (statErrors != null) {
            statErrors.setText(String.valueOf(overview.error_reqs));
        }
        if (statErrorsTrend != null) {
            statErrorsTrend.setText(overview.error_reqs > 0 ? "Errors rising" : "No edge errors");
        }
        if (statUniqueTrend != null) {
            statUniqueTrend.setText(overview.unique_ips > 0 ? "Distributed traffic" : "No unique sources");
        }
        if (heroTrafficChip != null) {
            heroTrafficChip.setText("60s rate " + overview.reqs_60s);
        }
        if (heroBlockedChip != null) {
            heroBlockedChip.setText("Blocked " + overview.blocked_now);
        }

        long timestampMs = overview.ts > 0 ? overview.ts * 1000L : System.currentTimeMillis();
        if (lastUpdated != null) {
            SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            lastUpdated.setText("Last updated " + format.format(timestampMs));
        }

        int risk = resolveRisk(overview);
        if (statusTitle != null) {
            statusTitle.setText(risk >= 2 ? "Under Attack" : "Protected");
        }
        if (statusMeta != null) {
            if (risk >= 2) {
                statusMeta.setText("Traffic spikes and error bursts triggered active mitigation.");
            } else if (risk == 1) {
                statusMeta.setText("Elevated traffic detected. Monitoring and rate controls are active.");
            } else {
                statusMeta.setText("Traffic is stable and mitigation is active.");
            }
        }
        if (statusRiskBadge != null) {
            if (risk >= 2) {
                statusRiskBadge.setText("High Risk");
                statusRiskBadge.setBackgroundResource(R.drawable.bg_ddos_badge_danger);
                statusRiskBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.ddos_red));
            } else if (risk == 1) {
                statusRiskBadge.setText("Medium Risk");
                statusRiskBadge.setBackgroundResource(R.drawable.bg_ddos_badge_warn);
                statusRiskBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.ddos_yellow));
            } else {
                statusRiskBadge.setText("Low Risk");
                statusRiskBadge.setBackgroundResource(R.drawable.bg_ddos_badge_safe);
                statusRiskBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.ddos_green));
            }
        }
    }

    private int resolveRisk(DdosOverview overview) {
        boolean high = overview.error_reqs >= 20 || overview.blocked_now >= 10 || overview.reqs_60s >= 1200 || overview.reqs_10s >= 260;
        boolean medium = overview.error_reqs >= 5 || overview.blocked_now >= 3 || overview.reqs_60s >= 400 || overview.reqs_10s >= 120;
        if (high) {
            return 2;
        }
        if (medium) {
            return 1;
        }
        return 0;
    }

    private void pulseLiveIndicator() {
        if (liveDot == null || autoRefreshSwitch == null || !autoRefreshSwitch.isChecked()) {
            return;
        }
        liveDot.animate().cancel();
        liveDot.animate().alpha(0.35f).setDuration(350L).withEndAction(() -> {
            if (liveDot != null && autoRefreshSwitch != null && autoRefreshSwitch.isChecked()) {
                liveDot.animate().alpha(1f).setDuration(350L).start();
            }
        }).start();
    }

    private void showManualBlockDialog(String prefillIp) {
        if (!isAdded()) {
            return;
        }
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_block_ip, null, false);
        TextInputEditText ipInput = content.findViewById(R.id.ipInput);
        TextInputEditText durationInput = content.findViewById(R.id.durationInput);
        if (durationInput != null) {
            durationInput.setText("3600");
        }
        if (ipInput != null && prefillIp != null && !prefillIp.trim().isEmpty()) {
            ipInput.setText(prefillIp.trim());
            if (ipInput.getText() != null) {
                ipInput.setSelection(ipInput.getText().length());
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Block IP")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Block", (dialog, which) -> {
                    String ip = ipInput != null && ipInput.getText() != null ? ipInput.getText().toString().trim() : "";
                    String durationRaw = durationInput != null && durationInput.getText() != null ? durationInput.getText().toString().trim() : "";
                    int duration = parseDurationSeconds(durationRaw);
                    if (TextUtils.isEmpty(ip)) {
                        toast("Enter an IP address");
                        return;
                    }
                    doBlock(ip, duration);
                })
                .show();
    }

    private void showManualUnblockDialog(String prefillIp) {
        if (!isAdded()) {
            return;
        }
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_block_ip, null, false);
        TextInputEditText ipInput = content.findViewById(R.id.ipInput);
        TextInputEditText durationInput = content.findViewById(R.id.durationInput);
        if (durationInput != null && durationInput.getParent() != null && durationInput.getParent().getParent() instanceof View) {
            ((View) durationInput.getParent().getParent()).setVisibility(View.GONE); // Hide duration field for unblock
        }
        if (ipInput != null && prefillIp != null && !prefillIp.trim().isEmpty()) {
            ipInput.setText(prefillIp.trim());
            if (ipInput.getText() != null) {
                ipInput.setSelection(ipInput.getText().length());
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Unblock IP")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Unblock", (dialog, which) -> {
                    String ip = ipInput != null && ipInput.getText() != null ? ipInput.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(ip)) {
                        toast("Enter an IP address");
                        return;
                    }
                    doUnblock(ip);
                })
                .show();
    }

    private int parseDurationSeconds(String raw) {
        int duration = 3600;
        if (!TextUtils.isEmpty(raw)) {
            try {
                duration = Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
                duration = 3600;
            }
        }
        if (duration <= 0) {
            duration = 3600;
        }
        if (duration > 86400) {
            duration = 86400;
        }
        return duration;
    }

    @Override
    public void onQuickBlock(DdosTopIp ip) {
        if (ip == null || TextUtils.isEmpty(ip.ip) || !isAdded()) {
            return;
        }
        CustomAlertUtils.showConfirmation(requireContext(),
                "Block " + ip.ip + "?",
                "Are you sure you want to block this IP for 1 hour? It will not be able to access the API.",
                "Block", "Cancel",
                R.drawable.ic_warning, R.color.ddos_red,
                () -> doBlock(ip.ip, 3600));
    }

    @Override
    public void onUnblock(DdosBlockedIp ip) {
        if (ip == null || TextUtils.isEmpty(ip.ip) || !isAdded()) {
            return;
        }
        CustomAlertUtils.showConfirmation(requireContext(),
                "Unblock " + ip.ip + "?",
                "Remove this IP from the block list? It will regain access immediately.",
                "Unblock", "Cancel",
                R.drawable.ic_check_circle, R.color.ddos_green,
                () -> doUnblock(ip.ip));
    }

    @Override
    public void onSelectIp(String ip) {
        if (!isAdded()) {
            return;
        }
        setSelectedIp(ip);
        showIpActions(ip, false);
    }

    @Override
    public void onSelectBlockedIp(String ip) {
        if (!isAdded()) {
            return;
        }
        setSelectedIp(ip);
        showIpActions(ip, true);
    }

    private void setSelectedIp(String ip) {
        selectedIp = ip != null ? ip : "";
        if (topIpsAdapter != null) {
            topIpsAdapter.setSelectedIp(selectedIp);
        }
        if (blockedAdapter != null) {
            blockedAdapter.setSelectedIp(selectedIp);
        }
        if (recentAdapter != null) {
            recentAdapter.setSelectedIp(selectedIp);
        }
        updateManualBlockLabel();
    }

    private void updateManualBlockLabel() {
        if (manualBlockBtn != null) {
            manualBlockBtn.setText(selectedIp == null || selectedIp.trim().isEmpty() ? "Block IP" : "Block selected");
        }
        if (manualUnblockBtn != null) {
            manualUnblockBtn.setText(selectedIp == null || selectedIp.trim().isEmpty() ? "Unblock IP" : "Unblock selected");
        }
    }

    private void applyPreset(String preset) {
        if (!isAdded()) {
            return;
        }
        String normalized = preset == null ? "normal" : preset.trim().toLowerCase(Locale.US);
        if (!"strict".equals(normalized) && !"relaxed".equals(normalized)) {
            normalized = "normal";
        }

        if ("strict".equals(normalized)) {
            autoRefreshSeconds = 2;
        } else if ("relaxed".equals(normalized)) {
            autoRefreshSeconds = 10;
        } else {
            autoRefreshSeconds = DEFAULT_AUTO_REFRESH_SECONDS;
        }
        countdown = autoRefreshSeconds;
        FeaturePrefs.setDdosPreset(requireContext(), normalized);

        updatePresetButton(presetRelaxedBtn, "relaxed".equals(normalized));
        updatePresetButton(presetNormalBtn, "normal".equals(normalized));
        updatePresetButton(presetStrictBtn, "strict".equals(normalized));
    }

    private void updatePresetButton(@Nullable MaterialButton button, boolean selected) {
        if (button == null || !isAdded()) {
            return;
        }
        button.setBackgroundResource(selected ? R.drawable.bg_ddos_segment_active : R.drawable.bg_ddos_segment_inactive);
        button.setTextColor(ContextCompat.getColor(requireContext(), selected ? android.R.color.white : R.color.ddos_text_secondary));
        button.setRippleColor(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.ddos_blue)));
    }

    private void showIpActions(String ip, boolean isBlocked) {
        if (!isAdded() || TextUtils.isEmpty(ip)) {
            return;
        }

        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        labels.add("Copy IP");
        if (isBlocked) {
            labels.add("Unblock");
        }
        labels.add("Block 10 minutes");
        labels.add("Block 1 hour");
        labels.add("Block 24 hours");
        labels.add("Custom duration...");
        if (selectedIp != null && !selectedIp.trim().isEmpty()) {
            labels.add("Clear selection");
        }

        String[] items = labels.toArray(new String[0]);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(ip)
                .setItems(items, (dialog, which) -> {
                    String choice = items[which];
                    if ("Copy IP".equals(choice)) {
                        copyToClipboard(ip);
                        toast("Copied " + ip);
                    } else if ("Unblock".equals(choice)) {
                        confirmUnblock(ip);
                    } else if ("Block 10 minutes".equals(choice)) {
                        doBlock(ip, 600);
                    } else if ("Block 1 hour".equals(choice)) {
                        doBlock(ip, 3600);
                    } else if ("Block 24 hours".equals(choice)) {
                        doBlock(ip, 86400);
                    } else if ("Custom duration...".equals(choice)) {
                        showManualBlockDialog(ip);
                    } else if ("Clear selection".equals(choice)) {
                        setSelectedIp("");
                    }
                })
                .show();
    }

    private void confirmUnblock(String ip) {
        if (!isAdded() || TextUtils.isEmpty(ip)) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Unblock " + ip + "?")
                .setMessage("Remove this IP from the block list?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Unblock", (dialog, which) -> doUnblock(ip))
                .show();
    }

    private void copyToClipboard(String ip) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        ClipboardManager clipboardManager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("IP", ip));
        }
    }

    private void doBlock(String ip, int durationSeconds) {
        if (actionCall != null) {
            actionCall.cancel();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("ip", ip);
        body.put("duration", durationSeconds);
        actionCall = api.ddosBlock(body);
        actionCall.enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                if (!isAdded()) {
                    return;
                }
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                ApiResponse<Map<String, Object>> result = response.body();
                if (!response.isSuccessful() || result == null || !result.status) {
                    toast("Failed to block IP");
                    return;
                }
                toast("Blocked " + ip);
                countdown = autoRefreshSeconds;
                AuditLog.write(requireContext(), store.getUsername(), "ddos.block", "ip=" + ip + ", duration=" + durationSeconds);
                loadAll();
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                if (!isAdded() || call.isCanceled()) {
                    return;
                }
                toast("Network error: " + t.getMessage());
            }
        });
    }

    private void doUnblock(String ip) {
        if (actionCall != null) {
            actionCall.cancel();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("ip", ip);
        actionCall = api.ddosUnblock(body);
        actionCall.enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                if (!isAdded()) {
                    return;
                }
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                ApiResponse<Map<String, Object>> result = response.body();
                if (!response.isSuccessful() || result == null || !result.status) {
                    toast("Failed to unblock IP");
                    return;
                }
                toast("Unblocked " + ip);
                countdown = autoRefreshSeconds;
                AuditLog.write(requireContext(), store.getUsername(), "ddos.unblock", "ip=" + ip);
                loadAll();
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                if (!isAdded() || call.isCanceled()) {
                    return;
                }
                toast("Network error: " + t.getMessage());
            }
        });
    }

    private void toast(String msg) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        }
    }

    private void cancelLoads() {
        if (overviewCall != null) {
            overviewCall.cancel();
            overviewCall = null;
        }
        if (blockedCall != null) {
            blockedCall.cancel();
            blockedCall = null;
        }
        if (topIpsCall != null) {
            topIpsCall.cancel();
            topIpsCall = null;
        }
        if (recentCall != null) {
            recentCall.cancel();
            recentCall = null;
        }
        if (rateCall != null) {
            rateCall.cancel();
            rateCall = null;
        }
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(autoTick);
        cancelLoads();
        if (actionCall != null) {
            actionCall.cancel();
            actionCall = null;
        }
        swipe = null;
        autoRefreshSwitch = null;
        lastUpdated = null;
        liveDot = null;
        statusTitle = null;
        statusMeta = null;
        statusRiskBadge = null;
        heroTrafficChip = null;
        heroBlockedChip = null;
        rateChart = null;
        blockedList = null;
        topIpsList = null;
        recentList = null;
        blockedEmpty = null;
        topEmpty = null;
        recentEmpty = null;
        manualBlockBtn = null;
        presetRelaxedBtn = null;
        presetNormalBtn = null;
        presetStrictBtn = null;
        super.onDestroyView();
    }
}
