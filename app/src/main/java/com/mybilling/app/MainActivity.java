package com.mybilling.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView web;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);        // app logic
        s.setDomStorageEnabled(true);        // localStorage -> saves invoices on device
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Bridge so the web app can trigger Android's native Print / Save-as-PDF
        web.addJavascriptInterface(new PrintBridge(), "AndroidApp");

        web.setWebViewClient(new WebViewClient()); // keep navigation inside the app
        web.loadUrl("file:///android_asset/index.html");
    }

    /** Exposed to JavaScript as window.AndroidApp */
    private class PrintBridge {
        @JavascriptInterface
        public void printPage(final String jobName) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    doPrint(jobName == null || jobName.trim().isEmpty() ? "Document" : jobName);
                }
            });
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private void doPrint(String jobName) {
        PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        if (pm == null || web == null) return;
        PrintDocumentAdapter adapter = web.createPrintDocumentAdapter(jobName);
        PrintAttributes attrs = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .build();
        // Opens Android's print sheet; the user can pick "Save as PDF" to store it on the device.
        pm.print(jobName, adapter, attrs);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
