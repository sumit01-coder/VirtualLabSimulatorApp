package com.virtuallab.admin.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.api.ApiClient;
import com.virtuallab.admin.model.ApiResponse;
import com.virtuallab.admin.model.Letter;
import com.virtuallab.admin.ui.list.LettersAdapter;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LettersActivity extends AppCompatActivity implements LettersAdapter.Listener {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvLetters;
    private View emptyState;
    private LettersAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_letters);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        swipeRefresh = findViewById(R.id.swipeRefresh);
        rvLetters = findViewById(R.id.rvLetters);
        emptyState = findViewById(R.id.emptyState);

        adapter = new LettersAdapter(this);
        rvLetters.setLayoutManager(new LinearLayoutManager(this));
        rvLetters.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadLetters);
        
        swipeRefresh.setRefreshing(true);
        loadLetters();
    }

    private void loadLetters() {
        ApiClient.get(new com.virtuallab.admin.data.TokenStore(this)).getLetters("list").enqueue(new Callback<ApiResponse<List<Letter>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Letter>>> call, Response<ApiResponse<List<Letter>>> response) {
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().status) {
                        List<Letter> letters = response.body().data;
                        adapter.setLetters(letters);
                        if (letters == null || letters.isEmpty()) {
                            emptyState.setVisibility(View.VISIBLE);
                            rvLetters.setVisibility(View.GONE);
                        } else {
                            emptyState.setVisibility(View.GONE);
                            rvLetters.setVisibility(View.VISIBLE);
                        }
                    } else {
                        Toast.makeText(LettersActivity.this, response.body().message, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(LettersActivity.this, "Failed to load letters", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Letter>>> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(LettersActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDeleteLetter(Letter letter) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Letter")
                .setMessage("Are you sure you want to delete this letter? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    ApiClient.get(new com.virtuallab.admin.data.TokenStore(this)).deleteLetter("delete", letter.letterId).enqueue(new Callback<ApiResponse<Object>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                if (response.body().status) {
                                    Toast.makeText(LettersActivity.this, "Letter deleted", Toast.LENGTH_SHORT).show();
                                    adapter.removeLetter(letter.letterId);
                                    if (adapter.getItemCount() == 0) {
                                        emptyState.setVisibility(View.VISIBLE);
                                        rvLetters.setVisibility(View.GONE);
                                    }
                                } else {
                                    Toast.makeText(LettersActivity.this, response.body().message, Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(LettersActivity.this, "Failed to delete letter", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                            Toast.makeText(LettersActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onViewLetter(Letter letter) {
        if (letter.letterId == null) return;
        String[] parts = letter.letterId.split("-");
        if (parts.length >= 3) {
            String verifyId = parts[2];
            String url = "https://virtuallabsimulator.com/verify_letter.php/" + verifyId;
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
            startActivity(intent);
        } else {
            Toast.makeText(this, "Invalid Letter ID", Toast.LENGTH_SHORT).show();
        }
    }
}
