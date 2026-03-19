package com.virtuallab.admin.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.virtuallab.admin.R;
import com.virtuallab.admin.api.ApiClient;
import com.virtuallab.admin.api.ApiService;
import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.model.ApiResponse;
import com.virtuallab.admin.model.Ticket;
import com.virtuallab.admin.model.TicketActionRequest;
import com.virtuallab.admin.ui.list.TicketsAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class TicketsFragment extends BaseAuthedFragment implements TicketsAdapter.Listener {
    private ApiService api;
    private TokenStore store;
    private SwipeRefreshLayout swipe;
    private TicketsAdapter adapter;
    private final List<Ticket> all = new ArrayList<>();

    private TextInputEditText searchInput;
    private MaterialAutoCompleteTextView statusFilterInput;
    private TextView countText;
    private TextView emptyText;

    private Call<ApiResponse<List<Ticket>>> pendingLoad;
    private Call<ApiResponse<Object>> pendingAction;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_tickets, container, false);
        store = new TokenStore(requireContext());
        api = ApiClient.get(store);

        swipe = v.findViewById(R.id.swipe);
        RecyclerView list = v.findViewById(R.id.list);
        searchInput = v.findViewById(R.id.searchInput);
        statusFilterInput = v.findViewById(R.id.statusFilterInput);
        countText = v.findViewById(R.id.countText);
        emptyText = v.findViewById(R.id.emptyText);

        adapter = new TicketsAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);

        ArrayAdapter<CharSequence> statusAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.ticket_status_filters,
                android.R.layout.simple_list_item_1
        );
        statusFilterInput.setAdapter(statusAdapter);
        statusFilterInput.setText("All", false);
        statusFilterInput.setOnItemClickListener((parent, view, position, id) -> load());

        swipe.setOnRefreshListener(this::load);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        load();
        return v;
    }

    private void load() {
        if (pendingLoad != null) {
            pendingLoad.cancel();
            pendingLoad = null;
        }
        if (swipe != null) swipe.setRefreshing(true);

        String selected = statusFilterInput.getText() != null ? statusFilterInput.getText().toString() : "All";
        String status = selected.trim().toLowerCase(Locale.US);
        if (status.equals("all")) status = "all";

        pendingLoad = api.tickets(status);
        pendingLoad.enqueue(new Callback<ApiResponse<List<Ticket>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Ticket>>> call, Response<ApiResponse<List<Ticket>>> response) {
                if (!isAdded()) return;
                if (swipe != null) swipe.setRefreshing(false);
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (!response.isSuccessful() || response.body() == null || !response.body().status || response.body().data == null) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Failed to load tickets", Toast.LENGTH_SHORT).show();
                    return;
                }
                all.clear();
                all.addAll(response.body().data);
                applyFilter();
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Ticket>>> call, Throwable t) {
                if (!isAdded()) return;
                if (call.isCanceled()) return;
                if (swipe != null) swipe.setRefreshing(false);
                Context ctx = getContext();
                if (ctx != null) Toast.makeText(ctx, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyFilter() {
        String q = searchInput.getText() != null ? searchInput.getText().toString().trim().toLowerCase(Locale.US) : "";
        List<Ticket> filtered = new ArrayList<>();
        for (Ticket t : all) {
            if (t == null) continue;
            if (q.isEmpty()) {
                filtered.add(t);
                continue;
            }
            String subject = t.subject != null ? t.subject.toLowerCase(Locale.US) : "";
            String sender = t.sender_name != null ? t.sender_name.toLowerCase(Locale.US) : "";
            String email = t.sender_email != null ? t.sender_email.toLowerCase(Locale.US) : "";
            if (subject.contains(q) || sender.contains(q) || email.contains(q)) filtered.add(t);
        }
        adapter.submit(filtered);
        updateCountAndEmpty(filtered.size(), all.size());
    }

    private void updateCountAndEmpty(int shown, int total) {
        if (countText != null) {
            if (total == 0) countText.setText("");
            else if (shown == total) countText.setText(total + " tickets");
            else countText.setText(shown + " / " + total);
        }
        if (emptyText != null) {
            emptyText.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onOpen(Ticket ticket) {
        if (ticket == null || getContext() == null) return;
        String subject = ticket.subject != null && !ticket.subject.trim().isEmpty() ? ticket.subject : "Ticket";

        StringBuilder msg = new StringBuilder();
        msg.append("ID: ").append(ticket.id).append('\n');
        if (ticket.status != null) msg.append("Status: ").append(ticket.status).append('\n');
        if (ticket.created_at != null) msg.append("Created: ").append(ticket.created_at).append('\n');
        if (ticket.sender_name != null) msg.append("Sender: ").append(ticket.sender_name).append('\n');
        if (ticket.sender_email != null) msg.append("Email: ").append(ticket.sender_email).append('\n');

        boolean canClose = ticket.status != null && !ticket.status.equalsIgnoreCase("closed");
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(subject)
                .setMessage(msg.toString().trim())
                .setNegativeButton("Close", null);

        if (canClose) {
            b.setPositiveButton("Close Ticket", (d, which) -> onResolve(ticket));
        } else {
            b.setPositiveButton("OK", null);
        }
        b.show();
    }

    @Override
    public void onResolve(Ticket ticket) {
        if (ticket == null) return;
        if (ticket.status != null && ticket.status.equalsIgnoreCase("closed")) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Close ticket?")
                .setMessage("This will mark the ticket as closed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Close", (d, which) -> doClose(ticket))
                .show();
    }

    private void doClose(Ticket ticket) {
        if (pendingAction != null) pendingAction.cancel();

        pendingAction = api.ticketAction(new TicketActionRequest("close", ticket.id));
        pendingAction.enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                if (!isAdded()) return;
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (!response.isSuccessful() || response.body() == null || !response.body().status) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Failed to close ticket", Toast.LENGTH_SHORT).show();
                    return;
                }
                Context ctx = getContext();
                if (ctx != null) Toast.makeText(ctx, "Ticket closed", Toast.LENGTH_SHORT).show();
                load();
            }

            @Override
            public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                if (!isAdded()) return;
                if (call.isCanceled()) return;
                Context ctx = getContext();
                if (ctx != null) Toast.makeText(ctx, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        if (pendingLoad != null) { pendingLoad.cancel(); pendingLoad = null; }
        if (pendingAction != null) { pendingAction.cancel(); pendingAction = null; }
        swipe = null;
        searchInput = null;
        statusFilterInput = null;
        countText = null;
        emptyText = null;
        super.onDestroyView();
    }
}

