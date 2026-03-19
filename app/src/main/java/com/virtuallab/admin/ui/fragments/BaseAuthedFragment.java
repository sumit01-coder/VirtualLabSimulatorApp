package com.virtuallab.admin.ui.fragments;

import android.content.Intent;

import androidx.fragment.app.Fragment;

import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.ui.LoginActivity;

public abstract class BaseAuthedFragment extends Fragment {
    protected final void handleUnauthorized() {
        if (getContext() == null) return;
        TokenStore store = new TokenStore(getContext());
        store.clear();
        startActivity(new Intent(getContext(), LoginActivity.class));
        if (getActivity() != null) {
            getActivity().finish();
        }
    }
}

