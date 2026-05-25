package com.gimytv.horror;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ImageLoader {
    // 8MB is perfect for low-resolution TV poster bitmaps and ensures memory-safe bounded caching
    private static final LruCache<String, Bitmap> imageCache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    public interface ImageLoadCallback {
        void onImageLoaded(Bitmap bitmap);
    }

    public static void loadImageBitmap(final String url, final ImageLoadCallback callback) {
        if (url == null || url.isEmpty()) {
            if (callback != null) callback.onImageLoaded(null);
            return;
        }
        
        String formattedUrlTemp = url.trim();
        if (formattedUrlTemp.startsWith("//")) {
            formattedUrlTemp = "https:" + formattedUrlTemp;
        } else if (formattedUrlTemp.startsWith("/")) {
            formattedUrlTemp = "https://gimyplus.com" + formattedUrlTemp;
        }
        final String finalUrl = formattedUrlTemp;

        Bitmap cached = imageCache.get(finalUrl);
        if (cached != null) {
            if (callback != null) callback.onImageLoaded(cached);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL connUrl = new URL(finalUrl);
                    HttpURLConnection conn = (HttpURLConnection) connUrl.openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                    conn.setDoInput(true);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.connect();
                    
                    InputStream is = conn.getInputStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(is);
                    if (bitmap != null) {
                        imageCache.put(finalUrl, bitmap);
                    }
                    if (callback != null) {
                        callback.onImageLoaded(bitmap);
                    }
                } catch (Exception e) {
                    Log.e("GimyHorror", "Image load error for: " + finalUrl, e);
                    if (callback != null) callback.onImageLoaded(null);
                }
            }
        }).start();
    }

    public static void loadImage(final String url, final ImageView imageView) {
        if (url == null || url.isEmpty()) return;
        
        String formattedUrlTemp = url.trim();
        if (formattedUrlTemp.startsWith("//")) {
            formattedUrlTemp = "https:" + formattedUrlTemp;
        } else if (formattedUrlTemp.startsWith("/")) {
            formattedUrlTemp = "https://gimyplus.com" + formattedUrlTemp;
        }
        final String finalUrl = formattedUrlTemp;

        Bitmap cached = imageCache.get(finalUrl);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL connUrl = new URL(finalUrl);
                    HttpURLConnection conn = (HttpURLConnection) connUrl.openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                    conn.setDoInput(true);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.connect();
                    
                    InputStream is = conn.getInputStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(is);
                    if (bitmap != null) {
                        imageCache.put(finalUrl, bitmap);
                        imageView.post(new Runnable() {
                            @Override
                            public void run() {
                                imageView.setImageBitmap(bitmap);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e("GimyHorror", "Image load error for: " + finalUrl, e);
                }
            }
        }).start();
    }
}
