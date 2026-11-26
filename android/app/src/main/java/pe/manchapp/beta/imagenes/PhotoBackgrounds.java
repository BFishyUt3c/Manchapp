package pe.manchapp.beta;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import java.io.IOException;
import java.io.InputStream;

/** Bundled tabletop photos and the soft shadow that anchors a cutout to them. */
final class PhotoBackgrounds {
    private PhotoBackgrounds() {}

    static boolean isPreset(String kind) {
        return "wood".equals(kind) || "marble".equals(kind) || "cloth".equals(kind);
    }

    static Bitmap render(Context context, int width, int height, String kind, boolean angled) throws IOException {
        if (!isPreset(kind)) throw new IOException("No encontramos ese fondo.");
        BitmapFactory.Options options = new BitmapFactory.Options();
        int edge = Math.max(width, height);
        options.inSampleSize = edge <= 160 ? 8 : edge <= 400 ? 2 : 1;
        try (InputStream input = context.getAssets().open("backgrounds/" + kind + ".jpg")) {
            Bitmap source = BitmapFactory.decodeStream(input, null, options);
            if (source == null) throw new IOException("No pudimos abrir ese fondo.");
            try {
                Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(result);
                if (angled) {
                    // The tabletop widens toward the viewer. Overscan keeps all edges covered.
                    Matrix perspective = new Matrix();
                    float[] frame = {0, 0, width, 0, width, height, 0, height};
                    float[] projected = {
                            -.04f * width, 0, 1.04f * width, 0,
                            1.20f * width, height, -.20f * width, height
                    };
                    if (perspective.setPolyToPoly(frame, 0, projected, 0, 4)) canvas.concat(perspective);
                }
                int sourceWidth = source.getWidth(), sourceHeight = source.getHeight();
                float scale = Math.max(width / (float) sourceWidth, height / (float) sourceHeight);
                int cropWidth = Math.min(sourceWidth, Math.max(1, Math.round(width / scale)));
                int cropHeight = Math.min(sourceHeight, Math.max(1, Math.round(height / scale)));
                int left = (sourceWidth - cropWidth) / 2, top = (sourceHeight - cropHeight) / 2;
                canvas.drawBitmap(source, new Rect(left, top, left + cropWidth, top + cropHeight),
                        new RectF(0, 0, width, height), new Paint(Paint.FILTER_BITMAP_FLAG));
                return result;
            } finally {
                source.recycle();
            }
        }
    }

    static void drawContactShadow(Canvas canvas, RectF bounds, boolean angled) {
        float width = bounds.width(), height = bounds.height();
        if (width <= 0 || height <= 0) return;
        float x = bounds.centerX() + width * .035f;
        if (angled) {
            float y = bounds.bottom - height * .055f;
            shade(canvas, x, y + height * .022f, width * .58f, height * .19f, 0x54402c22);
            shade(canvas, x, y, width * .46f, height * .09f, 0x58402c22);
        } else {
            shade(canvas, x, bounds.centerY() + height * .055f, width * .60f, height * .60f, 0x50402c22);
        }
    }

    private static void shade(Canvas canvas, float x, float y, float radiusX, float radiusY, int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int rgb = color & 0x00ffffff;
        int midAlpha = (color >>> 24) * 35 / 100;
        paint.setShader(new RadialGradient(0, 0, 1,
                new int[]{color, (midAlpha << 24) | rgb, rgb},
                new float[]{0, .53f, 1}, Shader.TileMode.CLAMP));
        int save = canvas.save();
        canvas.translate(x, y);
        canvas.scale(radiusX, radiusY);
        canvas.drawCircle(0, 0, 1, paint);
        canvas.restoreToCount(save);
    }
}
