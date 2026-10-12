package com.generated.app;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadHelper {

    public interface StreamCallback {
        void onSuccess(String streamUrl, String extension, String mimeType);
        void onError(String error);
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String[] INVIDIOUS_INSTANCES = new String[]{
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://vid.puffyan.us"
    };

    public static void getStreamUrl(final String videoId, final String targetQuality, final boolean isAudioOnly, final StreamCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                boolean found = false;
                for (String instance : INVIDIOUS_INSTANCES) {
                    try {
                        URL url = new URL(instance + "/api/v1/videos/" + videoId);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(6000);
                        conn.setReadTimeout(6000);
                        conn.setRequestMethod("GET");

                        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line);
                            }
                            reader.close();

                            JSONObject json = new JSONObject(sb.toString());

                            if (isAudioOnly) {
                                JSONArray adaptive = json.optJSONArray("adaptiveFormats");
                                String bestAudioUrl = null;
                                String bestExt = "m4a";
                                String bestMime = "audio/mp4";

                                if (adaptive != null) {
                                    for (int i = 0; i < adaptive.length(); i++) {
                                        JSONObject fmt = adaptive.getJSONObject(i);
                                        String type = fmt.optString("type", "");
                                        if (type.contains("audio/")) {
                                            bestAudioUrl = fmt.optString("url", "");
                                            if (type.contains("mp4") || type.contains("m4a")) {
                                                bestExt = "m4a";
                                                bestMime = "audio/mp4";
                                            } else if (type.contains("webm")) {
                                                bestExt = "webm";
                                                bestMime = "audio/webm";
                                            }
                                            break;
                                        }
                                    }
                                }

                                if (bestAudioUrl != null && !bestAudioUrl.isEmpty()) {
                                    postSuccess(callback, bestAudioUrl, bestExt, bestMime);
                                    found = true;
                                    break;
                                }
                            } else {
                                JSONArray formatStreams = json.optJSONArray("formatStreams");
                                String selectedUrl = null;
                                String container = "mp4";

                                if (formatStreams != null && formatStreams.length() > 0) {
                                    for (int i = 0; i < formatStreams.length(); i++) {
                                        JSONObject fmt = formatStreams.getJSONObject(i);
                                        String qLabel = fmt.optString("qualityLabel", "");
                                        if (qLabel.equalsIgnoreCase(targetQuality) || targetQuality.equalsIgnoreCase("AUTO")) {
                                            selectedUrl = fmt.optString("url", "");
                                            container = fmt.optString("container", "mp4");
                                            break;
                                        }
                                    }

                                    if (selectedUrl == null) {
                                        JSONObject firstFmt = formatStreams.getJSONObject(0);
                                        selectedUrl = firstFmt.optString("url", "");
                                        container = firstFmt.optString("container", "mp4");
                                    }
                                }

                                if (selectedUrl != null && !selectedUrl.isEmpty()) {
                                    postSuccess(callback, selectedUrl, container, "video/" + container);
                                    found = true;
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }

                if (!found) {
                    try {
                        String fallbackUrl = "https://inv.nadeko.net/latest_version?id=" + videoId + "&itag=" + (isAudioOnly ? "140" : "18");
                        postSuccess(callback, fallbackUrl, isAudioOnly ? "m4a" : "mp4", isAudioOnly ? "audio/mp4" : "video/mp4");
                    } catch (Exception e) {
                        postError(callback, "לא ניתן לחלץ קישור להורדה עבור סרטון זה");
                    }
                }
            }
        });
    }

    public static void startDownload(Context context, String title, String downloadUrl, String extension, String mimeType, boolean isAudio) {
        try {
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                Toast.makeText(context, "שירות ההורדות אינו זמין במכשיר זה", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri uri = Uri.parse(downloadUrl);
            DownloadManager.Request request = new DownloadManager.Request(uri);

            String cleanTitle = title != null ? title.replaceAll("[\\\\/:*?\"<>|]", "_") : "video";
            String filename = cleanTitle + "." + extension;

            request.setTitle(title != null ? title : "הורדת קובץ");
            request.setDescription(isAudio ? "מוריד קובץ שמע..." : "מוריד קובץ וידאו...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            if (isAudio) {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, filename);
            } else {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            }

            request.setMimeType(mimeType);

            downloadManager.enqueue(request);
            Toast.makeText(context, "ההורדה החלה! הקובץ יישמר בתיקיית ההורדות/מוזיקה", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "שגיאה בהתחלת ההורדה: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void postSuccess(final StreamCallback callback, final String url, final String ext, final String mime) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(url, ext, mime);
            }
        });
    }

    private static void postError(final StreamCallback callback, final String error) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(error);
            }
        });
    }
}
