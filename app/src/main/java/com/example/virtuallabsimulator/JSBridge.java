package com.example.virtuallabsimulator;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

public class JSBridge {
    private final Context ctx;

    public JSBridge(Context ctx) {
        this.ctx = ctx;
    }

    @JavascriptInterface
    public void toast(String message) {
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show();
    }
}