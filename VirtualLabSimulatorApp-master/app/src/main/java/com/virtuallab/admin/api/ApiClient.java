package com.virtuallab.admin.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.virtuallab.admin.Config;
import com.virtuallab.admin.data.TokenStore;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    private static Retrofit retrofit;

    private ApiClient() {}

    public static synchronized ApiService get(TokenStore tokenStore) {
        if (retrofit == null) {
            Gson gson = new GsonBuilder().create();

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new AuthInterceptor(tokenStore))
                    .addInterceptor(logging)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(Config.API_BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();
        }

        return retrofit.create(ApiService.class);
    }
}

