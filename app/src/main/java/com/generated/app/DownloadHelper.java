package com.generated.app;

import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadHelper {

    public interface StreamCallback {
        void onSuccess(String streamUrl, String extension, String mimeType);
        void onError(String error);
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(8);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    // Resilient mirror instances
    private static final String[] COBALT_INSTANCES = new String[]{
            "https://api.cobalt.tools",
            "https://cobalt.api.scip.io",
            "https://capi.wuk.sh",
            "https://cobalt.canine.tools",
            "https://api.server.garden",
            "https://cobalt-api.kwiatekm.tokyo"
    };

    private static final String[] INVIDIOUS_INSTANCES = new String[]{
            "https://invidious.nerdvpn.de",
            "https://inv.nadeko.net",
            "https://iv.melmac.space",
            "https://yt.artemislena.eu",
            "https://invidious.jing.rocks",
            "https://invidious.privacydev.net",
            "https://inv.tux.pizza"
    };

    private static final String[] PIPED_INSTANCES = new String[]{
            "https://api.piped.private.coffee",
            "https://pipedapi.kavin.rocks",
            "https://piped-api.lunar.icu",
            "https://pipedapi.tokhmi.xyz"
    };

    public static void getStreamUrl(final String videoId, final String targetQuality, final boolean isAudioOnly, final StreamCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                // Method 1: Innertube iOS Client (unthrottled, unciphered stream formats)
                StreamResult iosResult = fetchFromInnertubeIos(videoId, targetQuality, isAudioOnly);
                if (iosResult != null) {
                    postSuccess(callback, iosResult.url, iosResult.extension, iosResult.mimeType);
                    return;
                }

                // Method 2: Innertube TV Embedded Client (clean stream formats)
                StreamResult tvResult = fetchFromInnertubeTv(videoId, targetQuality, isAudioOnly);
                if (tvResult != null) {
                    postSuccess(callback, tvResult.url, tvResult.extension, tvResult.mimeType);
                    return;
                }

                // Method 3: Multi-cluster Cobalt API (v10 and legacy endpoints)
                StreamResult cobaltResult = fetchFromCobalt(videoId, targetQuality, isAudioOnly);
                if (cobaltResult != null) {
                    postSuccess(callback, cobaltResult.url, cobaltResult.extension, cobaltResult.mimeType);
                    return;
                }

                // Method 4: Invidious Instances (formatStreams & adaptiveFormats)
                StreamResult invidiousResult = fetchFromInvidious(videoId, targetQuality, isAudioOnly);
                if (invidiousResult != null) {
                    postSuccess(callback, invidiousResult.url, invidiousResult.extension, invidiousResult.mimeType);
                    return;
                }

                // Method 5: Piped Instances
                StreamResult pipedResult = fetchFromPiped(videoId, targetQuality, isAudioOnly);
                if (pipedResult != null) {
                    postSuccess(callback, pipedResult.url, pipedResult.extension, pipedResult.mimeType);
                    return;
                }

                // Method 6: Direct Invidious Proxy Stream
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

                postError(callback, "לא הצלחנו לחלץ קישור הורדה ישיר עבור סרטון זה ממקורות השרת.");
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

    /**
     * Innertube iOS Client - delivers un-throttled URLs with complete parameters.
     */
    private static StreamResult fetchFromInnertubeIos(String videoId, String targetQuality, boolean isAudioOnly) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(6500);
            conn.setReadTimeout(6500);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "com.google.ios.youtube/19.29.1 (iPhone14,3; U; CPU iOS 17_5 like Mac OS X; en_US)");
            conn.setRequestProperty("X-YouTube-Client-Name", "5");
            conn.setRequestProperty("X-YouTube-Client-Version", "19.29.1");

            JSONObject payload = new JSONObject();
            payload.put("videoId", videoId);

            JSONObject context = new JSONObject();
            JSONObject client = new JSONObject();
            client.put("clientName", "IOS");
            client.put("clientVersion", "19.29.1");
            client.put("deviceMake", "Apple");
            client.put("deviceModel", "iPhone14,3");
            client.put("osName", "iOS");
            client.put("osVersion", "17.5.0.21F79");
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
                    return parseStreamingData(streamingData, targetQuality, isAudioOnly);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    /**
     * Innertube TV HTML5 embedded client.
     */
    private static StreamResult fetchFromInnertubeTv(String videoId, String targetQuality, boolean isAudioOnly) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(6500);
            conn.setReadTimeout(6500);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (SMART-TV; Linux; Tizen 5.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/5.0 TV Safari/538.1");
            conn.setRequestProperty("X-YouTube-Client-Name", "85");
            conn.setRequestProperty("X-YouTube-Client-Version", "2.0");

            JSONObject payload = new JSONObject();
            payload.put("videoId", videoId);

            JSONObject context = new JSONObject();
            JSONObject client = new JSONObject();
            client.put("clientName", "TVHTML5_SIMPLY_EMBEDDED_PLAYER");
            client.put("clientVersion", "2.0");
            client.put("hl", "en");
            client.put("gl", "US");
            context.put("client", client);

            JSONObject thirdParty = new JSONObject();
            thirdParty.put("embedUrl", "https://www.youtube.com");
            payload.put("context", context);
            payload.put("thirdParty", thirdParty);

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
                    return parseStreamingData(streamingData, targetQuality, isAudioOnly);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static StreamResult parseStreamingData(JSONObject streamingData, String targetQuality, boolean isAudioOnly) {
        try {
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
                                bestExt = (mime.contains("mp4") || mime.contains("m4a")) ? "m4a" : "mp3";
                            }
                        }
                    }
                    if (bestAudioUrl != null) {
                        return new StreamResult(bestAudioUrl, bestExt, bestMime);
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
                    // Default to highest resolution combined format available
                    JSONObject first = formats.getJSONObject(0);
                    String directUrl = first.optString("url", "");
                    if (!directUrl.isEmpty()) {
                        return new StreamResult(directUrl, "mp4", "video/mp4");
                    }
                }
            }
        } catch (Exception ignored) {}
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
                conn.setReadTimeout(5500);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", USER_AGENT);

                // Cobalt v10 standard payload
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
                    if (stream.isEmpty()) {
                        stream = json.optString("text", "");
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

        // 1. If user selected a custom directory via Storage Access Framework
        if (DataManager.FOLDER_CUSTOM_TREE.equals(folderSetting) && customTreeUriStr != null && !customTreeUriStr.isEmpty()) {
            downloadToDocumentTree(context, title, downloadUrl, extension, mimeType, customTreeUriStr, isAudio);
            return;
        }

        // 2. Standard Public Storage (DownloadManager or MediaStore writer)
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
                    conn.setReadTimeout(25000);
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
                            Toast.makeText(context, "שגיאה בהורדה לתיקייה המותאמת. שומר בהורדות הראשיות...", Toast.LENGTH_SHORT).show();
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

                if (DataManager.FOLDER_DEFAULT_MUSIC.equalsIgnoreCase(targetFolderKey) && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, filename);
                } else if (DataManager.FOLDER_DEFAULT_MOVIES.equalsIgnoreCase(targetFolderKey) && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, filename);
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

        // Direct Stream Saver Fallback (compatible with Android 10+ Scoped Storage)
        downloadDirectlyWithMediaStore(context, title, downloadUrl, extension, mimeType, isAudio, targetFolderKey);
    }

    private static void downloadDirectlyWithMediaStore(final Context context, final String title, final String downloadUrl, final String extension, final String mimeType, final boolean isAudio, final String targetFolderKey) {
        Toast.makeText(context, "מתחיל הורדה ישירה למכשיר...", Toast.LENGTH_SHORT).show();

        executor.submit(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                InputStream in = null;
                OutputStream out = null;
                Uri itemUri = null;

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
                            // Fallback to downloads collection
                            itemUri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        }

                        if (itemUri != null) {
                            out = context.getContentResolver().openOutputStream(itemUri);
                        }
                    } else {
                        // Android 9 and below
                        File targetDir;
                        if (isAudio) {
                            targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                        } else {
                            targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                        }
                        if (!targetDir.exists()) targetDir.mkdirs();
                        File targetFile = new File(targetDir, fileName);
                        out = new FileOutputStream(targetFile);
                    }

                    if (out == null) {
                        throw new Exception("שגיאה בפתיחת קובץ יעד");
                    }

                    URL url = new URL(downloadUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", USER_AGENT);
                    conn.setRequestProperty("Referer", "https://www.youtube.com/");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(25000);
                    conn.setInstanceFollowRedirects(true);

                    int respCode = conn.getResponseCode();
                    if (respCode >= 300 && respCode < 400) {
                        String newUrl = conn.getHeaderField("Location");
                        conn.disconnect();
                        conn = (HttpURLConnection) new URL(newUrl).openConnection();
                        conn.setRequestProperty("User-Agent", USER_AGENT);
                    }

                    in = conn.getInputStream();
                    byte[] buffer = new byte[16384];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.flush();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && itemUri != null) {
                        ContentValues updateVals = new ContentValues();
                        updateVals.put(MediaStore.MediaColumns.IS_PENDING, 0);
                        context.getContentResolver().update(itemUri, updateVals, null, null);
                    }

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "הקובץ ירד בהצלחה ונשמר במכשיר!", Toast.LENGTH_LONG).show();
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
