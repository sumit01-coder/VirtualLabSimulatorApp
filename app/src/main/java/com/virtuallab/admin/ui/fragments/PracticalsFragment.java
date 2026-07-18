package com.virtuallab.admin.ui.fragments;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.api.ApiClient;
import com.virtuallab.admin.api.ApiService;
import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.model.ApiResponse;
import com.virtuallab.admin.model.Practical;
import com.virtuallab.admin.ui.list.PracticalsAdapter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class PracticalsFragment extends BaseAuthedFragment {
    private ApiService api;
    private SwipeRefreshLayout swipe;
    private PracticalsAdapter adapter;
    private RecyclerView list;
    private TextInputEditText searchInput;
    private MaterialAutoCompleteTextView deptFilterInput;
    private TextView countText;
    private View emptyText;

    private Call<ApiResponse<List<Practical>>> pendingCall;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_practicals, container, false);
        TokenStore store = new TokenStore(requireContext());
        api = ApiClient.get(store);

        swipe = v.findViewById(R.id.swipe);
        list = v.findViewById(R.id.list);
        searchInput = v.findViewById(R.id.searchInput);
        deptFilterInput = v.findViewById(R.id.deptFilterInput);
        countText = v.findViewById(R.id.countText);
        emptyText = v.findViewById(R.id.emptyText);

        adapter = new PracticalsAdapter(this::showPracticalDialog);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                adapter.setQuery(s != null ? s.toString() : "");
                updateCountAndEmpty();
            }
        });

        deptFilterInput.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            adapter.setDepartment(item != null ? String.valueOf(item) : "All Departments");
            updateCountAndEmpty();
        });

        swipe.setOnRefreshListener(this::load);

        List<String> initial = new ArrayList<>();
        initial.add("All Departments");
        deptFilterInput.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, initial));
        deptFilterInput.setText("All Departments", false);

        load();
        return v;
    }

    private void load() {
        if (pendingCall != null) {
            pendingCall.cancel();
            pendingCall = null;
        }
        if (swipe != null) swipe.setRefreshing(true);

        pendingCall = api.practicals();
        pendingCall.enqueue(new Callback<ApiResponse<List<Practical>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Practical>>> call, Response<ApiResponse<List<Practical>>> response) {
                if (!isAdded()) return;
                if (swipe != null) swipe.setRefreshing(false);
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (!response.isSuccessful() || response.body() == null || !response.body().status || response.body().data == null) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Failed to load practicals", Toast.LENGTH_SHORT).show();
                    return;
                }

                adapter.submit(response.body().data);
                if (list != null) list.scheduleLayoutAnimation();
                bindDepartments(response.body().data);
                updateCountAndEmpty();
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Practical>>> call, Throwable t) {
                if (!isAdded()) return;
                if (call.isCanceled()) return;
                if (swipe != null) swipe.setRefreshing(false);
                Context ctx = getContext();
                if (ctx != null) Toast.makeText(ctx, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindDepartments(List<Practical> practicals) {
        Set<String> set = new LinkedHashSet<>();
        set.add("All Departments");
        if (practicals != null) {
            for (Practical p : practicals) {
                if (p != null && p.dept_name != null && !p.dept_name.trim().isEmpty()) set.add(p.dept_name.trim());
            }
        }
        List<String> items = new ArrayList<>(set);
        deptFilterInput.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, items));
        if (deptFilterInput.getText() == null || deptFilterInput.getText().toString().trim().isEmpty()) {
            deptFilterInput.setText("All Departments", false);
        }
    }

    private void updateCountAndEmpty() {
        if (countText != null) {
            int total = adapter != null ? adapter.getTotalCount() : 0;
            int shown = adapter != null ? adapter.getVisibleCount() : 0;
            if (total == 0) countText.setText("");
            else if (shown == total) countText.setText(total + " practicals");
            else countText.setText(shown + " / " + total);
        }
        if (emptyText != null) {
            boolean show = adapter != null && adapter.getVisibleCount() == 0;
            emptyText.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showPracticalDialog(Practical p) {
        if (p == null || getContext() == null) return;
        String title = p.title != null && !p.title.trim().isEmpty() ? p.title : "Practical";

        View content = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_practical_preview, null, false);
        TextView meta = content.findViewById(R.id.meta);
        TextView heroTitle = content.findViewById(R.id.heroTitle);
        TextView overview = content.findViewById(R.id.overview);
        LinearLayout objectivesContainer = content.findViewById(R.id.objectivesContainer);
        LinearLayout materialsContainer = content.findViewById(R.id.materialsContainer);
        TextView procedure = content.findViewById(R.id.procedure);
        TextView code = content.findViewById(R.id.code);
        TextView output = content.findViewById(R.id.output);
        TextView links = content.findViewById(R.id.links);
        ImageView figurePreview = content.findViewById(R.id.figurePreview);
        ImageView heroImage = content.findViewById(R.id.heroImage);
        MaterialButton previewSimulatorBtn = content.findViewById(R.id.previewSimulatorBtn);
        MaterialButton startSimulationBtn = content.findViewById(R.id.startSimulationBtn);

        heroTitle.setText(title);

        String metaText = "ID: " + p.id;
        if (p.lab_name != null && !p.lab_name.trim().isEmpty()) metaText += " * Lab: " + p.lab_name.trim();
        if (p.dept_name != null && !p.dept_name.trim().isEmpty()) metaText += " * Dept: " + p.dept_name.trim();
        meta.setText(metaText);

        setRichText(overview, p.overview, "Overview will be available soon.");
        populatePointList(objectivesContainer, p.objective, "No objectives available.");
        populatePointList(materialsContainer, p.materials_required, "No materials listed.");
        bindSection(procedure, "Procedure", p.procedure);
        bindSection(code, "Program Code", p.program_code);
        bindSection(output, "Expected Output", p.program_output);

        StringBuilder linkText = new StringBuilder();
        String simUrl = null;
        if (p.simulator_url != null && !p.simulator_url.trim().isEmpty()) simUrl = p.simulator_url.trim();
        else if (p.simulator_link != null && !p.simulator_link.trim().isEmpty()) simUrl = p.simulator_link.trim();

        if (simUrl != null) {
            linkText.append("Simulator: ").append(simUrl).append('\n');
        }
        if (p.figure_urls != null && !p.figure_urls.isEmpty()) {
            linkText.append("Figures:").append('\n');
            for (String u : p.figure_urls) {
                if (u == null) continue;
                String url = u.trim();
                if (url.isEmpty()) continue;
                linkText.append(url).append('\n');
            }
        } else if (p.figure_path != null && !p.figure_path.trim().isEmpty()) {
            linkText.append("Figures: ").append(p.figure_path.trim()).append('\n');
        }
        if (p.code_description != null && !p.code_description.trim().isEmpty()) {
            linkText.append('\n').append("Description:").append('\n').append(p.code_description.trim());
        }
        if (linkText.length() == 0) {
            links.setVisibility(View.GONE);
        } else {
            links.setText(sanitizeText(linkText.toString().trim()));
            links.setVisibility(View.VISIBLE);
        }

        String firstFigureUrl = null;
        if (p.figure_urls != null) {
            for (String u : p.figure_urls) {
                if (u == null) continue;
                String url = u.trim();
                if (!url.isEmpty()) { firstFigureUrl = url; break; }
            }
        }
        if (firstFigureUrl != null) {
            String fullUrl = firstFigureUrl;
            Glide.with(figurePreview).load(fullUrl).centerCrop().into(figurePreview);
            Glide.with(heroImage).load(fullUrl).centerCrop().into(heroImage);
            figurePreview.setOnClickListener(v -> showImagePreviewDialog(fullUrl));
        } else {
            figurePreview.setImageResource(R.drawable.ic_shield_lab_72);
            heroImage.setImageResource(R.drawable.ic_shield_lab_72);
        }

        if (simUrl != null && !simUrl.trim().isEmpty()) {
            String simulatorUrl = simUrl.trim();
            previewSimulatorBtn.setVisibility(View.VISIBLE);
            previewSimulatorBtn.setOnClickListener(v -> showWebPreviewDialog(title, simulatorUrl));
            startSimulationBtn.setOnClickListener(v -> showWebPreviewDialog(title, simulatorUrl));
        } else {
            previewSimulatorBtn.setVisibility(View.GONE);
            startSimulationBtn.setEnabled(false);
            startSimulationBtn.setText("Simulation Link Unavailable");
        }

        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        sheet.setContentView(content);
        sheet.setOnShowListener(dialog -> {
            FrameLayout bottomSheet = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet == null) return;
            ViewGroup.LayoutParams lp = bottomSheet.getLayoutParams();
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            bottomSheet.setLayoutParams(lp);

            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

            content.setAlpha(0f);
            content.setTranslationY(24f);
            content.animate().alpha(1f).translationY(0f).setDuration(220).start();
        });
        sheet.show();
    }

    private static void bindSection(TextView tv, String label, String value) {
        if (tv == null) return;
        if (value == null || value.trim().isEmpty()) {
            tv.setVisibility(View.GONE);
            return;
        }
        tv.setText(label + ":\n" + sanitizeText(value));
        tv.setVisibility(View.VISIBLE);
    }

    private static String sanitizeText(String text) {
        if (text == null) return "";
        String t = text.trim();
        if (t.isEmpty()) return "";
        Spanned spanned = Html.fromHtml(t, Html.FROM_HTML_MODE_LEGACY);
        return spanned.toString().replace('\u00A0', ' ').trim();
    }

    private void setRichText(TextView tv, String value, String fallback) {
        if (tv == null) return;
        String clean = sanitizeText(value);
        tv.setText(clean.isEmpty() ? fallback : clean);
    }

    private void populatePointList(LinearLayout container, String raw, String fallback) {
        if (container == null) return;
        container.removeAllViews();
        List<String> points = splitPoints(raw);
        if (points.isEmpty()) points.add(fallback);

        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        for (String point : points) {
            View row = inflater.inflate(R.layout.item_preview_point, container, false);
            TextView text = row.findViewById(R.id.text);
            text.setText(point);
            container.addView(row);
        }
    }

    private static List<String> splitPoints(String raw) {
        List<String> out = new ArrayList<>();
        String clean = sanitizeText(raw);
        if (clean.isEmpty()) return out;

        String[] lines = clean.split("\\r?\\n");
        for (String line : lines) {
            String item = line.trim();
            if (item.isEmpty()) continue;
            item = item.replaceFirst("^[-*\\d.)\\s]+", "").trim();
            if (!item.isEmpty()) out.add(item);
        }

        if (out.isEmpty()) {
            String[] parts = clean.split("\\s*;\\s*");
            for (String part : parts) {
                String item = part.trim();
                if (!item.isEmpty()) out.add(item);
            }
        }
        return out;
    }

    private void showWebPreviewDialog(String title, String url) {
        if (!isAdded() || getContext() == null) return;
        if (url == null || url.trim().isEmpty()) return;

        final String startUrl = url.trim();
        final Uri startUri = Uri.parse(startUrl);
        final String allowedHost = startUri.getHost();
        if (allowedHost == null || allowedHost.trim().isEmpty()) return;

        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_simulator_preview, null, false);
        TextView previewTitle = content.findViewById(R.id.previewTitle);
        ProgressBar loading = content.findViewById(R.id.previewLoading);
        MaterialButton openExternalBtn = content.findViewById(R.id.openExternalBtn);
        MaterialButton closeBtn = content.findViewById(R.id.closePreviewBtn);
        WebView web = content.findViewById(R.id.simulatorWebView);

        previewTitle.setText("Preview: " + title);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(true);
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                loading.setVisibility(View.GONE);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return true;
                Uri u = request.getUrl();
                String host = u.getHost();
                if (host == null) return true;
                return !allowedHost.equalsIgnoreCase(host);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url == null) return true;
                Uri u = Uri.parse(url);
                String host = u.getHost();
                if (host == null) return true;
                return !allowedHost.equalsIgnoreCase(host);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 100) loading.setVisibility(View.GONE);
                else loading.setVisibility(View.VISIBLE);
            }
        });

        openExternalBtn.setOnClickListener(v -> {
            try {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(startUrl)));
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Unable to open browser", Toast.LENGTH_SHORT).show();
            }
        });
        closeBtn.setOnClickListener(v -> sheet.dismiss());

        sheet.setContentView(content);
        sheet.setOnShowListener(dialog -> {
            FrameLayout bottomSheet = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet == null) return;
            ViewGroup.LayoutParams lp = bottomSheet.getLayoutParams();
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            bottomSheet.setLayoutParams(lp);
            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        web.loadUrl(startUrl);
        sheet.show();
        sheet.setOnDismissListener(d -> {
            try { web.loadUrl("about:blank"); } catch (Exception ignored) {}
            try { web.stopLoading(); } catch (Exception ignored) {}
            try { web.destroy(); } catch (Exception ignored) {}
        });
    }

    private void showImagePreviewDialog(String url) {
        if (!isAdded() || getContext() == null) return;
        if (url == null || url.trim().isEmpty()) return;

        ImageView img = new ImageView(requireContext());
        img.setAdjustViewBounds(true);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = (int) (16 * requireContext().getResources().getDisplayMetrics().density);
        img.setPadding(pad, pad, pad, pad);

        Glide.with(img).load(url.trim()).fitCenter().into(img);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Image Preview")
                .setView(img)
                .setPositiveButton("Close", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        if (pendingCall != null) {
            pendingCall.cancel();
            pendingCall = null;
        }
        swipe = null;
        list = null;
        searchInput = null;
        deptFilterInput = null;
        countText = null;
        emptyText = null;
        super.onDestroyView();
    }
}

