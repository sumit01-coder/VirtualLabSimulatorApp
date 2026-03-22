package com.virtuallab.admin.ui.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.virtuallab.admin.R;
import com.virtuallab.admin.api.ApiClient;
import com.virtuallab.admin.api.ApiService;
import com.virtuallab.admin.data.TokenStore;
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
    private static final int AUTO_REFRESH_SECONDS = 5;
    private static final String ARG_SELECTED_IP = "selected_ip";

    public static DdosFragment newInstance(@Nullable String selectedIp) {
        DdosFragment f = new DdosFragment();
        Bundle b = new Bundle();
        if (selectedIp != null) b.putString(ARG_SELECTED_IP, selectedIp);
        f.setArguments(b);
        return f;
    }

    private ApiService api;
    private TokenStore store;

    private SwipeRefreshLayout swipe;
    private SwitchMaterial autoRefreshSwitch;
    private TextView lastUpdated;

    private TextView statReq5m;
    private TextView statReq60s;
    private TextView statReq10s;
    private TextView statBlocked;
    private TextView statUnique;
    private TextView statErrors;

    private DdosRateChartView rateChart;

    private RecyclerView blockedList;
    private RecyclerView topIpsList;
    private RecyclerView recentList;
    private TextView blockedEmpty;
    private TextView topEmpty;
    private TextView recentEmpty;
    private MaterialButton manualBlockBtn;

    private DdosBlockedAdapter blockedAdapter;
    private DdosTopIpsAdapter topIpsAdapter;
    private DdosRecentAdapter recentAdapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int countdown = AUTO_REFRESH_SECONDS;

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
            if (!isAdded()) return;
            if (autoRefreshSwitch == null || !autoRefreshSwitch.isChecked()) return;

            countdown--;
            if (countdown <= 0) {
                countdown = AUTO_REFRESH_SECONDS;
                loadAll();
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_ddos, container, false);
        store = new TokenStore(requireContext());
        api = ApiClient.get(store);

        swipe = v.findViewById(R.id.swipe);
        autoRefreshSwitch = v.findViewById(R.id.autoRefreshSwitch);
        lastUpdated = v.findViewById(R.id.lastUpdated);

        statReq5m = v.findViewById(R.id.statReq5m);
        statReq60s = v.findViewById(R.id.statReq60s);
        statReq10s = v.findViewById(R.id.statReq10s);
        statBlocked = v.findViewById(R.id.statBlocked);
        statUnique = v.findViewById(R.id.statUnique);
        statErrors = v.findViewById(R.id.statErrors);
        rateChart = v.findViewById(R.id.rateChart);

        blockedList = v.findViewById(R.id.blockedList);
        topIpsList = v.findViewById(R.id.topIpsList);
        recentList = v.findViewById(R.id.recentList);
        blockedEmpty = v.findViewById(R.id.blockedEmpty);
        topEmpty = v.findViewById(R.id.topEmpty);
        recentEmpty = v.findViewById(R.id.recentEmpty);

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

        manualBlockBtn = v.findViewById(R.id.blockBtn);
        if (manualBlockBtn != null) manualBlockBtn.setOnClickListener(view -> showManualBlockDialog(selectedIp));
        updateManualBlockLabel();

        if (autoRefreshSwitch != null) {
            autoRefreshSwitch.setChecked(true);
            autoRefreshSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                countdown = AUTO_REFRESH_SECONDS;
                handler.removeCallbacks(autoTick);
                if (isChecked) handler.postDelayed(autoTick, 1000);
            });
        }

        swipe.setOnRefreshListener(() -> {
            countdown = AUTO_REFRESH_SECONDS;
            loadAll();
        });

        loadAll();
        handler.postDelayed(autoTick, 1000);
        return v;
    }

    private void loadAll() {
        cancelLoads();
        if (swipe != null) swipe.setRefreshing(true);
        inFlightLoads = 5;

        overviewCall = api.ddosOverview("overview");
        overviewCall.enqueue(new Callback<ApiResponse<DdosOverview>>() {
            @Override
            public void onResponse(Call<ApiResponse<DdosOverview>> call, Response<ApiResponse<DdosOverview>> response) {
                if (!isAdded()) return;
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
                if (!isAdded()) return;
                finishOneLoad();
                if (call.isCanceled()) return;
                toast("Network error: " + t.getMessage());
            }
        });

        blockedCall = api.ddosBlocked("blocked");
        blockedCall.enqueue(new Callback<ApiResponse<List<DdosBlockedIp>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<DdosBlockedIp>>> call, Response<ApiResponse<List<DdosBlockedIp>>> response) {
                if (!isAdded()) return;
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
                if (blockedList != null) blockedList.scheduleLayoutAnimation();
                if (blockedEmpty != null) blockedEmpty.setVisibility(rows == null || rows.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<DdosBlockedIp>>> call, Throwable t) {
                if (!isAdded()) return;
                finishOneLoad();
                if (call.isCanceled()) return;
                toast("Network error: " + t.getMessage());
            }
        });

        topIpsCall = api.ddosTopIps("top_ips");
        topIpsCall.enqueue(new Callback<ApiResponse<List<DdosTopIp>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<DdosTopIp>>> call, Response<ApiResponse<List<DdosTopIp>>> response) {
                if (!isAdded()) return;
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
                if (topIpsList != null) topIpsList.scheduleLayoutAnimation();
                if (topEmpty != null) topEmpty.setVisibility(rows == null || rows.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<DdosTopIp>>> call, Throwable t) {
                if (!isAdded()) return;
                finishOneLoad();
                if (call.isCanceled()) return;
                toast("Network error: " + t.getMessage());
            }
        });

        recentCall = api.ddosRecent("recent");
        recentCall.enqueue(new Callback<ApiResponse<List<DdosRecentRequest>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<DdosRecentRequest>>> call, Response<ApiResponse<List<DdosRecentRequest>>> response) {
                if (!isAdded()) return;
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
                if (recentList != null) recentList.scheduleLayoutAnimation();
                if (recentEmpty != null) recentEmpty.setVisibility(rows == null || rows.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<DdosRecentRequest>>> call, Throwable t) {
                if (!isAdded()) return;
                finishOneLoad();
                if (call.isCanceled()) return;
                toast("Network error: " + t.getMessage());
            }
        });

        rateCall = api.ddosRateHistory("rate_history");
        rateCall.enqueue(new Callback<ApiResponse<List<DdosRatePoint>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<DdosRatePoint>>> call, Response<ApiResponse<List<DdosRatePoint>>> response) {
                if (!isAdded()) return;
                finishOneLoad();
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                ApiResponse<List<DdosRatePoint>> body = response.body();
                if (!response.isSuccessful() || body == null || !body.status) {
                    return;
                }
                if (rateChart != null) rateChart.setPoints(body.data);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<DdosRatePoint>>> call, Throwable t) {
                if (!isAdded()) return;
                finishOneLoad();
            }
        });
    }

    private void finishOneLoad() {
        inFlightLoads = Math.max(0, inFlightLoads - 1);
        if (inFlightLoads == 0 && swipe != null) swipe.setRefreshing(false);
    }

    private void bindOverview(DdosOverview o) {
        if (o == null) return;
        if (statReq5m != null) statReq5m.setText(String.valueOf(o.reqs_5min));
        if (statReq60s != null) statReq60s.setText(String.valueOf(o.reqs_60s));
        if (statReq10s != null) statReq10s.setText(String.valueOf(o.reqs_10s));
        if (statBlocked != null) statBlocked.setText(String.valueOf(o.blocked_now));
        if (statUnique != null) statUnique.setText(String.valueOf(o.unique_ips));
        if (statErrors != null) statErrors.setText(String.valueOf(o.error_reqs));

        long tsMs = o.ts > 0 ? (o.ts * 1000L) : System.currentTimeMillis();
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        if (lastUpdated != null) lastUpdated.setText("Updated: " + f.format(tsMs));
    }

    private void showManualBlockDialog(String prefillIp) {
        if (!isAdded()) return;
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_block_ip, null, false);
        TextInputEditText ipInput = content.findViewById(R.id.ipInput);
        TextInputEditText durationInput = content.findViewById(R.id.durationInput);
        if (durationInput != null) durationInput.setText("3600");
        if (ipInput != null && prefillIp != null && !prefillIp.trim().isEmpty()) {
            ipInput.setText(prefillIp.trim());
            if (ipInput.getText() != null) ipInput.setSelection(ipInput.getText().length());
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Block IP")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Block", (d, which) -> {
                    String ip = ipInput != null && ipInput.getText() != null ? ipInput.getText().toString().trim() : "";
                    String durRaw = durationInput != null && durationInput.getText() != null ? durationInput.getText().toString().trim() : "";
                    int duration = parseDurationSeconds(durRaw);
                    if (TextUtils.isEmpty(ip)) {
                        toast("Enter an IP address");
                        return;
                    }
                    doBlock(ip, duration);
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
        if (duration <= 0) duration = 3600;
        if (duration > 86400) duration = 86400;
        return duration;
    }

    @Override
    public void onQuickBlock(DdosTopIp ip) {
        if (ip == null || TextUtils.isEmpty(ip.ip) || !isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Block " + ip.ip + "?")
                .setMessage("Block this IP for 1 hour?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Block", (d, which) -> doBlock(ip.ip, 3600))
                .show();
    }

    @Override
    public void onUnblock(DdosBlockedIp ip) {
        if (ip == null || TextUtils.isEmpty(ip.ip) || !isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Unblock " + ip.ip + "?")
                .setMessage("Remove this IP from the block list?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Unblock", (d, which) -> doUnblock(ip.ip))
                .show();
    }

    @Override
    public void onSelectIp(String ip) {
        if (!isAdded()) return;
        setSelectedIp(ip);
        showIpActions(ip, false);
    }

    @Override
    public void onSelectBlockedIp(String ip) {
        if (!isAdded()) return;
        setSelectedIp(ip);
        showIpActions(ip, true);
    }

    private void setSelectedIp(String ip) {
        selectedIp = ip != null ? ip : "";
        if (topIpsAdapter != null) topIpsAdapter.setSelectedIp(selectedIp);
        if (blockedAdapter != null) blockedAdapter.setSelectedIp(selectedIp);
        if (recentAdapter != null) recentAdapter.setSelectedIp(selectedIp);
        updateManualBlockLabel();
    }

    private void updateManualBlockLabel() {
        if (manualBlockBtn == null) return;
        if (selectedIp == null || selectedIp.trim().isEmpty()) manualBlockBtn.setText("Block IP");
        else manualBlockBtn.setText("Block selected");
    }

    private void showIpActions(String ip, boolean isBlocked) {
        if (!isAdded() || TextUtils.isEmpty(ip)) return;

        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        labels.add("Copy IP");
        if (isBlocked) labels.add("Unblock");
        labels.add("Block 10 minutes");
        labels.add("Block 1 hour");
        labels.add("Block 24 hours");
        labels.add("Custom duration…");
        if (selectedIp != null && !selectedIp.trim().isEmpty()) labels.add("Clear selection");

        String[] items = labels.toArray(new String[0]);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(ip)
                .setItems(items, (d, which) -> {
                    String choice = items[which];
                    if ("Copy IP".equals(choice)) {
                        copyToClipboard(ip);
                        toast("Copied " + ip);
                        return;
                    }
                    if ("Unblock".equals(choice)) {
                        confirmUnblock(ip);
                        return;
                    }
                    if ("Block 10 minutes".equals(choice)) {
                        doBlock(ip, 600);
                        return;
                    }
                    if ("Block 1 hour".equals(choice)) {
                        doBlock(ip, 3600);
                        return;
                    }
                    if ("Block 24 hours".equals(choice)) {
                        doBlock(ip, 86400);
                        return;
                    }
                    if ("Custom duration…".equals(choice)) {
                        showManualBlockDialog(ip);
                        return;
                    }
                    if ("Clear selection".equals(choice)) {
                        setSelectedIp("");
                    }
                })
                .show();
    }

    private void confirmUnblock(String ip) {
        if (!isAdded() || TextUtils.isEmpty(ip)) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Unblock " + ip + "?")
                .setMessage("Remove this IP from the block list?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Unblock", (d, which) -> doUnblock(ip))
                .show();
    }

    private void copyToClipboard(String ip) {
        if (!isAdded() || getContext() == null) return;
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("IP", ip));
    }

    private void doBlock(String ip, int durationSeconds) {
        if (actionCall != null) actionCall.cancel();
        Map<String, Object> body = new HashMap<>();
        body.put("ip", ip);
        body.put("duration", durationSeconds);
        actionCall = api.ddosBlock(body);
        actionCall.enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                if (!isAdded()) return;
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                ApiResponse<Map<String, Object>> b = response.body();
                if (!response.isSuccessful() || b == null || !b.status) {
                    toast("Failed to block IP");
                    return;
                }
                toast("Blocked " + ip);
                countdown = AUTO_REFRESH_SECONDS;
                loadAll();
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                if (!isAdded()) return;
                if (call.isCanceled()) return;
                toast("Network error: " + t.getMessage());
            }
        });
    }

    private void doUnblock(String ip) {
        if (actionCall != null) actionCall.cancel();
        Map<String, Object> body = new HashMap<>();
        body.put("ip", ip);
        actionCall = api.ddosUnblock(body);
        actionCall.enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                if (!isAdded()) return;
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                ApiResponse<Map<String, Object>> b = response.body();
                if (!response.isSuccessful() || b == null || !b.status) {
                    toast("Failed to unblock IP");
                    return;
                }
                toast("Unblocked " + ip);
                countdown = AUTO_REFRESH_SECONDS;
                loadAll();
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                if (!isAdded()) return;
                if (call.isCanceled()) return;
                toast("Network error: " + t.getMessage());
            }
        });
    }

    private void toast(String msg) {
        if (!isAdded()) return;
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void cancelLoads() {
        if (overviewCall != null) { overviewCall.cancel(); overviewCall = null; }
        if (blockedCall != null) { blockedCall.cancel(); blockedCall = null; }
        if (topIpsCall != null) { topIpsCall.cancel(); topIpsCall = null; }
        if (recentCall != null) { recentCall.cancel(); recentCall = null; }
        if (rateCall != null) { rateCall.cancel(); rateCall = null; }
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(autoTick);
        cancelLoads();
        if (actionCall != null) { actionCall.cancel(); actionCall = null; }
        swipe = null;
        autoRefreshSwitch = null;
        lastUpdated = null;
        rateChart = null;
        blockedList = null;
        topIpsList = null;
        recentList = null;
        blockedEmpty = null;
        topEmpty = null;
        recentEmpty = null;
        super.onDestroyView();
    }
}

