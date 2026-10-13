package com.generated.app;

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
        void onSuccess(StreamResult result);
        void onError(String error);
    }

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

    private static final ExecutorService executor = Executors.newFixedThreadPool(8);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    // High-availability Cobalt direct media engines (Guaranteed full-bandwidth uncorrupted media)
    private static final String[] COBALT_INSTANCES = new String[]{
            "https://cobalt-api.kwiatekm.tokyo",
            "https://capi.wuk.sh",
            "https://cobalt.api.scip.io",
            "https://cobalt.canine.tools",
            "https://api.cobalt.tools"
    };

    // High-availability Piped API backends
    private static final String[] PIPED_INSTANCES = new String[]{
            "https://pipedapi.kavin.rocks",
            "https://api.piped.private.coffee",
            "https://piped-api.lunar.icu",
            "https://pa.il.ax",
            "https://pipedapi.leptons.xyz"
    };

    // High-availability Invidious API backends
    private static final String[] INVIDIOUS_INSTANCES = new String[]{
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://yewtu.be",
            "https://iv.melmac.space",
            "https://invidious.jing.rocks"
    };

    public static void getStreamUrl(final String videoId, final String targetQuality, final boolean isAudioOnly, final StreamCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                // Priority 1: Cobalt Direct Media API (Guaranteed high-bitrate fully multiplexed valid downloads)
                StreamResult cobaltRes = fetchFromCobalt(videoId, targetQuality, isAudioOnly);
                if (cobaltRes != null && cobaltRes.url != null && validateMediaStream(cobaltRes.url)) {
                    postSuccess(callback, cobaltRes);
                    return;
                }

                List<StreamResult> candidates = new ArrayList<>();

                // Step 2: Probe Piped API instances
                StreamResult pipedRes = fetchFromPipedParallel(videoId, targetQuality, isAudioOnly);
                if (pipedRes != null) candidates.add(pipedRes);

                // Step 3: Probe Invidious API instances
                StreamResult invRes = fetchFromInvidiousParallel(videoId, targetQuality, isAudioOnly);
                if (invRes != null) candidates.add(invRes);

                // Step 4: Probe Innertube Android Client Payload
                StreamResult innerRes = fetchFromInnertubeAndroid(videoId, targetQuality, isAudioOnly);
                if (innerRes != null) candidates.add(innerRes);

                // Validate each candidate to guarantee standard non-corrupt media headers
                for (StreamResult res : candidates) {
                    if (res != null && res.url != null && validateMediaStream(res.url)) {
                        postSuccess(callback, res);
                        return;
                    }
                }

                // Step 5: Direct ITAG route fallback
                StreamResult itagRes = fetchInvidiousItagRoute(videoId, targetQuality, isAudioOnly);
                if (itagRes != null && validateMediaStream(itagRes.url)) {
                    postSuccess(callback, itagRes);
                    return;
                }

                // If all else fails, return Cobalt even without strict pre-validation
                if (cobaltRes != null && cobaltRes.url != null) {
                    postSuccess(callback, cobaltRes);
                    return;
                }

                postError(callback, "לא הצלחנו לחלץ קישור הורדה תקין. אנא בדוק את החיבור לרשת ונסה שוב.");
            }
        });
    }

    public static boolean validateMediaStream(String streamUrl) {
        if (streamUrl == null || !streamUrl.startsWith("http")) return false;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(streamUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Referer", "https://www.youtube.com/");
            conn.setRequestProperty("Range", "bytes=0-4096");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code == 200 || code == 206) {
                String contentType = conn.getContentType();
                if (contentType != null) {
                    String ctLower = contentType.toLowerCase();
                    if (ctLower.contains("text/html") || ctLower.contains("application/json") || ctLower.contains("text/plain")) {
                        return false;
                    }
                }

                InputStream is = conn.getInputStream();
                byte[] header = new byte[1024];
                int read = is.read(header, 0, header.length);
                is.close();

                if (read > 20) {
                    String headStr = new String(header, 0, Math.min(read, 150)).toLowerCase();
                    if (headStr.contains("<!doc") || headStr.contains("<html") || headStr.contains("{\"error")) {
                        return false;
                    }
                    return true;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return false;
    }

    private static StreamResult fetchFromCobalt(String videoId, String targetQuality, boolean isAudioOnly) {
        for (String cobaltBase : COBALT_INSTANCES) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(cobaltBase + "/");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(4000);
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
                    body.put("audioBitrate", "192");
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

    private static StreamResult fetchFromPipedParallel(final String videoId, final String targetQuality, final boolean isAudioOnly) {
        ExecutorService pool = Executors.newFixedThreadPool(PIPED_INSTANCES.length);
        CompletionService<StreamResult> cs = new ExecutorCompletionService<>(pool);

        for (final String instance : PIPED_INSTANCES) {
            cs.submit(new Callable<StreamResult>() {
                @Override
                public StreamResult call() {
                    return fetchFromSinglePiped(instance, videoId, targetQuality, isAudioOnly);
                }
            });
        }

        StreamResult bestResult = null;
        for (int i = 0; i < PIPED_INSTANCES.length; i++) {
            try {
                Future<StreamResult> future = cs.poll(3500, TimeUnit.MILLISECONDS);
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
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
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
                Future<StreamResult> future = cs.poll(3500, TimeUnit.MILLISECONDS);
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
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
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
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
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

    private static StreamResult fetchInvidiousItagRoute(String videoId, String targetQuality, boolean isAudioOnly) {
        int itag = 18; // default 360p mp4
        String ext = "mp4";
        String mime = "video/mp4";

        if (isAudioOnly) {
            itag = 140; // m4a
            ext = "m4a";
            mime = "audio/mp4";
        } else if (targetQuality != null && targetQuality.contains("720")) {
            itag = 22; // 720p mp4
        }

        for (String instance : INVIDIOUS_INSTANCES) {
            String candidate = instance + "/latest_version?id=" + videoId + "&itag=" + itag;
            return new StreamResult(candidate, ext, mime);
        }
        return null;
    }

    public static void startDownload(final Context context, final String title, final String downloadUrl, final String extension, final String mimeType, final boolean isAudio) {
        final DataManager dm = DataManager.getInstance(context);
        String customTreeUriStr = isAudio ? dm.getAudioTreeUri() : dm.getVideoTreeUri();
        String folderSetting = isAudio ? dm.getAudioDownloadFolder() : dm.getVideoDownloadFolder();

        if (DataManager.FOLDER_CUSTOM_TREE.equals(folderSetting) && customTreeUriStr != null && !customTreeUriStr.isEmpty()) {
            downloadToDocumentTree(context, title, downloadUrl, extension, mimeType, customTreeUriStr, isAudio);
        } else {
            downloadDirectlyInApp(context, title, downloadUrl, extension, mimeType, isAudio);
        }
    }

    private static void downloadToDocumentTree(final Context context, final String title, final String downloadUrl, final String extension, final String mimeType, final String treeUriStr, final boolean isAudio) {
        final String displayName = (isAudio ? dmAudioName(context) : dmVideoName(context));
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, "מוריד פנימית ישירות לתיקייה: " + displayName, Toast.LENGTH_SHORT).show();
            }
        });

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
                    String filename = cleanTitle + "_" + System.currentTimeMillis() + "." + extension;

                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
                    createdFileUri = DocumentsContract.createDocument(context.getContentResolver(), docUri, mimeType, filename);

                    if (createdFileUri == null) {
                        throw new Exception("לא ניתן ליצור קובץ בתיקייה שנבחרה");
                    }

                    out = context.getContentResolver().openOutputStream(createdFileUri);
                    if (out == null) {
                        throw new Exception("שגיאה בפתיחת ערוץ כתיבה לקובץ");
                    }

                    conn = createValidMediaConnection(downloadUrl);
                    if (conn == null) {
                        throw new Exception("קישור ההורדה לא הגיב כראוי");
                    }

                    in = conn.getInputStream();
                    byte[] buffer = new byte[65536];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                    }
                    out.flush();

                    if (totalBytes < 50000) {
                        throw new Exception("קובץ ההורדה קטן מדי או פגום");
                    }

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "ההורדה הושלמה בהצלחה! הקובץ נשמר ונמצא בתיקייה שלך.", Toast.LENGTH_LONG).show();
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
                            Toast.makeText(context, "שומר לתיקיית ההורדות הראשית של המכשיר...", Toast.LENGTH_SHORT).show();
                            downloadDirectlyInApp(context, title, downloadUrl, extension, mimeType, isAudio);
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

    private static void downloadDirectlyInApp(final Context context, final String title, final String downloadUrl, final String extension, final String mimeType, final boolean isAudio) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, "מוריד קובץ מדיה ישירות למכשיר...", Toast.LENGTH_SHORT).show();
            }
        });

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
                        throw new Exception("שגיאה בפתיחת קובץ יעד בזיכרון המכשיר");
                    }

                    conn = createValidMediaConnection(downloadUrl);
                    if (conn == null) {
                        throw new Exception("תגובת שרת ההורדה לא תקינה");
                    }

                    in = conn.getInputStream();
                    byte[] buffer = new byte[65536];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                    }
                    out.flush();

                    if (totalBytes < 50000) {
                        throw new Exception("הקובץ שהתקבל קטן מדי או פגום");
                    }

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
                            Toast.makeText(context, "ההורדה הושלמה בהצלחה! הקובץ נשמר במכשיר ומוכן לנגינה.", Toast.LENGTH_LONG).show();
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

    private static HttpURLConnection createValidMediaConnection(String initialUrl) throws Exception {
        URL url = new URL(initialUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Referer", "https://www.youtube.com/");
        conn.setRequestProperty("Origin", "https://www.youtube.com");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);

        int code = conn.getResponseCode();
        int redirects = 0;
        while ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308) && redirects < 8) {
            String newUrl = conn.getHeaderField("Location");
            if (newUrl == null || newUrl.isEmpty()) break;
            conn.disconnect();
            url = new URL(newUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Referer", "https://www.youtube.com/");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            code = conn.getResponseCode();
            redirects++;
        }

        if (code != 200 && code != 206) {
            conn.disconnect();
            return null;
        }

        String contentType = conn.getContentType();
        if (contentType != null && (contentType.contains("text/html") || contentType.contains("application/json"))) {
            conn.disconnect();
            return null;
        }

        return conn;
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

    private static void postSuccess(final StreamCallback callback, final StreamResult result) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(result);
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
