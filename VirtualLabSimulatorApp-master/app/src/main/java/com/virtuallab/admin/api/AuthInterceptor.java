package com.virtuallab.admin.api;


import com.virtuallab.admin.data.TokenStore;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public final class AuthInterceptor implements Interceptor {
    private final TokenStore tokenStore;

    public AuthInterceptor(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    
    @Override
    public Response intercept( Chain chain) throws IOException {
        Request original = chain.request();
        String token = tokenStore.getToken();
        if (token == null || token.isEmpty()) {
            return chain.proceed(original);
        }

        Request authed = original.newBuilder()
                .header("Authorization", "Bearer " + token)
                .build();
        return chain.proceed(authed);
    }
}

