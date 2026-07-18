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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.api.ApiClient;
import com.virtuallab.admin.api.ApiService;
import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.feature.AuditLog;
import com.virtuallab.admin.feature.OfflineCache;
import com.virtuallab.admin.feature.PermissionMatrix;
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
    private static final String CACHE_KEY = "tickets.cache";

    private ApiService api;
    private TokenStore store;
    private SwipeRefreshLayout swipe;
    private TicketsAdapter adapter;
    private final List<Ticket> all = new ArrayList<>();

    private TextInputEditText searchInput;
    private MaterialAutoCompleteTextView statusFilterInput;
    private TextView countText;
    private TextView emptyText;
    private TextView selectionText;
    private View bulkActionsRow;
    private MaterialButton bulkCloseBtn;
    private MaterialButton bulkAssignBtn;
    private RecyclerView list;

    private Call<ApiResponse<List<Ticket>>> pendingLoad;
    private Call<ApiResponse<Object>> pendingAction;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_tickets, container, false);
        store = new TokenStore(requireContext());
        api = ApiClient.get(store);

        swipe = v.findViewById(R.id.swipe);
        list = v.findViewById(R.id.list);
        searchInput = v.findViewById(R.id.searchInput);
        statusFilterInput = v.findViewById(R.id.statusFilterInput);
        countText = v.findViewById(R.id.countText);
        emptyText = v.findViewById(R.id.emptyText);
        selectionText = v.findViewById(R.id.selectionText);
        bulkActionsRow = v.findViewById(R.id.bulkActionsRow);
        bulkCloseBtn = v.findViewById(R.id.bulkCloseBtn);
        bulkAssignBtn = v.findViewById(R.id.bulkAssignBtn);

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
        bulkCloseBtn.setOnClickListener(v1 -> bulkCloseSelected());
        bulkAssignBtn.setOnClickListener(v12 -> openAssignDialog());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        if (!PermissionMatrix.has(store.getRole(), PermissionMatrix.Capability.CLOSE_TICKETS)) {
            bulkCloseBtn.setEnabled(false);
            bulkAssignBtn.setEnabled(false);
        }

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
                    List<Ticket> cached = OfflineCache.getList(requireContext(), CACHE_KEY, Ticket.class);
                    if (!cached.isEmpty()) {
                        all.clear();
                        all.addAll(cached);
                        applyFilter();
                        toast("Loaded cached tickets");
                    } else {
                        toast("Failed to load tickets");
                    }
                    return;
                }
                all.clear();
                all.addAll(response.body().data);
                OfflineCache.putList(requireContext(), CACHE_KEY, all);
                applyFilter();
                if (list != null) list.scheduleLayoutAnimation();
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Ticket>>> call, Throwable t) {
                if (!isAdded()) return;
                if (call.isCanceled()) return;
                if (swipe != null) swipe.setRefreshing(false);
                List<Ticket> cached = OfflineCache.getList(requireContext(), CACHE_KEY, Ticket.class);
                if (!cached.isEmpty()) {
                    all.clear();
                    all.addAll(cached);
                    applyFilter();
                    toast("Offline mode: cached tickets");
                } else {
                    toast("Network error: " + t.getMessage());
                }
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
        onSelectionChanged(0);
    }

    private void updateCountAndEmpty(int shown, int total) {
        if (countText != null) {
            if (total == 0) countText.setText("");
            else if (shown == total) countText.setText(total + " tickets");
            else countText.setText(shown + " / " + total);
        }
        if (emptyText != null) emptyText.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
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

        boolean canClose = ticket.status != null && !ticket.status.equalsIgnoreCase("closed")
                && PermissionMatrix.has(store.getRole(), PermissionMatrix.Capability.CLOSE_TICKETS);

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(subject)
                .setMessage(msg.toString().trim())
                .setNegativeButton("Close", null);

        if (canClose) {
            b.setPositiveButton("Actions...", (d, which) -> showTicketActions(ticket, subject));
        } else {
            b.setPositiveButton("OK", null);
        }
        b.show();
    }

    @Override
    public void onReply(Ticket ticket) {
        if (ticket == null || getContext() == null) return;
        android.content.Intent intent = new android.content.Intent(getContext(), com.virtuallab.admin.ui.TicketChatActivity.class);
        intent.putExtra(com.virtuallab.admin.ui.TicketChatActivity.EXTRA_TICKET_ID, ticket.id);
        intent.putExtra(com.virtuallab.admin.ui.TicketChatActivity.EXTRA_TICKET_SUBJECT, ticket.subject);
        startActivity(intent);
    }

    private void showTicketActions(Ticket ticket, String subject) {
        CharSequence[] options = {"Reply to User", "Assign / Internal Note", "Mark as Closed"};
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(subject + " Actions")
            .setItems(options, (d, which) -> {
                if (which == 0) openReplyDialog(ticket);
                else if (which == 1) openAssignDialog(ticket);
                else if (which == 2) onResolve(ticket);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void openReplyDialog(Ticket ticket) {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ticket_reply, null, false);
        TextInputEditText replyInput = content.findViewById(R.id.replyMessageInput);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reply to Ticket #" + ticket.id)
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", (d, which) -> {
                    String message = replyInput.getText() != null ? replyInput.getText().toString().trim() : "";
                    if (message.isEmpty()) {
                        toast("Message cannot be empty");
                        return;
                    }
                    sendReply(ticket, message);
                })
                .show();
    }

    private void sendReply(Ticket ticket, String message) {
        if (pendingAction != null) pendingAction.cancel();
        
        pendingAction = api.ticketAction(new TicketActionRequest("reply", ticket.id, message));
        pendingAction.enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                if (!isAdded()) return;
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (!response.isSuccessful() || response.body() == null || !response.body().status) {
                    toast("Failed to send reply");
                    return;
                }
                toast("Reply sent successfully");
                AuditLog.write(requireContext(), store.getUsername(), "ticket.reply", "ticket_id=" + ticket.id);
                load();
            }

            @Override
            public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                if (!isAdded()) return;
                if (call.isCanceled()) return;
                toast("Network error: " + t.getMessage());
            }
        });
    }

    @Override
    public void onResolve(Ticket ticket) {
        if (ticket == null) return;
        if (ticket.status != null && ticket.status.equalsIgnoreCase("closed")) return;
        if (!PermissionMatrix.has(store.getRole(), PermissionMatrix.Capability.CLOSE_TICKETS)) {
            toast("Permission denied");
            return;
        }

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
                    toast("Failed to close ticket");
                    return;
                }
                toast("Ticket closed");
                AuditLog.write(requireContext(), store.getUsername(), "ticket.close", "ticket_id=" + ticket.id);
                load();
            }

            @Override
            public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                if (!isAdded()) return;
                if (call.isCanceled()) return;
                toast("Network error: " + t.getMessage());
            }
        });
    }

    private void bulkCloseSelected() {
        List<Ticket> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            toast("Select tickets first");
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Close selected tickets?")
                .setMessage("Selected: " + selected.size())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Close", (d, which) -> closeSequential(selected, 0, 0))
                .show();
    }

    private void closeSequential(List<Ticket> selected, int index, int success) {
        if (!isAdded()) return;
        if (index >= selected.size()) {
            toast("Closed " + success + " / " + selected.size());
            AuditLog.write(requireContext(), store.getUsername(), "ticket.bulk_close", "count=" + success);
            adapter.clearSelection();
            load();
            return;
        }

        Ticket t = selected.get(index);
        api.ticketAction(new TicketActionRequest("close", t.id)).enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                int nextSuccess = success;
                if (response.isSuccessful() && response.body() != null && response.body().status) nextSuccess++;
                closeSequential(selected, index + 1, nextSuccess);
            }

            @Override
            public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                closeSequential(selected, index + 1, success);
            }
        });
    }

    private void openAssignDialog() {
        List<Ticket> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            toast("Select tickets first");
            return;
        }
        openAssignDialogFor(selected);
    }

    private void openAssignDialog(Ticket single) {
        List<Ticket> one = new ArrayList<>();
        one.add(single);
        openAssignDialogFor(one);
    }

    private void openAssignDialogFor(List<Ticket> tickets) {
        if (!PermissionMatrix.has(store.getRole(), PermissionMatrix.Capability.ASSIGN_TICKETS)
                && !PermissionMatrix.has(store.getRole(), PermissionMatrix.Capability.CLOSE_TICKETS)) {
            toast("Permission denied");
            return;
        }

        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ticket_assign, null, false);
        TextInputEditText assigneeInput = content.findViewById(R.id.assigneeInput);
        TextInputEditText noteInput = content.findViewById(R.id.noteInput);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Assign / Note (" + tickets.size() + ")")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, which) -> {
                    String assignee = assigneeInput.getText() != null ? assigneeInput.getText().toString().trim() : "";
                    String note = noteInput.getText() != null ? noteInput.getText().toString().trim() : "";
                    assignSequential(tickets, assignee, note, 0, 0);
                })
                .show();
    }

    private void assignSequential(List<Ticket> tickets, String assignee, String note, int index, int success) {
        if (!isAdded()) return;
        if (index >= tickets.size()) {
            toast("Updated " + success + " / " + tickets.size());
            AuditLog.write(requireContext(), store.getUsername(), "ticket.assign_note", "count=" + success + ", assignee=" + assignee);
            adapter.clearSelection();
            load();
            return;
        }

        Ticket t = tickets.get(index);
        api.ticketAction(new TicketActionRequest("assign", t.id, assignee, note)).enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                int nextSuccess = success;
                if (response.isSuccessful() && response.body() != null && response.body().status) nextSuccess++;
                assignSequential(tickets, assignee, note, index + 1, nextSuccess);
            }

            @Override
            public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                assignSequential(tickets, assignee, note, index + 1, success);
            }
        });
    }

    @Override
    public void onSelectionChanged(int count) {
        if (selectionText == null || bulkActionsRow == null) return;
        if (count <= 0) {
            selectionText.setVisibility(View.GONE);
            bulkActionsRow.setVisibility(View.GONE);
        } else {
            selectionText.setText(count + " selected");
            selectionText.setVisibility(View.VISIBLE);
            bulkActionsRow.setVisibility(View.VISIBLE);
        }
    }

    private void toast(String msg) {
        if (!isAdded()) return;
        Context ctx = getContext();
        if (ctx != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
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
        selectionText = null;
        bulkActionsRow = null;
        bulkCloseBtn = null;
        bulkAssignBtn = null;
        list = null;
        super.onDestroyView();
    }
}