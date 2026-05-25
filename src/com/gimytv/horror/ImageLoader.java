package com.gimytv.horror;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

public class ImageLoader {
    private static final HashMap<String, Bitmap> imageCache = new HashMap<>();

    public static void loadImage(final String url, final ImageView imageView) {
        if (url == null || url.isEmpty()) return;
        
        String formattedUrlTemp = url.trim();
        if (formattedUrlTemp.startsWith("//")) {
            formattedUrlTemp = "https:" + formattedUrlTemp;
        } else if (formattedUrlTemp.startsWith("/")) {
            formattedUrlTemp = "https://gimyplus.com" + formattedUrlTemp;
        }
        final String finalUrl = formattedUrlTemp;

        if (imageCache.containsKey(finalUrl)) {
            imageView.setImageBitmap(imageCache.get(finalUrl));
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
                    android.util.Log.e("GimyHorror", "Image load error for: " + finalUrl, e);
                }
            }
        }).start();
    }
}
