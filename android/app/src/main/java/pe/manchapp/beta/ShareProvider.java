package pe.manchapp.beta;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Grants only temporary access to generated flyers and camera output. */
public class ShareProvider extends ContentProvider {
    public boolean onCreate() { return true; }
    private File file(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("[a-zA-Z0-9._-]+")) throw new FileNotFoundException();
        return new File(new File(getContext().getCacheDir(), "shared"), name);
    }
    public String getType(Uri uri) { return uri.toString().endsWith(".png") ? "image/png" : "image/jpeg"; }
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(uri), mode.contains("w") ? ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_WRITE_ONLY : ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try { File f=file(uri); MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}); c.addRow(new Object[]{f.getName(),f.length()}); return c; } catch(Exception e) { return null; }
    }
    public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
    public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
}
