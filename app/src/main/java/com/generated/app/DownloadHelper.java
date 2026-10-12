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
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadHelper {

    public interface StreamCallback {
        void onSuccess(String streamUrl, String extension, String mimeType);
        void onError(String error);
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(8);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36";

    // Modern active Invidious API instances
    private static final String[] INVIDIOUS_INSTANCES = new String[]{
            "https://invidious.nerdvpn.de",
            "https://yewtu.be",
            "https://inv.nadeko.net",
            "https://invidious.jing.rocks",
            "https://iv.melmac.space",
            "https://invidious.drgns.space",
            "https://invidious.privacydev.net"
    };

    // Modern Cobalt API instances supporting v7 & v10 protocols
    private static final String[] COBALT_INSTANCES = new String[]{
            "https://cobalt-api.kwiatekm.tokyo",
            "https://capi.wuk.sh",
            "https://cobalt.api.scip.io",
            "https://cobalt.canine.tools",
            "https://api.cobalt.tools"
    };

    // Modern Piped API instances
    private static final String[] PIPED_INSTANCES = new String[]{
            "https://pipedapi.kavin.rocks",
            "https://api.piped.private.coffee",
            "https://piped-api.lunar.icu",
            "https://pipedapi.tokhmi.xyz"
    };

    public static void getStreamUrl(final String videoId, final String targetQuality, final boolean isAudioOnly, final StreamCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                // Tier 1: Invidious Direct Media Formats (formatStreams & adaptiveFormats)
                StreamResult invApiResult = fetchFromInvidiousApi(videoId, targetQuality, isAudioOnly);
                if (invApiResult != null && isStreamUrlValid(invApiResult.url)) {
                    postSuccess(callback, invApiResult.url, invApiResult.extension, invApiResult.mimeType);
                    return;
                }

                // Tier 2: Cobalt Multi-version API (Pre-merged, ultra-fast CDN download links)
                StreamResult cobaltResult = fetchFromCobalt(videoId, targetQuality, isAudioOnly);
                if (cobaltResult != null && isStreamUrlValid(cobaltResult.url)) {
                    postSuccess(callback, cobaltResult.url, cobaltResult.extension, cobaltResult.mimeType);
                    return;
                }

                // Tier 3: Piped Stream Services
                StreamResult pipedResult = fetchFromPiped(videoId, targetQuality, isAudioOnly);
                if (pipedResult != null && isStreamUrlValid(pipedResult.url)) {
                    postSuccess(callback, pipedResult.url, pipedResult.extension, pipedResult.mimeType);
                    return;
                }

                // Tier 4: Y2Mate Direct Converter Engine
                StreamResult y2mateResult = fetchFromY2Mate(videoId, targetQuality, isAudioOnly);
                if (y2mateResult != null && isStreamUrlValid(y2mateResult.url)) {
                    postSuccess(callback, y2mateResult.url, y2mateResult.extension, y2mateResult.mimeType);
                    return;
                }

                // Tier 5: Innertube iOS Client Parser
                StreamResult iosResult = fetchFromInnertubeIos(videoId, targetQuality, isAudioOnly);
                if (iosResult != null && isStreamUrlValid(iosResult.url)) {
                    postSuccess(callback, iosResult.url, iosResult.extension, iosResult.mimeType);
                    return;
                }

                // Fallbacks: If strict probe failed on some network configurations, return top candidate URL directly
                if (invApiResult != null && invApiResult.url.startsWith("http")) {
                    postSuccess(callback, invApiResult.url, invApiResult.extension, invApiResult.mimeType);
                    return;
                }
                if (cobaltResult != null && cobaltResult.url.startsWith("http")) {
                    postSuccess(callback, cobaltResult.url, cobaltResult.extension, cobaltResult.mimeType);
                    return;
                }
                if (pipedResult != null && pipedResult.url.startsWith("http")) {
                    postSuccess(callback, pipedResult.url, pipedResult.extension, pipedResult.mimeType);
                    return;
                }
                if (y2mateResult != null && y2mateResult.url.startsWith("http")) {
                    postSuccess(callback, y2mateResult.url, y2mateResult.extension, y2mateResult.mimeType);
                    return;
                }

                postError(callback, "לא הצלחנו לחלץ קישור ישיר לסרטון זה. באפשרותך להוריד ישירות דרך כלי ההורדה המהיר.");
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

    private static boolean isStreamUrlValid(String testUrl) {
        if (testUrl == null || testUrl.trim().isEmpty()) return false;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(testUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3500);
            conn.setReadTimeout(3500);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "*/*");
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
            return testUrl.startsWith("http");
        } finally {
            if (conn != null) conn.disconnect();
        }
        return false;
    }

    private static StreamResult fetchFromInvidiousApi(String videoId, String targetQuality, boolean isAudioOnly) {
        for (String instance : INVIDIOUS_INSTANCES) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(instance + "/api/v1/videos/" + videoId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(4500);
                conn.setReadTimeout(4500);
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setRequestProperty("Accept", "application/json");

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
                            String bestAudioUrl = "";
                            int bestBitrate = 0;
                            String bestExt = "m4a";

                            for (int i = 0; i < adaptive.length(); i++) {
                                JSONObject item = adaptive.getJSONObject(i);
                                String type = item.optString("type", "");
                                String directUrl = item.optString("url", "");
                                int bitrate = item.optInt("bitrate", 0);

                                if (!directUrl.isEmpty() && type.startsWith("audio/")) {
                                    if (directUrl.startsWith("/")) directUrl = instance + directUrl;
                                    if (bitrate >= bestBitrate) {
                                        bestBitrate = bitrate;
                                        bestAudioUrl = directUrl;
                                        bestExt = (type.contains("mp4") || type.contains("m4a")) ? "m4a" : "mp3";
                                    }
                                }
                            }
                            if (!bestAudioUrl.isEmpty()) {
                                return new StreamResult(bestAudioUrl, bestExt, "audio/mp4");
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
                            // Default to highest pre-merged stream
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
                conn.setReadTimeout(6000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", USER_AGENT);

                JSONObject body = new JSONObject();
                body.put("url", "https://www.youtube.com/watch?v= " + videoId);
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
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    String stream = "";

                    if (json.has("url")) stream = json.optString("url", "");
                    if (stream.isEmpty() && json.has("tunnel")) stream = json.optString("tunnel", "");
                    if (stream.isEmpty() && json.has("redirect")) stream = json.optString("redirect", "");
                    if (stream.isEmpty() && json.has("picker")) {
                        JSONArray picker = json.optJSONArray("picker");
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
                    while ((line = reader.readLine()) != null) sb.append(line);
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
                                boolean isVideoOnly = stream.optBoolean("videoOnly", false);
                                if (!isVideoOnly) {
                                    String matchedUrl = stream.optString("url", "");
                                    String mime = stream.optString("mimeType", "video/mp4");
                                    String q = stream.optString("quality", "");
                                    if (!matchedUrl.isEmpty()) {
                                        if (targetQuality == null || q.contains(targetQuality)) {
                                            return new StreamResult(matchedUrl, "mp4", mime);
                                        }
                                    }
                                }
                            }
                            for (int i = 0; i < videoStreams.length(); i++) {
                                JSONObject stream = videoStreams.getJSONObject(i);
                                if (!stream.optBoolean("videoOnly", false)) {
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

    private static StreamResult fetchFromY2Mate(String videoId, String targetQuality, boolean isAudioOnly) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://www.y2mate.com/mates/analyzeV2/ajax");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5500);
            conn.setReadTimeout(5500);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");

            String postData = "k_query=" + URLEncoder.encode("https://www.youtube.com/watch?v=" + videoId, "UTF-8") + "&k_page=home&hl=en&q_auto=0";
            OutputStream os = conn.getOutputStream();
            os.write(postData.getBytes("UTF-8"));
            os.flush();
            os.close();

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                String vid = json.optString("vid", videoId);
                JSONObject links = json.optJSONObject("links");
                if (links != null) {
                    String kKey = null;
                    if (isAudioOnly) {
                        JSONObject mp3Obj = links.optJSONObject("mp3");
                        if (mp3Obj != null) {
                            for (java.util.Iterator<String> keys = mp3Obj.keys(); keys.hasNext(); ) {
                                JSONObject item = mp3Obj.getJSONObject(keys.next());
                                kKey = item.optString("k", "");
                                if (!kKey.isEmpty()) break;
                            }
                        }
                    } else {
                        JSONObject mp4Obj = links.optJSONObject("mp4");
                        if (mp4Obj != null) {
                            for (java.util.Iterator<String> keys = mp4Obj.keys(); keys.hasNext(); ) {
                                JSONObject item = mp4Obj.getJSONObject(keys.next());
                                String q = item.optString("q", "");
                                if (targetQuality != null && q.contains(targetQuality)) {
                                    kKey = item.optString("k", "");
                                    break;
                                }
                            }
                            if (kKey == null || kKey.isEmpty()) {
                                for (java.util.Iterator<String> keys = mp4Obj.keys(); keys.hasNext(); ) {
                                    JSONObject item = mp4Obj.getJSONObject(keys.next());
                                    kKey = item.optString("k", "");
                                    if (!kKey.isEmpty()) break;
                                }
                            }
                        }
                    }

                    if (kKey != null && !kKey.isEmpty()) {
                        return convertY2MateKey(vid, kKey, isAudioOnly);
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static StreamResult convertY2MateKey(String vid, String kKey, boolean isAudioOnly) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://www.y2mate.com/mates/convertV2/index");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");

            String postData = "vid=" + URLEncoder.encode(vid, "UTF-8") + "&k=" + URLEncoder.encode(kKey, "UTF-8");
            OutputStream os = conn.getOutputStream();
            os.write(postData.getBytes("UTF-8"));
            os.flush();
            os.close();

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                String dlink = json.optString("dlink", "");
                if (!dlink.isEmpty()) {
                    String ext = isAudioOnly ? "mp3" : "mp4";
                    String mime = isAudioOnly ? "audio/mpeg" : "video/mp4";
                    return new StreamResult(dlink, ext, mime);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

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

    public static void startDownload(final Context context, final String title, final String downloadUrl, final String extension, final String mimeType, final boolean isAudio) {
        final DataManager dm = DataManager.getInstance(context);
        String customTreeUriStr = isAudio ? dm.getAudioTreeUri() : dm.getVideoTreeUri();
        String folderSetting = isAudio ? dm.getAudioDownloadFolder() : dm.getVideoDownloadFolder();

        // Path 1: Custom Document Tree via SAF if selected by user
        if (DataManager.FOLDER_CUSTOM_TREE.equals(folderSetting) && customTreeUriStr != null && !customTreeUriStr.isEmpty()) {
            downloadToDocumentTree(context, title, downloadUrl, extension, mimeType, customTreeUriStr, isAudio);
            return;
        }

        // Path 2: Native Android DownloadManager (System service, background queue, reliable notification progress)
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
                Toast.makeText(context, "ההורדה החלה ברקע! תוכל לראות את ההתקדמות בווילון ההתראות.", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to direct MediaStore file stream
        }

        // Path 3: Direct MediaStore fallback
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

                    if (totalBytes < 10240) {
                        throw new Exception("קובץ המדיה שהתקבל קטן מדי או פגום");
                    }

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
                            Toast.makeText(context, "שגיאה בגישה לתיקייה: " + e.getMessage() + ". שומר להורדות רגילות...", Toast.LENGTH_SHORT).show();
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
        Toast.makeText(context, "מתחיל בהורדת הקובץ...", Toast.LENGTH_SHORT).show();

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

                    if (totalBytes < 10240) {
                        throw new Exception("הקובץ שהורד אינו תקין או שהורד חלקית בלבד");
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
