package com.generated.app;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
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
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    // Resilient, updated public mirror APIs
    private static final String[] COBALT_INSTANCES = new String[]{
            "https://api.cobalt.tools",
            "https://cobalt-api.kwiatekm.tokyo",
            "https://co.wuk.sh"
    };

    private static final String[] INVIDIOUS_INSTANCES = new String[]{
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://iv.melmac.space",
            "https://yt.artemislena.eu",
            "https://invidious.jing.rocks"
    };

    private static final String[] PIPED_INSTANCES = new String[]{
            "https://pipedapi.kavin.rocks",
            "https://api.piped.private.coffee",
            "https://piped-api.lunar.icu",
            "https://api.piped.privacydev.net"
    };

    public static void getStreamUrl(final String videoId, final String targetQuality, final boolean isAudioOnly, final StreamCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                // Method 1: Innertube Android Client (Direct Google Video links with no signature encryption)
                StreamResult innertubeResult = fetchFromInnertube(videoId, targetQuality, isAudioOnly);
                if (innertubeResult != null) {
                    postSuccess(callback, innertubeResult.url, innertubeResult.extension, innertubeResult.mimeType);
                    return;
                }

                // Method 2: Cobalt API (High Quality direct download CDN link)
                StreamResult cobaltResult = fetchFromCobalt(videoId, targetQuality, isAudioOnly);
                if (cobaltResult != null) {
                    postSuccess(callback, cobaltResult.url, cobaltResult.extension, cobaltResult.mimeType);
                    return;
                }

                // Method 3: Invidious Instances
                StreamResult invidiousResult = fetchFromInvidious(videoId, targetQuality, isAudioOnly);
                if (invidiousResult != null) {
                    postSuccess(callback, invidiousResult.url, invidiousResult.extension, invidiousResult.mimeType);
                    return;
                }

                // Method 4: Piped Instances
                StreamResult pipedResult = fetchFromPiped(videoId, targetQuality, isAudioOnly);
                if (pipedResult != null) {
                    postSuccess(callback, pipedResult.url, pipedResult.extension, pipedResult.mimeType);
                    return;
                }

                // Method 5: Direct Invidious Proxy Stream Fallback
                for (String host : INVIDIOUS_INSTANCES) {
                    try {
                        String directUrl;
                        String ext;
                        String mime;
                        if (isAudioOnly) {
                            directUrl = host + "/latest_version?id=" + videoId + "&itag=140";
                            ext = "m4a";
                            mime = "audio/mp4";
                        } else {
                            String itag = "22"; // 720p
                            if (targetQuality != null && targetQuality.contains("360")) {
                                itag = "18"; // 360p
                            }
                            directUrl = host + "/latest_version?id=" + videoId + "&itag=" + itag;
                            ext = "mp4";
                            mime = "video/mp4";
                        }
                        postSuccess(callback, directUrl, ext, mime);
                        return;
                    } catch (Exception ignored) {}
                }

                postError(callback, "לא הצלחנו לחלץ קישור מדיה עבור סרטון זה.");
            }
        });
    }

    private static class StreamResult {
        String url;
        String extension;
        String mimeType;

        StreamResult(String url, String extension, String mimeType) {
            this.url = url;
            this.extension = extension;
            this.mimeType = mimeType;
        }
    }

    private static StreamResult fetchFromInnertube(String videoId, String targetQuality, boolean isAudioOnly) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.29.35 (Linux; U; Android 11) gzip");
            conn.setRequestProperty("X-YouTube-Client-Name", "3");
            conn.setRequestProperty("X-YouTube-Client-Version", "19.29.35");

            JSONObject payload = new JSONObject();
            payload.put("videoId", videoId);

            JSONObject context = new JSONObject();
            JSONObject client = new JSONObject();
            client.put("clientName", "ANDROID");
            client.put("clientVersion", "19.29.35");
            client.put("androidSdkVersion", 30);
            client.put("hl", "en");
            client.put("gl", "US");
            context.put("client", client);
            payload.put("context", context);

            OutputStream os = conn.getOutputStream();
            os.write(payload.toString().getBytes("UTF-8"));
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
                JSONObject streamingData = json.optJSONObject("streamingData");
                if (streamingData != null) {
                    if (isAudioOnly) {
                        JSONArray adaptive = streamingData.optJSONArray("adaptiveFormats");
                        if (adaptive != null) {
                            String bestAudioUrl = null;
                            int bestBitrate = 0;
                            String bestExt = "m4a";
                            String bestMime = "audio/mp4";

                            for (int i = 0; i < adaptive.length(); i++) {
                                JSONObject fmt = adaptive.getJSONObject(i);
                                String mime = fmt.optString("mimeType", "");
                                if (mime.startsWith("audio/")) {
                                    String directUrl = fmt.optString("url", "");
                                    int bitrate = fmt.optInt("bitrate", 0);
                                    if (!directUrl.isEmpty() && bitrate >= bestBitrate) {
                                        bestBitrate = bitrate;
                                        bestAudioUrl = directUrl;
                                        bestMime = mime.contains(";") ? mime.substring(0, mime.indexOf(";")).trim() : mime;
                                        bestExt = mime.contains("mp4") || mime.contains("m4a") ? "m4a" : "mp3";
                                    }
                                }
                            }
                            if (bestAudioUrl != null) {
                                return new StreamResult(bestAudioUrl, bestExt, bestMime);
                            }
                        }
                    } else {
                        // Standard formats (video with combined audio)
                        JSONArray formats = streamingData.optJSONArray("formats");
                        if (formats != null && formats.length() > 0) {
                            for (int i = 0; i < formats.length(); i++) {
                                JSONObject fmt = formats.getJSONObject(i);
                                String qLabel = fmt.optString("qualityLabel", "");
                                String directUrl = fmt.optString("url", "");
                                if (!directUrl.isEmpty()) {
                                    if (targetQuality != null && qLabel.contains(targetQuality)) {
                                        return new StreamResult(directUrl, "mp4", "video/mp4");
                                    }
                                }
                            }
                            // Default to first combined format
                            JSONObject first = formats.getJSONObject(0);
                            String directUrl = first.optString("url", "");
                            if (!directUrl.isEmpty()) {
                                return new StreamResult(directUrl, "mp4", "video/mp4");
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static StreamResult fetchFromCobalt(String videoId, String targetQuality, boolean isAudioOnly) {
        for (String cobaltBase : COBALT_INSTANCES) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(cobaltBase + "/");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", USER_AGENT);

                JSONObject body = new JSONObject();
                body.put("url", "https://www.youtube.com/watch?v=" + videoId);
                if (isAudioOnly) {
                    body.put("downloadMode", "audio");
                    body.put("audioFormat", "mp3");
                } else {
                    body.put("downloadMode", "auto");
                    String vQuality = "720";
                    if (targetQuality != null && targetQuality.contains("1080")) vQuality = "1080";
                    else if (targetQuality != null && targetQuality.contains("360")) vQuality = "360";
                    body.put("videoQuality", vQuality);
                }

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                int respCode = conn.getResponseCode();
                if (respCode == HttpURLConnection.HTTP_OK || respCode == 201) {
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
                        return new StreamResult(stream, ext, mime);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return null;
    }

    private static StreamResult fetchFromInvidious(String videoId, String targetQuality, boolean isAudioOnly) {
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
                            for (int i = 0; i < adaptive.length(); i++) {
                                JSONObject fmt = adaptive.getJSONObject(i);
                                String type = fmt.optString("type", "");
                                if (type.contains("audio/")) {
                                    String streamUrl = fmt.optString("url", "");
                                    if (!streamUrl.isEmpty()) {
                                        String ext = (type.contains("mp4") || type.contains("m4a")) ? "m4a" : "mp3";
                                        return new StreamResult(streamUrl, ext, "audio/mp4");
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
                                if (targetQuality != null && qLabel.contains(targetQuality)) {
                                    String streamUrl = fmt.optString("url", "");
                                    if (!streamUrl.isEmpty()) {
                                        return new StreamResult(streamUrl, "mp4", "video/mp4");
                                    }
                                }
                            }

                            JSONObject firstFmt = formatStreams.getJSONObject(0);
                            String streamUrl = firstFmt.optString("url", "");
                            if (!streamUrl.isEmpty()) {
                                return new StreamResult(streamUrl, "mp4", "video/mp4");
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return null;
    }

    private static StreamResult fetchFromPiped(String videoId, String targetQuality, boolean isAudioOnly) {
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
                            JSONObject sel = audioStreams.getJSONObject(0);
                            String aUrl = sel.optString("url", "");
                            String mime = sel.optString("mimeType", "audio/mp4");
                            String ext = (mime.contains("mp4") || mime.contains("m4a")) ? "m4a" : "mp3";
                            if (!aUrl.isEmpty()) {
                                return new StreamResult(aUrl, ext, mime);
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
                                        return new StreamResult(matchedUrl, "mp4", mime);
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
        return null;
    }

    public static void startDownload(final Context context, final String title, final String downloadUrl, final String extension, final String mimeType, final boolean isAudio) {
        final DataManager dm = DataManager.getInstance(context);

        String customTreeUriStr = isAudio ? dm.getAudioTreeUri() : dm.getVideoTreeUri();
        String folderSetting = isAudio ? dm.getAudioDownloadFolder() : dm.getVideoDownloadFolder();

        // 1. If user selected a custom directory via Storage Access Framework (ACTION_OPEN_DOCUMENT_TREE)
        if (DataManager.FOLDER_CUSTOM_TREE.equals(folderSetting) && customTreeUriStr != null && !customTreeUriStr.isEmpty()) {
            downloadToDocumentTree(context, title, downloadUrl, extension, mimeType, customTreeUriStr, isAudio);
            return;
        }

        // 2. Standard Public Storage (DownloadManager or fallback to direct file stream)
        downloadToPublicStorage(context, title, downloadUrl, extension, mimeType, isAudio, folderSetting);
    }

    private static void downloadToDocumentTree(final Context context, final String title, final String downloadUrl, final String extension, final String mimeType, final String treeUriStr, final boolean isAudio) {
        final String displayName = (isAudio ? dmAudioName(context) : dmVideoName(context));
        Toast.makeText(context, "מתחיל הורדה לתיקייה שבחרת: " + displayName, Toast.LENGTH_SHORT).show();

        executor.submit(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                InputStream in = null;
                OutputStream out = null;
                try {
                    Uri treeUri = Uri.parse(treeUriStr);
                    String cleanTitle = sanitizeFilename(title);
                    String filename = cleanTitle + "." + extension;

                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
                    Uri createdFileUri = DocumentsContract.createDocument(context.getContentResolver(), docUri, mimeType, filename);

                    if (createdFileUri == null) {
                        throw new Exception("לא ניתן ליצור קובץ בתיקייה שנבחרה");
                    }

                    out = context.getContentResolver().openOutputStream(createdFileUri);
                    if (out == null) {
                        throw new Exception("שגיאה בפתיחת ערוץ כתיבה לקובץ");
                    }

                    URL url = new URL(downloadUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", USER_AGENT);
                    conn.setRequestProperty("Referer", "https://www.youtube.com/");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);
                    conn.setInstanceFollowRedirects(true);

                    int respCode = conn.getResponseCode();
                    if (respCode >= 300 && respCode < 400) {
                        String newLoc = conn.getHeaderField("Location");
                        conn.disconnect();
                        conn = (HttpURLConnection) new URL(newLoc).openConnection();
                        conn.setRequestProperty("User-Agent", USER_AGENT);
                    }

                    in = conn.getInputStream();
                    byte[] buffer = new byte[16384];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.flush();

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "ההורדה הושלמה בהצלחה לתיקייה: " + displayName, Toast.LENGTH_LONG).show();
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "שגיאה בהורדה לתיקייה המותאמת: " + e.getMessage() + ". מנסה שמירה רגילה...", Toast.LENGTH_LONG).show();
                            // Fallback to regular download
                            downloadToPublicStorage(context, title, downloadUrl, extension, mimeType, isAudio, DataManager.FOLDER_DEFAULT_DOWNLOADS);
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

    private static void downloadToPublicStorage(final Context context, final String title, final String downloadUrl, final String extension, final String mimeType, final boolean isAudio, final String targetFolderKey) {
        // Try Android DownloadManager first
        try {
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager != null) {
                Uri uri = Uri.parse(downloadUrl);
                DownloadManager.Request request = new DownloadManager.Request(uri);

                String cleanTitle = sanitizeFilename(title);
                String filename = cleanTitle + "." + extension;

                request.setTitle(title != null ? title : (isAudio ? "הורדת שיר" : "הורדת סרטון"));
                request.setDescription("יוטיוב בטוח");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
                request.addRequestHeader("User-Agent", USER_AGENT);
                request.addRequestHeader("Referer", "https://www.youtube.com/");

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
                Toast.makeText(context, (isAudio ? "הורדת השיר החלה: " : "הורדת הסרטון החלה: ") + filename, Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // Direct Stream Saver Fallback:
        downloadDirectlyToFile(context, title, downloadUrl, extension, targetFolderKey);
    }

    private static void downloadDirectlyToFile(final Context context, final String title, final String downloadUrl, final String extension, final String targetFolderKey) {
        Toast.makeText(context, "מתחיל הורדה ישירה למכשיר...", Toast.LENGTH_SHORT).show();

        executor.submit(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                InputStream in = null;
                FileOutputStream out = null;
                try {
                    String cleanTitle = sanitizeFilename(title);
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
                    conn.setRequestProperty("Referer", "https://www.youtube.com/");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);
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

                    byte[] buffer = new byte[16384];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.flush();

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "הקובץ ירד בהצלחה ונשמר!", Toast.LENGTH_LONG).show();
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

    private static String sanitizeFilename(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "media_" + System.currentTimeMillis();
        }
        String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (clean.length() > 60) {
            clean = clean.substring(0, 60);
        }
        return clean;
    }

    private static String dmAudioName(Context ctx) {
        String n = DataManager.getInstance(ctx).getAudioTreeName();
        return n != null ? n : "תיקיית שמע מותאמת";
    }

    private static String dmVideoName(Context ctx) {
        String n = DataManager.getInstance(ctx).getVideoTreeName();
        return n != null ? n : "תיקיית סרטונים מותאמת";
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
