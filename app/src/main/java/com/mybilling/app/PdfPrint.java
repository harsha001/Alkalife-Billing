package com.mybilling.app;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;

import java.io.File;

/**
 * Drives a WebView's PrintDocumentAdapter to render the current page into a real
 * PDF file (in app cache), without showing the system print dialog. The finished
 * file is then shared or saved natively.
 */
class PdfPrint {

    interface CallbackPrint {
        void onSuccess(File file);
        void onFailure();
    }

    private final PrintAttributes printAttributes;

    PdfPrint(PrintAttributes printAttributes) {
        this.printAttributes = printAttributes;
    }

    void print(final PrintDocumentAdapter adapter, final File dir,
               final String fileName, final CallbackPrint callback) {
        adapter.onLayout(null, printAttributes, null,
                new PrintDocumentAdapter.LayoutResultCallback() {
                    @Override
                    public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                        ParcelFileDescriptor pfd = outputFile(dir, fileName);
                        if (pfd == null) { callback.onFailure(); return; }
                        adapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, pfd,
                                new CancellationSignal(),
                                new PrintDocumentAdapter.WriteResultCallback() {
                                    @Override
                                    public void onWriteFinished(PageRange[] pages) {
                                        super.onWriteFinished(pages);
                                        if (pages != null && pages.length > 0) {
                                            callback.onSuccess(new File(dir, fileName));
                                        } else {
                                            callback.onFailure();
                                        }
                                    }

                                    @Override
                                    public void onWriteFailed(CharSequence error) {
                                        super.onWriteFailed(error);
                                        callback.onFailure();
                                    }
                                });
                    }

                    @Override
                    public void onLayoutFailed(CharSequence error) {
                        super.onLayoutFailed(error);
                        callback.onFailure();
                    }
                }, null);
    }

    private ParcelFileDescriptor outputFile(File dir, String fileName) {
        try {
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, fileName);
            if (file.exists()) file.delete();
            file.createNewFile();
            return ParcelFileDescriptor.open(file,
                    ParcelFileDescriptor.MODE_READ_WRITE);
        } catch (Exception e) {
            return null;
        }
    }
}
