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
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36";

    private static final String[] INVIDIOUS_INSTANCES = new String[]{
            "https://inv.nadeko.net",
            "https://invidious.jing.rocks",
            "https://yt.artemislena.eu",
            "https://invidious.privacyredirect.com",
            "https://iv.melmac.space",
            "https://invidious.drgns.space"
    };

    private static final String[] PIPED_INSTANCES = new String[]{
            "https://pipedapi.kavin.rocks",
            "https://api.piped.privacydev.net",
            "https://pipedapi.tokhmi.xyz"
    };

    public static void getStreamUrl(final String videoId, final String targetQuality, final boolean isAudioOnly, final StreamCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                // Tier 1: Invidious Instances
                for (String instance : INVIDIOUS_INSTANCES) {
                    try {
                        URL url = new URL(instance + "/api/v1/videos/" + videoId);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(4500);
                        conn.setReadTimeout(4500);
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
                                            String mime = "audio/mp4";
                                            postSuccess(callback, streamUrl, ext, mime);
                                            return;
                                        }
                                    }
                                }
                            } else {
                                JSONArray formatStreams = json.optJSONArray("formatStreams");
                                if (formatStreams != null && formatStreams.length() > 0) {
                                    String matchedUrl = null;
                                    String container = "mp4";

                                    for (int i = 0; i < formatStreams.length(); i++) {
                                        JSONObject fmt = formatStreams.getJSONObject(i);
                                        String qLabel = fmt.optString("qualityLabel", "");
                                        if (qLabel.equalsIgnoreCase(targetQuality) || targetQuality.equalsIgnoreCase("AUTO")) {
                                            matchedUrl = fmt.optString("url", "");
                                            container = fmt.optString("container", "mp4");
                                            break;
                                        }
                                    }

                                    if (matchedUrl == null) {
                                        JSONObject firstFmt = formatStreams.getJSONObject(0);
                                        matchedUrl = firstFmt.optString("url", "");
                                        container = firstFmt.optString("container", "mp4");
                                    }

                                    if (matchedUrl != null && !matchedUrl.isEmpty()) {
                                        postSuccess(callback, matchedUrl, container, "video/" + container);
                                        return;
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // Tier 2: Piped API Streams
                for (String instance : PIPED_INSTANCES) {
                    try {
                        URL url = new URL(instance + "/streams/" + videoId);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(4500);
                        conn.setReadTimeout(4500);
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

                            JSONObject root = new JSONObject(sb.toString());

                            if (isAudioOnly) {
                                JSONArray audioStreams = root.optJSONArray("audioStreams");
                                if (audioStreams != null && audioStreams.length() > 0) {
                                    JSONObject bestAudio = audioStreams.getJSONObject(0);
                                    String audioUrl = bestAudio.optString("url", "");
                                    String format = bestAudio.optString("format", "m4a").toLowerCase();
                                    if (!audioUrl.isEmpty()) {
                                        postSuccess(callback, audioUrl, format.contains("mp3") ? "mp3" : "m4a", "audio/mp4");
                                        return;
                                    }
                                }
                            } else {
                                JSONArray videoStreams = root.optJSONArray("videoStreams");
                                if (videoStreams != null && videoStreams.length() > 0) {
                                    String bestVideoUrl = null;
                                    String ext = "mp4";

                                    for (int i = 0; i < videoStreams.length(); i++) {
                                        JSONObject stream = videoStreams.getJSONObject(i);
                                        boolean videoOnly = stream.optBoolean("videoOnly", false);
                                        // Prefer progressive streams (video + audio together)
                                        if (!videoOnly) {
                                            String quality = stream.optString("quality", "");
                                            if (quality.contains(targetQuality) || targetQuality.equalsIgnoreCase("AUTO") || bestVideoUrl == null) {
                                                bestVideoUrl = stream.optString("url", "");
                                                ext = stream.optString("format", "mp4").toLowerCase();
                                                if (quality.contains(targetQuality)) break;
                                            }
                                        }
                                    }

                                    if (bestVideoUrl != null && !bestVideoUrl.isEmpty()) {
                                        postSuccess(callback, bestVideoUrl, ext, "video/" + ext);
                                        return;
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // Tier 3: Direct Invidious Proxy Stream
                String itag = isAudioOnly ? "140" : (targetQuality.contains("720") ? "22" : "18");
                String fallbackUrl = "https://inv.nadeko.net/latest_version?id=" + videoId + "&itag=" + itag;
                postSuccess(callback, fallbackUrl, isAudioOnly ? "m4a" : "mp4", isAudioOnly ? "audio/mp4" : "video/mp4");
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

            String cleanTitle = title != null ? title.replaceAll("[\\\\/:*?\"<>|]", "_").trim() : "safe_youtube_file";
            if (cleanTitle.length() > 60) {
                cleanTitle = cleanTitle.substring(0, 60);
            }
            String filename = cleanTitle + "." + extension;

            request.setTitle(title != null ? title : "הורדת תוכן");
            request.setDescription(isAudio ? "הורדת שמע..." : "הורדת וידאו...");
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

    private static void fallbackBrowserDownload(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Toast.makeText(context, "ההורדה נפתחה בדפדפן המכשיר", Toast.LENGTH_SHORT).show();
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
