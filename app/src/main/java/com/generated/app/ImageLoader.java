package com.generated.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageLoader {
    private static ImageLoader instance;
    private LruCache<String, Bitmap> memoryCache;
    private ExecutorService executorService;
    private Handler mainHandler;

    private ImageLoader() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
        executorService = Executors.newFixedThreadPool(4);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized ImageLoader getInstance() {
        if (instance == null) {
            instance = new ImageLoader();
        }
        return instance;
    }

    public void displayImage(final String url, final ImageView imageView) {
        if (url == null || url.isEmpty()) return;

        imageView.setTag(url);
        Bitmap bitmap = memoryCache.get(url);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            return;
        }

        imageView.setImageBitmap(null);

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                final Bitmap downloaded = downloadBitmap(url);
                if (downloaded != null) {
                    memoryCache.put(url, downloaded);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (url.equals(imageView.getTag())) {
                                imageView.setImageBitmap(downloaded);
                            }
                        }
                    });
                }
            }
        });
    }

    private Bitmap downloadBitmap(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            InputStream is = conn.getInputStream();
            return BitmapFactory.decodeStream(is);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
