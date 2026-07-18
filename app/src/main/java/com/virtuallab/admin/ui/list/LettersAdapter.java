package com.virtuallab.admin.ui.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.model.Letter;

import java.util.ArrayList;
import java.util.List;

public class LettersAdapter extends RecyclerView.Adapter<LettersAdapter.LetterViewHolder> {

    private List<Letter> letters = new ArrayList<>();
    private final Listener listener;

    public interface Listener {
        void onDeleteLetter(Letter letter);
        void onViewLetter(Letter letter);
    }

    public LettersAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setLetters(List<Letter> letters) {
        this.letters = letters;
        notifyDataSetChanged();
    }

    public void removeLetter(String letterId) {
        for (int i = 0; i < letters.size(); i++) {
            if (letters.get(i).letterId.equals(letterId)) {
                letters.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public LetterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_letter, parent, false);
        return new LetterViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull LetterViewHolder holder, int position) {
        Letter l = letters.get(position);
        holder.bind(l, listener);
    }

    @Override
    public int getItemCount() {
        return letters.size();
    }

    static class LetterViewHolder extends RecyclerView.ViewHolder {
        TextView tvLetterId, tvDate, tvSubject, tvRecipientName, tvRecipientEmail;
        MaterialButton btnDelete, btnView;

        public LetterViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLetterId = itemView.findViewById(R.id.tvLetterId);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvSubject = itemView.findViewById(R.id.tvSubject);
            tvRecipientName = itemView.findViewById(R.id.tvRecipientName);
            tvRecipientEmail = itemView.findViewById(R.id.tvRecipientEmail);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnView = itemView.findViewById(R.id.btnView);
        }

        public void bind(Letter l, Listener listener) {
            tvLetterId.setText(l.letterId);
            tvDate.setText(l.letterDate);
            tvSubject.setText(l.subject);
            tvRecipientName.setText(l.recipientName);
            
            if (l.recipientEmail != null && !l.recipientEmail.isEmpty()) {
                tvRecipientEmail.setText(l.recipientEmail);
                tvRecipientEmail.setVisibility(View.VISIBLE);
            } else {
                tvRecipientEmail.setVisibility(View.GONE);
            }

            btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteLetter(l);
            });
            btnView.setOnClickListener(v -> {
                if (listener != null) listener.onViewLetter(l);
            });
        }
    }
}
