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

    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    // Fast and reliable NewPipe / Piped API instances
    private static final String[] PIPED_INSTANCES = new String[]{
            "https://api.piped.private.coffee",
            "https://pipedapi.kavin.rocks",
            "https://piped-api.lunar.icu",
            "https://pipedapi.leptons.xyz",
            "https://pipedapi.tokhmi.xyz"
    };

    // Fast Invidious API instances
    private static final String[] INVIDIOUS_INSTANCES = new String[]{
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://iv.melmac.space",
            "https://invidious.jing.rocks"
    };

    public static void getStreamUrl(final String videoId, final String targetQuality, final boolean isAudioOnly, final StreamCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                // 1. Try NewPipe / Piped API first
                for (String instance : PIPED_INSTANCES) {
                    HttpURLConnection conn = null;
                    try {
                        URL url = new URL(instance + "/streams/" + videoId);
                        conn = (HttpURLConnection) url.openConnection();
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
                                JSONArray audioStreams = json.optJSONArray("audioStreams");
                                if (audioStreams != null && audioStreams.length() > 0) {
                                    JSONObject selectedAudio = null;
                                    int desiredBitrate = 128000;
                                    if ("160k".equalsIgnoreCase(targetQuality)) {
                                        desiredBitrate = 160000;
                                    } else if ("64k".equalsIgnoreCase(targetQuality)) {
                                        desiredBitrate = 64000;
                                    }

                                    int minDiff = Integer.MAX_VALUE;
                                    for (int i = 0; i < audioStreams.length(); i++) {
                                        JSONObject stream = audioStreams.getJSONObject(i);
                                        int bitrate = stream.optInt("bitrate", 0);
                                        int diff = Math.abs(bitrate - desiredBitrate);
                                        if (diff < minDiff) {
                                            minDiff = diff;
                                            selectedAudio = stream;
                                        }
                                    }

                                    if (selectedAudio == null) {
                                        selectedAudio = audioStreams.getJSONObject(0);
                                    }

                                    String aUrl = selectedAudio.optString("url", "");
                                    String mime = selectedAudio.optString("mimeType", "audio/mp4");
                                    String ext = (mime.contains("mp4") || mime.contains("m4a")) ? "m4a" : "mp3";
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
                                        boolean hasAudio = !stream.optBoolean("videoOnly", false);
                                        if (hasAudio && quality.contains(targetQuality)) {
                                            matchedUrl = stream.optString("url", "");
                                            mime = stream.optString("mimeType", "video/mp4");
                                            break;
                                        }
                                    }

                                    if (matchedUrl == null) {
                                        for (int i = 0; i < videoStreams.length(); i++) {
                                            JSONObject stream = videoStreams.getJSONObject(i);
                                            boolean hasAudio = !stream.optBoolean("videoOnly", false);
                                            if (hasAudio) {
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

                // 2. Try Invidious API
                for (String instance : INVIDIOUS_INSTANCES) {
                    HttpURLConnection conn = null;
                    try {
                        URL url = new URL(instance + "/api/v1/videos/" + videoId);
                        conn = (HttpURLConnection) url.openConnection();
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
                                if (adaptive != null && adaptive.length() > 0) {
                                    JSONObject bestAudio = null;
                                    int targetBr = 128000;
                                    if ("160k".equalsIgnoreCase(targetQuality)) targetBr = 160000;
                                    else if ("64k".equalsIgnoreCase(targetQuality)) targetBr = 64000;

                                    int minDiff = Integer.MAX_VALUE;
                                    for (int i = 0; i < adaptive.length(); i++) {
                                        JSONObject fmt = adaptive.getJSONObject(i);
                                        String type = fmt.optString("type", "");
                                        if (type.contains("audio/")) {
                                            int br = fmt.optInt("bitrate", 0);
                                            int diff = Math.abs(br - targetBr);
                                            if (diff < minDiff) {
                                                minDiff = diff;
                                                bestAudio = fmt;
                                            }
                                        }
                                    }

                                    if (bestAudio != null) {
                                        String streamUrl = bestAudio.optString("url", "");
                                        String type = bestAudio.optString("type", "audio/mp4");
                                        String ext = (type.contains("mp4") || type.contains("m4a")) ? "m4a" : "mp3";
                                        if (!streamUrl.isEmpty()) {
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
                                        if (qLabel.contains(targetQuality)) {
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

                // 3. Fallback direct stream links from verified Invidious instance
                String fallbackInstance = "https://inv.nadeko.net";
                if (isAudioOnly) {
                    String audioDirectUrl = fallbackInstance + "/latest_version?id=" + videoId + "&itag=140";
                    postSuccess(callback, audioDirectUrl, "m4a", "audio/mp4");
                } else {
                    String itag = "22"; // 720p HD
                    if ("360p".equalsIgnoreCase(targetQuality) || "480p".equalsIgnoreCase(targetQuality)) {
                        itag = "18"; // 360p MP4
                    }
                    String videoDirectUrl = fallbackInstance + "/latest_version?id=" + videoId + "&itag=" + itag;
                    postSuccess(callback, videoDirectUrl, "mp4", "video/mp4");
                }
            }
        });
    }

    public static void startDownload(Context context, String title, String downloadUrl, String extension, String mimeType, boolean isAudio) {
        try {
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                Toast.makeText(context, "שירות ההורדות של המכשיר אינו זמין", Toast.LENGTH_LONG).show();
                return;
            }

            Uri uri = Uri.parse(downloadUrl);
            DownloadManager.Request request = new DownloadManager.Request(uri);

            String cleanTitle = title != null ? title.replaceAll("[\\\\/:*?\"<>|]", "_").trim() : "media";
            if (cleanTitle.length() > 50) {
                cleanTitle = cleanTitle.substring(0, 50);
            }
            String filename = cleanTitle + "." + extension;

            request.setTitle(title != null ? title : (isAudio ? "הורדת שמע" : "הורדת סרטון"));
            request.setDescription(isAudio ? "הורדת קובץ שמע ישירות לאפליקציה" : "הורדת סרטון וידאו ישירות לאפליקציה");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.addRequestHeader("User-Agent", USER_AGENT);

            if (isAudio) {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, filename);
            } else {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            }

            if (mimeType != null && !mimeType.isEmpty()) {
                request.setMimeType(mimeType);
            }

            downloadManager.enqueue(request);
            Toast.makeText(context, isAudio ? "הורדת השיר החלה ישירות במכשיר!" : "הורדת הסרטון החלה ישירות במכשיר!", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "שגיאה בהפעלת מנהל ההורדות: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
