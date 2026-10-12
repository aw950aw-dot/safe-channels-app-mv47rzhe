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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadHelper {

    public interface StreamCallback {
        void onSuccess(String streamUrl, String extension, String mimeType);
        void onError(String error);
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(6);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    // Fast, resilient APIs for stream links
    private static final String[] PIPED_INSTANCES = new String[]{
            "https://api.piped.private.coffee",
            "https://pipedapi.kavin.rocks",
            "https://piped-api.lunar.icu",
            "https://pipedapi.leptons.xyz",
            "https://pipedapi.tokhmi.xyz"
    };

    private static final String[] INVIDIOUS_INSTANCES = new String[]{
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://iv.melmac.space",
            "https://invidious.jing.rocks",
            "https://vid.puffyan.us"
    };

    private static final String[] COBALT_INSTANCES = new String[]{
            "https://co.wuk.sh/api/json",
            "https://api.cobalt.tools/api/json"
    };

    public static void getStreamUrl(final String videoId, final String targetQuality, final boolean isAudioOnly, final StreamCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                // 1. Try Cobalt API (High reliability and directly downloadable signed CDN streams)
                for (String cobaltUrl : COBALT_INSTANCES) {
                    HttpURLConnection conn = null;
                    try {
                        URL url = new URL(cobaltUrl);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setConnectTimeout(4500);
                        conn.setReadTimeout(5000);
                        conn.setDoOutput(true);
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setRequestProperty("Accept", "application/json");
                        conn.setRequestProperty("User-Agent", USER_AGENT);

                        JSONObject body = new JSONObject();
                        body.put("url", "https://www.youtube.com/watch?v=" + videoId);
                        if (isAudioOnly) {
                            body.put("isAudioOnly", true);
                            body.put("aFormat", "mp3");
                        } else {
                            String vQuality = "720";
                            if (targetQuality != null && targetQuality.contains("1080")) vQuality = "1080";
                            else if (targetQuality != null && targetQuality.contains("360")) vQuality = "360";
                            body.put("vQuality", vQuality);
                        }

                        OutputStream os = conn.getOutputStream();
                        os.write(body.toString().getBytes("UTF-8"));
                        os.flush();
                        os.close();

                        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line);
                            }
                            reader.close();

                            JSONObject json = new JSONObject(sb.toString());
                            String stream = json.optString("url", "");
                            if (!stream.isEmpty()) {
                                String ext = isAudioOnly ? "mp3" : "mp4";
                                String mime = isAudioOnly ? "audio/mpeg" : "video/mp4";
                                postSuccess(callback, stream, ext, mime);
                                return;
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
                                if (adaptive != null && adaptive.length() > 0) {
                                    for (int i = 0; i < adaptive.length(); i++) {
                                        JSONObject fmt = adaptive.getJSONObject(i);
                                        String type = fmt.optString("type", "");
                                        if (type.contains("audio/")) {
                                            String streamUrl = fmt.optString("url", "");
                                            if (!streamUrl.isEmpty()) {
                                                String ext = type.contains("mp4") || type.contains("m4a") ? "m4a" : "mp3";
                                                postSuccess(callback, streamUrl, ext, "audio/mp4");
                                                return;
                                            }
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

                // 3. Try Piped API
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
                                    JSONObject sel = audioStreams.getJSONObject(0);
                                    String aUrl = sel.optString("url", "");
                                    String mime = sel.optString("mimeType", "audio/mp4");
                                    String ext = (mime.contains("mp4") || mime.contains("m4a")) ? "m4a" : "mp3";
                                    if (!aUrl.isEmpty()) {
                                        postSuccess(callback, aUrl, ext, mime);
                                        return;
                                    }
                                }
                            } else {
                                JSONArray videoStreams = json.optJSONArray("videoStreams");
                                if (videoStreams != null && videoStreams.length() > 0) {
                                    for (int i = 0; i < videoStreams.length(); i++) {
                                        JSONObject stream = videoStreams.getJSONObject(i);
                                        boolean hasAudio = !stream.optBoolean("videoOnly", false);
                                        if (hasAudio) {
                                            String matchedUrl = stream.optString("url", "");
                                            String mime = stream.optString("mimeType", "video/mp4");
                                            if (!matchedUrl.isEmpty()) {
                                                postSuccess(callback, matchedUrl, "mp4", mime);
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    } finally {
                        if (conn != null) conn.disconnect();
                    }
                }

                // 4. Invidious proxy fallback
                String directProxyInstance = "https://inv.nadeko.net";
                if (isAudioOnly) {
                    String audioDirectUrl = directProxyInstance + "/latest_version?id=" + videoId + "&itag=140";
                    postSuccess(callback, audioDirectUrl, "m4a", "audio/mp4");
                } else {
                    String itag = "22"; // 720p
                    if ("360p".equalsIgnoreCase(targetQuality) || "480p".equalsIgnoreCase(targetQuality)) {
                        itag = "18"; // 360p
                    }
                    String videoDirectUrl = directProxyInstance + "/latest_version?id=" + videoId + "&itag=" + itag;
                    postSuccess(callback, videoDirectUrl, "mp4", "video/mp4");
                }
            }
        });
    }

    public static void startDownload(final Context context, final String title, final String downloadUrl, final String extension, final String mimeType, final boolean isAudio) {
        final DataManager dm = DataManager.getInstance(context);
        final String targetFolderKey = isAudio ? dm.getAudioDownloadFolder() : dm.getVideoDownloadFolder();

        // Try DownloadManager first with proper headers and directory mapping
        try {
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager != null) {
                Uri uri = Uri.parse(downloadUrl);
                DownloadManager.Request request = new DownloadManager.Request(uri);

                String cleanTitle = title != null ? title.replaceAll("[\\\\/:*?\"<>|]", "_").trim() : "media";
                if (cleanTitle.length() > 60) {
                    cleanTitle = cleanTitle.substring(0, 60);
                }
                String filename = cleanTitle + "." + extension;

                request.setTitle(title != null ? title : (isAudio ? "הורדת שיר" : "הורדת סרטון"));
                request.setDescription("הורדה מיוטיוב בטוח");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
                request.addRequestHeader("User-Agent", USER_AGENT);

                // Set destination according to user selected preference
                if (DataManager.FOLDER_DEFAULT_MUSIC.equalsIgnoreCase(targetFolderKey)) {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, filename);
                } else if (DataManager.FOLDER_DEFAULT_MOVIES.equalsIgnoreCase(targetFolderKey)) {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, filename);
                } else if (DataManager.FOLDER_SAFETUBE_MUSIC.equalsIgnoreCase(targetFolderKey)) {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "SafeTube/" + filename);
                } else if (DataManager.FOLDER_SAFETUBE_VIDEOS.equalsIgnoreCase(targetFolderKey)) {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "SafeTube/" + filename);
                } else {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                }

                if (mimeType != null && !mimeType.isEmpty()) {
                    request.setMimeType(mimeType);
                }

                downloadManager.enqueue(request);
                Toast.makeText(context, (isAudio ? "הורדת השיר החלה לתיקיית: " : "הורדת הסרטון החלה לתיקיית: ") + targetFolderKey, Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception ex) {
            // DownloadManager exception (e.g. Scoped storage or unsupported url) -> Run Background direct streaming file save
            ex.printStackTrace();
        }

        // Direct Stream Saver Fallback:
        downloadDirectly(context, title, downloadUrl, extension, isAudio, targetFolderKey);
    }

    private static void downloadDirectly(final Context context, final String title, final String downloadUrl, final String extension, final boolean isAudio, final String targetFolderKey) {
        Toast.makeText(context, "מתחיל הורדה ישירה למכשיר...", Toast.LENGTH_SHORT).show();

        executor.submit(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                InputStream in = null;
                FileOutputStream out = null;
                try {
                    String cleanTitle = title != null ? title.replaceAll("[\\\\/:*?\"<>|]", "_").trim() : "media";
                    if (cleanTitle.length() > 50) cleanTitle = cleanTitle.substring(0, 50);
                    String fileName = cleanTitle + "_" + System.currentTimeMillis() + "." + extension;

                    File targetDir;
                    if (DataManager.FOLDER_DEFAULT_MUSIC.equalsIgnoreCase(targetFolderKey)) {
                        targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                    } else if (DataManager.FOLDER_DEFAULT_MOVIES.equalsIgnoreCase(targetFolderKey)) {
                        targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                    } else if (DataManager.FOLDER_SAFETUBE_MUSIC.equalsIgnoreCase(targetFolderKey)) {
                        targetDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SafeTube");
                    } else if (DataManager.FOLDER_SAFETUBE_VIDEOS.equalsIgnoreCase(targetFolderKey)) {
                        targetDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "SafeTube");
                    } else {
                        targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    }

                    if (!targetDir.exists()) {
                        targetDir.mkdirs();
                    }

                    File targetFile = new File(targetDir, fileName);

                    URL url = new URL(downloadUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", USER_AGENT);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    conn.setInstanceFollowRedirects(true);

                    int respCode = conn.getResponseCode();
                    if (respCode >= 300 && respCode < 400) {
                        String newUrl = conn.getHeaderField("Location");
                        conn.disconnect();
                        conn = (HttpURLConnection) new URL(newUrl).openConnection();
                        conn.setRequestProperty("User-Agent", USER_AGENT);
                    }

                    in = conn.getInputStream();
                    out = new FileOutputStream(targetFile);

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.flush();

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "הקובץ ירד בהצלחה ונשמר בתיקייה: " + targetFolderKey, Toast.LENGTH_LONG).show();
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "שגיאה בשמירת הקובץ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                } finally {
                    try { if (in != null) in.close(); } catch (Exception ignored) {}
                    try { if (out != null) out.close(); } catch (Exception ignored) {}
                    if (conn != null) conn.disconnect();
                }
            }
        });
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
