package com.generated.app;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
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

    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final String[] PIPED_INSTANCES = new String[]{
            "https://pipedapi.kavin.rocks",
            "https://api.piped.private.coffee",
            "https://piped-api.lunar.icu"
    };

    private static final String[] INVIDIOUS_INSTANCES = new String[]{
            "https://invidious.nerdvpn.de",
            "https://inv.nadeko.net",
            "https://iv.melmac.space"
    };

    public static void getStreamUrl(final String videoId, final String targetQuality, final boolean isAudioOnly, final StreamCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                // Try Piped Streams API first
                for (String instance : PIPED_INSTANCES) {
                    HttpURLConnection conn = null;
                    try {
                        URL url = new URL(instance + "/streams/" + videoId);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(4000);
                        conn.setReadTimeout(4000);
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("User-Agent", USER_AGENT);

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
                                JSONArray audioStreams = json.optJSONArray("audioStreams");
                                if (audioStreams != null && audioStreams.length() > 0) {
                                    JSONObject bestAudio = audioStreams.getJSONObject(0);
                                    String aUrl = bestAudio.optString("url", "");
                                    String mime = bestAudio.optString("mimeType", "audio/mp4");
                                    String ext = mime.contains("mp4") || mime.contains("m4a") ? "m4a" : "mp3";
                                    if (!aUrl.isEmpty()) {
                                        postSuccess(callback, aUrl, ext, mime);
                                        return;
                                    }
                                }
                            } else {
                                JSONArray videoStreams = json.optJSONArray("videoStreams");
                                if (videoStreams != null && videoStreams.length() > 0) {
                                    String matchedUrl = null;
                                    String mime = "video/mp4";

                                    for (int i = 0; i < videoStreams.length(); i++) {
                                        JSONObject stream = videoStreams.getJSONObject(i);
                                        String quality = stream.optString("quality", "");
                                        boolean hasAudio = stream.optBoolean("videoOnly", false) == false;
                                        if (hasAudio && (quality.contains(targetQuality) || targetQuality.equalsIgnoreCase("AUTO"))) {
                                            matchedUrl = stream.optString("url", "");
                                            mime = stream.optString("mimeType", "video/mp4");
                                            break;
                                        }
                                    }

                                    if (matchedUrl == null) {
                                        for (int i = 0; i < videoStreams.length(); i++) {
                                            JSONObject stream = videoStreams.getJSONObject(i);
                                            if (!stream.optBoolean("videoOnly", false)) {
                                                matchedUrl = stream.optString("url", "");
                                                mime = stream.optString("mimeType", "video/mp4");
                                                break;
                                            }
                                        }
                                    }

                                    if (matchedUrl != null && !matchedUrl.isEmpty()) {
                                        postSuccess(callback, matchedUrl, "mp4", mime);
                                        return;
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    } finally {
                        if (conn != null) conn.disconnect();
                    }
                }

                // Try Invidious Instances
                for (String instance : INVIDIOUS_INSTANCES) {
                    HttpURLConnection conn = null;
                    try {
                        URL url = new URL(instance + "/api/v1/videos/" + videoId);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(4000);
                        conn.setReadTimeout(4000);
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("User-Agent", USER_AGENT);

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
                                if (adaptive != null) {
                                    for (int i = 0; i < adaptive.length(); i++) {
                                        JSONObject fmt = adaptive.getJSONObject(i);
                                        String type = fmt.optString("type", "");
                                        String streamUrl = fmt.optString("url", "");
                                        if (type.contains("audio/") && !streamUrl.isEmpty()) {
                                            String ext = type.contains("mp4") || type.contains("m4a") ? "m4a" : "mp3";
                                            postSuccess(callback, streamUrl, ext, "audio/mp4");
                                            return;
                                        }
                                    }
                                }
                            } else {
                                JSONArray formatStreams = json.optJSONArray("formatStreams");
                                if (formatStreams != null && formatStreams.length() > 0) {
                                    for (int i = 0; i < formatStreams.length(); i++) {
                                        JSONObject fmt = formatStreams.getJSONObject(i);
                                        String qLabel = fmt.optString("qualityLabel", "");
                                        if (qLabel.contains(targetQuality) || targetQuality.equalsIgnoreCase("AUTO")) {
                                            String streamUrl = fmt.optString("url", "");
                                            if (!streamUrl.isEmpty()) {
                                                postSuccess(callback, streamUrl, "mp4", "video/mp4");
                                                return;
                                            }
                                        }
                                    }

                                    JSONObject firstFmt = formatStreams.getJSONObject(0);
                                    String streamUrl = firstFmt.optString("url", "");
                                    if (!streamUrl.isEmpty()) {
                                        postSuccess(callback, streamUrl, "mp4", "video/mp4");
                                        return;
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    } finally {
                        if (conn != null) conn.disconnect();
                    }
                }

                // If direct link resolution failed
                postError(callback, "השרת הישיר אינו זמין כרגע, פותח חלון הורדה...");
            }
        });
    }

    public static void startDownload(Context context, String title, String downloadUrl, String extension, String mimeType, boolean isAudio) {
        try {
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                fallbackBrowserDownload(context, downloadUrl);
                return;
            }

            Uri uri = Uri.parse(downloadUrl);
            DownloadManager.Request request = new DownloadManager.Request(uri);

            String cleanTitle = title != null ? title.replaceAll("[\\\\/:*?\"<>|]", "_").trim() : "video";
            if (cleanTitle.length() > 60) {
                cleanTitle = cleanTitle.substring(0, 60);
            }
            String filename = cleanTitle + "." + extension;

            request.setTitle(title != null ? title : "הורדת תוכן");
            request.setDescription(isAudio ? "הורדת שמע MP3..." : "הורדת וידאו MP4...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.addRequestHeader("User-Agent", USER_AGENT);

            if (isAudio) {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, filename);
            } else {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            }

            request.setMimeType(mimeType);

            downloadManager.enqueue(request);
            Toast.makeText(context, "ההורדה החלה בהצלחה! הקובץ יישמר במכשיר", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            fallbackBrowserDownload(context, downloadUrl);
        }
    }

    public static void downloadViaWebPortal(Context context, String videoId, boolean isAudioOnly) {
        String portalUrl = isAudioOnly ?
                "https://y2mate.nu/en/youtube-to-mp3/" + videoId :
                "https://y2mate.nu/en/youtube/" + videoId;
        fallbackBrowserDownload(context, portalUrl);
    }

    private static void fallbackBrowserDownload(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Toast.makeText(context, "נפתח בדפדפן להורדה מהירה", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "שגיאה בפתיחת ההורדה: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
