package com.mybilling.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private WebView web;
    private WebView pdfWebView; // off-screen renderer (kept referenced during load)

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // localStorage -> saves invoices on device
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.addJavascriptInterface(new AppBridge(), "AndroidApp");
        web.setWebViewClient(new WebViewClient());
        web.loadUrl("file:///android_asset/index.html");
    }

    /** window.AndroidApp in the web app */
    private class AppBridge {
        @JavascriptInterface
        public void printPage(final String jobName) {
            runOnUiThread(() -> doPrint(safeName(jobName)));
        }

        @JavascriptInterface
        public void sharePdf(final String jobName, final String html) {
            runOnUiThread(() -> makePdf(safeName(jobName), html,
                    (file, nice) -> sharePdfFile(file, nice)));
        }

        @JavascriptInterface
        public void downloadPdf(final String jobName, final String html) {
            runOnUiThread(() -> makePdf(safeName(jobName), html,
                    (file, nice) -> saveToDownloads(file, nice)));
        }
    }

    // ---- Render the invoice HTML to a PDF file (off-screen) ----
    private interface PdfReady { void ready(File pdf, String niceName); }

    private void makePdf(final String name, final String html, final PdfReady then) {
        final String fileName = name + ".pdf";
        final WebView pw = new WebView(this);
        pdfWebView = pw;
        WebSettings st = pw.getSettings();
        st.setJavaScriptEnabled(false);
        st.setAllowFileAccess(true);
        st.setLoadWithOverviewMode(true);
        st.setUseWideViewPort(true);
        pw.setWebViewClient(new WebViewClient() {
            private boolean done = false;
            @Override
            public void onPageFinished(WebView v, String url) {
                if (done) return;
                done = true;
                // small delay so layout/fonts settle before drawing
                v.postDelayed(() -> {
                    File f = drawToPdf(v, fileName);
                    pdfWebView = null;
                    if (f != null) then.ready(f, fileName);
                    else toast("Sorry, could not create the PDF");
                }, 350);
            }
        });
        pw.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null);
    }

    private File drawToPdf(WebView v, String fileName) {
        try {
            final int widthPx = 794; // A4 width at ~96dpi
            v.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            v.measure(
                    View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int heightPx = v.getMeasuredHeight();
            if (heightPx <= 0) {
                heightPx = (int) (v.getContentHeight()
                        * getResources().getDisplayMetrics().density);
            }
            if (heightPx <= 0) heightPx = 1123; // A4 fallback
            v.layout(0, 0, widthPx, heightPx);

            PdfDocument doc = new PdfDocument();
            PdfDocument.PageInfo info =
                    new PdfDocument.PageInfo.Builder(widthPx, heightPx, 1).create();
            PdfDocument.Page page = doc.startPage(info);
            Canvas c = page.getCanvas();
            c.drawColor(android.graphics.Color.WHITE);
            v.draw(c);
            doc.finishPage(page);

            File dir = new File(getCacheDir(), "pdfs");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, fileName);
            FileOutputStream fos = new FileOutputStream(out);
            doc.writeTo(fos);
            fos.close();
            doc.close();
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Share sheet (WhatsApp, Gmail, etc.) ----
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

    // ---- Save to Downloads ----
    private void saveToDownloads(File pdf, String niceName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, niceName);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) { toast("Could not save to Downloads"); return; }
                try (OutputStream os = getContentResolver().openOutputStream(uri);
                     InputStream is = new FileInputStream(pdf)) {
                    copy(is, os);
                }
                toast("Saved to Downloads: " + niceName);
            } else {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                File out = new File(dir, niceName);
                try (InputStream is = new FileInputStream(pdf);
                     OutputStream os = new FileOutputStream(out)) {
                    copy(is, os);
                }
                toast("Saved: " + out.getAbsolutePath());
            }
        } catch (Exception e) {
            toast("Could not save the PDF");
        }
    }

    private void copy(InputStream is, OutputStream os) throws Exception {
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
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
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
