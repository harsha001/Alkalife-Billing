package com.mybilling.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

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

        // Bridge: lets the web app print, download, or share invoices as PDF natively.
        web.addJavascriptInterface(new AppBridge(), "AndroidApp");

        web.setWebViewClient(new WebViewClient());
        web.loadUrl("file:///android_asset/index.html");
    }

    /** Exposed to JavaScript as window.AndroidApp */
    private class AppBridge {
        @JavascriptInterface
        public void printPage(final String jobName) {
            runOnUiThread(() -> doPrint(safeName(jobName)));
        }

        @JavascriptInterface
        public void sharePdf(final String jobName) {
            runOnUiThread(() -> makePdf(safeName(jobName), MainActivity.this::sharePdfFile));
        }

        @JavascriptInterface
        public void downloadPdf(final String jobName) {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    makePdf(safeName(jobName), MainActivity.this::saveToDownloads);
                } else {
                    // Older Android: use the print sheet, which offers "Save as PDF".
                    doPrint(safeName(jobName));
                }
            });
        }
    }

    // ---- PDF generation (silent, into app cache) ----
    private interface PdfReady { void ready(File pdf, String niceName); }

    private void makePdf(final String jobName, final PdfReady then) {
        PrintAttributes attrs = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(new PrintAttributes.Resolution("pdf", "pdf", 600, 600))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build();
        PrintDocumentAdapter adapter = web.createPrintDocumentAdapter(jobName);
        final File dir = new File(getCacheDir(), "pdfs");
        final String fileName = jobName + ".pdf";
        new PdfPrint(attrs).print(adapter, dir, fileName, new PdfPrint.CallbackPrint() {
            @Override public void onSuccess(File file) { then.ready(file, fileName); }
            @Override public void onFailure() { toast("Sorry, could not create the PDF"); }
        });
    }

    // ---- Share via the Android share sheet (WhatsApp, Gmail, etc.) ----
    private void sharePdfFile(File pdf, String niceName) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdf);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, niceName);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share " + niceName));
        } catch (Exception e) {
            toast("Could not open share");
        }
    }

    // ---- Save straight into the device's Downloads folder (Android 10+) ----
    private void saveToDownloads(File pdf, String niceName) {
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, niceName);
            cv.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) { toast("Could not save to Downloads"); return; }
            try (OutputStream os = getContentResolver().openOutputStream(uri);
                 InputStream is = new FileInputStream(pdf)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            }
            toast("Saved to Downloads: " + niceName);
        } catch (Exception e) {
            toast("Could not save to Downloads");
        }
    }

    // ---- System print sheet (also offers Save as PDF) ----
    private void doPrint(String jobName) {
        PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        if (pm == null || web == null) return;
        PrintDocumentAdapter adapter = web.createPrintDocumentAdapter(jobName);
        PrintAttributes attrs = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .build();
        pm.print(jobName, adapter, attrs);
    }

    private String safeName(String jobName) {
        if (jobName == null || jobName.trim().isEmpty()) return "Document";
        return jobName.replaceAll("[^a-zA-Z0-9 _.-]", "").trim();
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_LONG).show();
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
