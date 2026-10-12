package com.generated.app;

import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class DownloadHelper {

    public interface StreamCallback {
        void onSuccess(String streamUrl, String extension, String mimeType);
        void onError(String error);
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(8);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36";

    // 1. High-availability NewPipe / Piped API backends (Official NewPipe routing)
    private static final String[] NEWPIPE_PIPED_INSTANCES = new String[]{
            "https://pipedapi.kavin.rocks",
            "https://api.piped.private.coffee",
            "https://piped-api.lunar.icu",
            "https://pa.il.ax",
            "https://pipedapi.leptons.xyz",
            "https://pipedapi.tokhmi.xyz"
    };

    // 2. High-availability Invidious API backends
    private static final String[] INVIDIOUS_INSTANCES = new String[]{
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://yewtu.be",
            "https://iv.melmac.space",
            "https://invidious.jing.rocks",
            "https://invidious.drgns.space",
            "https://invidious.projectsegfau.lt"
    };

    // 3. Cobalt high-speed direct media engines
    private static final String[] COBALT_INSTANCES = new String[]{
            "https://cobalt-api.kwiatekm.tokyo",
            "https://capi.wuk.sh",
            "https://cobalt.api.scip.io",
            "https://cobalt.canine.tools",
            "https://api.cobalt.tools"
    };

    public static class StreamResult {
        public String url;
        public String extension;
        public String mimeType;

        public StreamResult(String url, String extension, String mimeType) {
            this.url = url;
            this.extension = extension;
            this.mimeType = mimeType;
        }
    }

    public static void getStreamUrl(final String videoId, final String targetQuality, final boolean isAudioOnly, final StreamCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                // Step 1: Parallel probe on NewPipe / Piped API instances
                StreamResult result = fetchFromNewPipePipedParallel(videoId, targetQuality, isAudioOnly);
                if (result != null && isStreamUrlPlayable(result.url)) {
                    postSuccess(callback, result.url, result.extension, result.mimeType);
                    return;
                }

                // Step 2: Parallel probe on Invidious instances (Direct media & latest_version proxy streams)
                result = fetchFromInvidiousParallel(videoId, targetQuality, isAudioOnly);
                if (result != null && isStreamUrlPlayable(result.url)) {
                    postSuccess(callback, result.url, result.extension, result.mimeType);
                    return;
                }

                // Step 3: Fast Innertube Android Client (Native YouTube Android app client payload)
                result = fetchFromInnertubeAndroid(videoId, targetQuality, isAudioOnly);
                if (result != null && isStreamUrlPlayable(result.url)) {
                    postSuccess(callback, result.url, result.extension, result.mimeType);
                    return;
                }

                // Step 4: Cobalt high-speed API
                result = fetchFromCobalt(videoId, targetQuality, isAudioOnly);
                if (result != null && isStreamUrlPlayable(result.url)) {
                    postSuccess(callback, result.url, result.extension, result.mimeType);
                    return;
                }

                // Step 5: Invidious Direct ITAG Stream Route (Guaranteed fallback for YouTube videos)
                result = fetchInvidiousItagRoute(videoId, targetQuality, isAudioOnly);
                if (result != null) {
                    postSuccess(callback, result.url, result.extension, result.mimeType);
                    return;
                }

                postError(callback, "לא הצלחנו לחלץ קישור הורדה ישיר לסרטון זה. אנא נסה שוב בעוד רגע.");
            }
        });
    }

    private static StreamResult fetchFromNewPipePipedParallel(final String videoId, final String targetQuality, final boolean isAudioOnly) {
        ExecutorService pool = Executors.newFixedThreadPool(NEWPIPE_PIPED_INSTANCES.length);
        CompletionService<StreamResult> cs = new ExecutorCompletionService<>(pool);

        for (final String instance : NEWPIPE_PIPED_INSTANCES) {
            cs.submit(new Callable<StreamResult>() {
                @Override
                public StreamResult call() {
                    return fetchFromSinglePiped(instance, videoId, targetQuality, isAudioOnly);
                }
            });
        }

        StreamResult bestResult = null;
        for (int i = 0; i < NEWPIPE_PIPED_INSTANCES.length; i++) {
            try {
                Future<StreamResult> future = cs.poll(4000, TimeUnit.MILLISECONDS);
                if (future != null) {
                    StreamResult r = future.get();
                    if (r != null && r.url != null && !r.url.isEmpty()) {
                        bestResult = r;
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
        pool.shutdownNow();
        return bestResult;
    }

    private static StreamResult fetchFromSinglePiped(String instance, String videoId, String targetQuality, boolean isAudioOnly) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(instance + "/streams/" + videoId);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3500);
            conn.setReadTimeout(3500);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());

                if (isAudioOnly) {
                    JSONArray audioStreams = json.optJSONArray("audioStreams");
                    if (audioStreams != null && audioStreams.length() > 0) {
                        for (int i = 0; i < audioStreams.length(); i++) {
                            JSONObject stream = audioStreams.getJSONObject(i);
                            String aUrl = stream.optString("url", "");
                            String mime = stream.optString("mimeType", "audio/mp4");
                            String ext = (mime.contains("mp4") || mime.contains("m4a")) ? "m4a" : "mp3";
                            if (!aUrl.isEmpty()) {
                                return new StreamResult(aUrl, ext, mime);
                            }
                        }
                    }
                } else {
                    JSONArray videoStreams = json.optJSONArray("videoStreams");
                    if (videoStreams != null && videoStreams.length() > 0) {
                        // First pass: try to match quality
                        for (int i = 0; i < videoStreams.length(); i++) {
                            JSONObject stream = videoStreams.getJSONObject(i);
                            if (!stream.optBoolean("videoOnly", false)) {
                                String vUrl = stream.optString("url", "");
                                String q = stream.optString("quality", "");
                                String mime = stream.optString("mimeType", "video/mp4");
                                if (!vUrl.isEmpty() && targetQuality != null && q.contains(targetQuality)) {
                                    return new StreamResult(vUrl, "mp4", mime);
                                }
                            }
                        }
                        // Second pass: any valid muxed stream
                        for (int i = 0; i < videoStreams.length(); i++) {
                            JSONObject stream = videoStreams.getJSONObject(i);
                            if (!stream.optBoolean("videoOnly", false)) {
                                String vUrl = stream.optString("url", "");
                                String mime = stream.optString("mimeType", "video/mp4");
                                if (!vUrl.isEmpty()) {
                                    return new StreamResult(vUrl, "mp4", mime);
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
        return null;
    }

    private static StreamResult fetchFromInvidiousParallel(final String videoId, final String targetQuality, final boolean isAudioOnly) {
        ExecutorService pool = Executors.newFixedThreadPool(INVIDIOUS_INSTANCES.length);
        CompletionService<StreamResult> cs = new ExecutorCompletionService<>(pool);

        for (final String instance : INVIDIOUS_INSTANCES) {
            cs.submit(new Callable<StreamResult>() {
                @Override
                public StreamResult call() {
                    return fetchFromSingleInvidious(instance, videoId, targetQuality, isAudioOnly);
                }
            });
        }

        StreamResult bestResult = null;
        for (int i = 0; i < INVIDIOUS_INSTANCES.length; i++) {
            try {
                Future<StreamResult> future = cs.poll(4000, TimeUnit.MILLISECONDS);
                if (future != null) {
                    StreamResult r = future.get();
                    if (r != null && r.url != null && !r.url.isEmpty()) {
                        bestResult = r;
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
        pool.shutdownNow();
        return bestResult;
    }

    private static StreamResult fetchFromSingleInvidious(String instance, String videoId, String targetQuality, boolean isAudioOnly) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(instance + "/api/v1/videos/" + videoId);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3500);
            conn.setReadTimeout(3500);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());

                if (isAudioOnly) {
                    JSONArray adaptive = json.optJSONArray("adaptiveFormats");
                    if (adaptive != null && adaptive.length() > 0) {
                        for (int i = 0; i < adaptive.length(); i++) {
                            JSONObject item = adaptive.getJSONObject(i);
                            String type = item.optString("type", "");
                            String directUrl = item.optString("url", "");
                            if (type.startsWith("audio/") && !directUrl.isEmpty()) {
                                if (directUrl.startsWith("/")) directUrl = instance + directUrl;
                                String ext = (type.contains("mp4") || type.contains("m4a")) ? "m4a" : "mp3";
                                return new StreamResult(directUrl, ext, "audio/mp4");
                            }
                        }
                    }
                } else {
                    JSONArray formatStreams = json.optJSONArray("formatStreams");
                    if (formatStreams != null && formatStreams.length() > 0) {
                        for (int i = 0; i < formatStreams.length(); i++) {
                            JSONObject item = formatStreams.getJSONObject(i);
                            String q = item.optString("qualityLabel", "");
                            String directUrl = item.optString("url", "");
                            if (!directUrl.isEmpty() && targetQuality != null && q.contains(targetQuality)) {
                                if (directUrl.startsWith("/")) directUrl = instance + directUrl;
                                return new StreamResult(directUrl, "mp4", "video/mp4");
                            }
                        }
                        JSONObject first = formatStreams.getJSONObject(0);
                        String directUrl = first.optString("url", "");
                        if (!directUrl.isEmpty()) {
                            if (directUrl.startsWith("/")) directUrl = instance + directUrl;
                            return new StreamResult(directUrl, "mp4", "video/mp4");
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

    private static StreamResult fetchFromInnertubeAndroid(String videoId, String targetQuality, boolean isAudioOnly) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(4500);
            conn.setReadTimeout(4500);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "com.google.android.youtube/19.08.35 (Linux; U; Android 14; en_US) gzip");
            conn.setRequestProperty("X-YouTube-Client-Name", "3");
            conn.setRequestProperty("X-YouTube-Client-Version", "19.08.35");

            JSONObject payload = new JSONObject();
            payload.put("videoId", videoId);

            JSONObject context = new JSONObject();
            JSONObject client = new JSONObject();
            client.put("clientName", "ANDROID");
            client.put("clientVersion", "19.08.35");
            client.put("androidSdkVersion", 34);
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
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                JSONObject streamingData = json.optJSONObject("streamingData");
                if (streamingData != null) {
                    if (isAudioOnly) {
                        JSONArray adaptive = streamingData.optJSONArray("adaptiveFormats");
                        if (adaptive != null) {
                            for (int i = 0; i < adaptive.length(); i++) {
                                JSONObject fmt = adaptive.getJSONObject(i);
                                String mime = fmt.optString("mimeType", "");
                                String directUrl = fmt.optString("url", "");
                                if (!directUrl.isEmpty() && mime.startsWith("audio/")) {
                                    String ext = (mime.contains("mp4") || mime.contains("m4a")) ? "m4a" : "mp3";
                                    return new StreamResult(directUrl, ext, "audio/mp4");
                                }
                            }
                        }
                    } else {
                        JSONArray formats = streamingData.optJSONArray("formats");
                        if (formats != null && formats.length() > 0) {
                            for (int i = 0; i < formats.length(); i++) {
                                JSONObject fmt = formats.getJSONObject(i);
                                String qLabel = fmt.optString("qualityLabel", "");
                                String directUrl = fmt.optString("url", "");
                                if (!directUrl.isEmpty() && targetQuality != null && qLabel.contains(targetQuality)) {
                                    return new StreamResult(directUrl, "mp4", "video/mp4");
                                }
                            }
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
                conn.setConnectTimeout(4500);
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

                int code = conn.getResponseCode();
                if (code == 200 || code == 201) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject res = new JSONObject(sb.toString());
                    String stream = res.optString("url", "");
                    if (stream.isEmpty()) stream = res.optString("redirect", "");
                    if (stream.isEmpty() && res.has("picker")) {
                        JSONArray picker = res.optJSONArray("picker");
                        if (picker != null && picker.length() > 0) {
                            stream = picker.getJSONObject(0).optString("url", "");
                        }
                    }

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

    private static StreamResult fetchInvidiousItagRoute(String videoId, String targetQuality, boolean isAudioOnly) {
        int itag = 18; // default 360p mp4
        String ext = "mp4";
        String mime = "video/mp4";

        if (isAudioOnly) {
            itag = 140; // m4a 128k
            ext = "m4a";
            mime = "audio/mp4";
        } else if (targetQuality != null && targetQuality.contains("720")) {
            itag = 22; // 720p mp4
        }

        for (String instance : INVIDIOUS_INSTANCES) {
            String candidate = instance + "/latest_version?id=" + videoId + "&itag=" + itag;
            if (isStreamUrlPlayable(candidate)) {
                return new StreamResult(candidate, ext, mime);
            }
        }
        return null;
    }

    private static boolean isStreamUrlPlayable(String testUrl) {
        if (testUrl == null || !testUrl.startsWith("http")) return false;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(testUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Range", "bytes=0-1024");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code >= 200 && code < 400) {
                String cType = conn.getContentType();
                if (cType != null && (cType.contains("text/html") || cType.contains("application/json"))) {
                    return false;
                }
                return true;
            }
        } catch (Exception ignored) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
        return false;
    }

    public static void startDownload(final Context context, final String title, final String downloadUrl, final String extension, final String mimeType, final boolean isAudio) {
        final DataManager dm = DataManager.getInstance(context);
        String customTreeUriStr = isAudio ? dm.getAudioTreeUri() : dm.getVideoTreeUri();
        String folderSetting = isAudio ? dm.getAudioDownloadFolder() : dm.getVideoDownloadFolder();

        // 1. If user selected a custom device directory via SAF
        if (DataManager.FOLDER_CUSTOM_TREE.equals(folderSetting) && customTreeUriStr != null && !customTreeUriStr.isEmpty()) {
            downloadToDocumentTree(context, title, downloadUrl, extension, mimeType, customTreeUriStr, isAudio);
            return;
        }

        // 2. Standard direct DownloadManager enqueue
        try {
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager != null && downloadUrl.startsWith("http")) {
                String cleanTitle = sanitizeFilename(title);
                String fileName = cleanTitle + "_" + System.currentTimeMillis() + "." + extension;

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
                request.setTitle(cleanTitle);
                request.setDescription(isAudio ? "שיר שמע מיוטיוב בטוח" : "סרטון וידאו מיוטיוב בטוח");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.addRequestHeader("User-Agent", USER_AGENT);
                request.addRequestHeader("Referer", "https://www.youtube.com/");
                if (mimeType != null && !mimeType.isEmpty()) {
                    request.setMimeType(mimeType);
                }

                String destinationDir = Environment.DIRECTORY_DOWNLOADS;
                if (isAudio && DataManager.FOLDER_DEFAULT_MUSIC.equalsIgnoreCase(folderSetting)) {
                    destinationDir = Environment.DIRECTORY_MUSIC;
                } else if (!isAudio && DataManager.FOLDER_DEFAULT_MOVIES.equalsIgnoreCase(folderSetting)) {
                    destinationDir = Environment.DIRECTORY_MOVIES;
                }

                try {
                    request.setDestinationInExternalPublicDir(destinationDir, fileName);
                } catch (Exception e) {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                }

                downloadManager.enqueue(request);
                Toast.makeText(context, "ההורדה החלה ישירות ברקע! תוכל לראות את ההתקדמות בווילון ההתראות.", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception ignored) {}

        // 3. Fallback direct stream saver via MediaStore
        downloadDirectlyWithMediaStore(context, title, downloadUrl, extension, mimeType, isAudio, folderSetting);
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
                Uri createdFileUri = null;
                long totalBytes = 0;

                try {
                    Uri treeUri = Uri.parse(treeUriStr);
                    String cleanTitle = sanitizeFilename(title);
                    String filename = cleanTitle + "." + extension;

                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
                    createdFileUri = DocumentsContract.createDocument(context.getContentResolver(), docUri, mimeType, filename);

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
                    conn.setRequestProperty("Accept", "*/*");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setInstanceFollowRedirects(true);

                    int responseCode = conn.getResponseCode();
                    int redirectCount = 0;
                    while ((responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == 301 || responseCode == 302 || responseCode == 303 || responseCode == 307 || responseCode == 308) && redirectCount < 8) {
                        String newUrl = conn.getHeaderField("Location");
                        if (newUrl == null || newUrl.isEmpty()) break;
                        conn.disconnect();
                        url = new URL(newUrl);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestProperty("User-Agent", USER_AGENT);
                        conn.setRequestProperty("Accept", "*/*");
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(30000);
                        responseCode = conn.getResponseCode();
                        redirectCount++;
                    }

                    if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                        throw new Exception("תגובת שרת לא תקינה (" + responseCode + ")");
                    }

                    in = conn.getInputStream();
                    byte[] buffer = new byte[16384];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
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
                    if (createdFileUri != null) {
                        try { DocumentsContract.deleteDocument(context.getContentResolver(), createdFileUri); } catch (Exception ignored) {}
                    }
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "שומר להורדות המכשיר הראשיות...", Toast.LENGTH_SHORT).show();
                            downloadDirectlyWithMediaStore(context, title, downloadUrl, extension, mimeType, isAudio, DataManager.FOLDER_DEFAULT_DOWNLOADS);
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

    private static void downloadDirectlyWithMediaStore(final Context context, final String title, final String downloadUrl, final String extension, final String mimeType, final boolean isAudio, final String targetFolderKey) {
        Toast.makeText(context, "מתחיל בהורדה ישירה למכשיר...", Toast.LENGTH_SHORT).show();

        executor.submit(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                InputStream in = null;
                OutputStream out = null;
                Uri itemUri = null;
                File targetFile = null;
                long totalBytes = 0;

                try {
                    String cleanTitle = sanitizeFilename(title);
                    String fileName = cleanTitle + "_" + System.currentTimeMillis() + "." + extension;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType != null ? mimeType : (isAudio ? "audio/mpeg" : "video/mp4"));
                        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                        if (isAudio) {
                            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/SafeTube");
                            itemUri = context.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
                        } else {
                            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SafeTube");
                            itemUri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                        }

                        if (itemUri == null) {
                            itemUri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        }

                        if (itemUri != null) {
                            out = context.getContentResolver().openOutputStream(itemUri);
                        }
                    } else {
                        File targetDir;
                        if (isAudio) {
                            targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                        } else {
                            targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                        }
                        if (!targetDir.exists()) targetDir.mkdirs();
                        targetFile = new File(targetDir, fileName);
                        out = new FileOutputStream(targetFile);
                    }

                    if (out == null) {
                        throw new Exception("שגיאה בפתיחת קובץ יעד");
                    }

                    URL url = new URL(downloadUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", USER_AGENT);
                    conn.setRequestProperty("Accept", "*/*");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setInstanceFollowRedirects(true);

                    int responseCode = conn.getResponseCode();
                    int redirectCount = 0;
                    while ((responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == 301 || responseCode == 302 || responseCode == 303 || responseCode == 307 || responseCode == 308) && redirectCount < 8) {
                        String newUrl = conn.getHeaderField("Location");
                        if (newUrl == null || newUrl.isEmpty()) break;
                        conn.disconnect();
                        url = new URL(newUrl);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestProperty("User-Agent", USER_AGENT);
                        conn.setRequestProperty("Accept", "*/*");
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(30000);
                        responseCode = conn.getResponseCode();
                        redirectCount++;
                    }

                    if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                        throw new Exception("שגיאת שרת: " + responseCode);
                    }

                    in = conn.getInputStream();
                    byte[] buffer = new byte[16384];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                    }
                    out.flush();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && itemUri != null) {
                        ContentValues updateVals = new ContentValues();
                        updateVals.put(MediaStore.MediaColumns.IS_PENDING, 0);
                        context.getContentResolver().update(itemUri, updateVals, null, null);
                    } else if (targetFile != null && targetFile.exists()) {
                        MediaScannerConnection.scanFile(context, new String[]{targetFile.getAbsolutePath()}, null, null);
                    }

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "הקובץ ירד בהצלחה ונשמר במכשיר!", Toast.LENGTH_LONG).show();
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && itemUri != null) {
                        try { context.getContentResolver().delete(itemUri, null, null); } catch (Exception ignored) {}
                    } else if (targetFile != null && targetFile.exists()) {
                        try { targetFile.delete(); } catch (Exception ignored) {}
                    }

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "שגיאה בשמירת הקובץ: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
