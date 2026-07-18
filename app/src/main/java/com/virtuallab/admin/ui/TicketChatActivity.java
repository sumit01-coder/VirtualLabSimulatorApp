package com.virtuallab.admin.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.api.ApiClient;
import com.virtuallab.admin.api.ApiService;
import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.feature.AuditLog;
import com.virtuallab.admin.model.ApiResponse;
import com.virtuallab.admin.model.TicketActionRequest;
import com.virtuallab.admin.model.TicketMessage;
import com.virtuallab.admin.ui.list.TicketChatAdapter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TicketChatActivity extends AppCompatActivity {
    public static final String EXTRA_TICKET_ID = "ticket_id";
    public static final String EXTRA_TICKET_SUBJECT = "ticket_subject";

    private ApiService api;
    private TokenStore store;
    private TicketChatAdapter adapter;
    private RecyclerView chatList;
    private TextInputEditText messageInput;
    private MaterialButton sendBtn;
    
    private int ticketId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ticket_chat);
        
        store = new TokenStore(this);
        api = ApiClient.get(store);

        ticketId = getIntent().getIntExtra(EXTRA_TICKET_ID, -1);
        String subject = getIntent().getStringExtra(EXTRA_TICKET_SUBJECT);
        
        if (ticketId == -1) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(subject != null ? subject : "Ticket #" + ticketId);
        toolbar.setNavigationOnClickListener(v -> finish());

        chatList = findViewById(R.id.chatList);
        messageInput = findViewById(R.id.messageInput);
        sendBtn = findViewById(R.id.sendBtn);

        adapter = new TicketChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatList.setLayoutManager(layoutManager);
        chatList.setAdapter(adapter);

        sendBtn.setOnClickListener(v -> sendReply());

        loadMessages();
    }

    private void loadMessages() {
        api.ticketMessages("messages", ticketId).enqueue(new Callback<ApiResponse<List<TicketMessage>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<TicketMessage>>> call, Response<ApiResponse<List<TicketMessage>>> response) {
                if (isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null && response.body().status) {
                    adapter.submit(response.body().data);
                    if (adapter.getItemCount() > 0) {
                        chatList.scrollToPosition(adapter.getItemCount() - 1);
                    }
                } else {
                    Toast.makeText(TicketChatActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<TicketMessage>>> call, Throwable t) {
                if (isDestroyed()) return;
                Toast.makeText(TicketChatActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendReply() {
        String msg = messageInput.getText() != null ? messageInput.getText().toString().trim() : "";
        if (msg.isEmpty()) return;

        sendBtn.setEnabled(false);

        api.ticketAction(new TicketActionRequest("reply", ticketId, msg)).enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                if (isDestroyed()) return;
                sendBtn.setEnabled(true);
                if (response.isSuccessful() && response.body() != null && response.body().status) {
                    messageInput.setText("");
                    AuditLog.write(TicketChatActivity.this, store.getUsername(), "ticket.reply", "ticket_id=" + ticketId);
                    
                    TicketMessage myMsg = new TicketMessage();
                    myMsg.ticket_id = ticketId;
                    myMsg.author_type = "admin";
                    myMsg.author_name = "You";
                    myMsg.message = msg;
                    myMsg.created_at = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
                    adapter.addMessage(myMsg);
                    chatList.scrollToPosition(adapter.getItemCount() - 1);
                } else {
                    Toast.makeText(TicketChatActivity.this, "Failed to send message", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                if (isDestroyed()) return;
                sendBtn.setEnabled(true);
                Toast.makeText(TicketChatActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
