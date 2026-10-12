package com.generated.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RssFetcher {

    public interface FetchCallback {
        void onSuccess(List<VideoModel> videos);
        void onError(String error);
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(6);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36";

    private static final String[] INVIDIOUS_INSTANCES = new String[]{
            "https://inv.nadeko.net",
            "https://invidious.jing.rocks",
            "https://yt.artemislena.eu",
            "https://invidious.privacyredirect.com",
            "https://iv.melmac.space"
    };

    private static final String[] PIPED_INSTANCES = new String[]{
            "https://pipedapi.kavin.rocks",
            "https://api.piped.privacydev.net",
            "https://pipedapi.tokhmi.xyz"
    };

    public static void fetchVideosForChannels(final List<ChannelModel> channels, final FetchCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                final List<ChannelModel> approvedList = new ArrayList<>();
                for (ChannelModel c : channels) {
                    if (c.isApproved()) {
                        approvedList.add(c);
                    }
                }

                if (approvedList.isEmpty()) {
                    postSuccess(callback, new ArrayList<VideoModel>());
                    return;
                }

                final List<VideoModel> allVideos = Collections.synchronizedList(new ArrayList<VideoModel>());
                final Set<String> seenIds = Collections.synchronizedSet(new HashSet<String>());
                final CountDownLatch latch = new CountDownLatch(approvedList.size());

                for (final ChannelModel channel : approvedList) {
                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                List<VideoModel> channelVideos = fetchVideosMultiSource(channel.getId(), channel.getName());
                                for (VideoModel v : channelVideos) {
                                    if (v != null && v.getId() != null && !v.getId().isEmpty()) {
                                        synchronized (seenIds) {
                                            if (!seenIds.contains(v.getId())) {
                                                seenIds.add(v.getId());
                                                allVideos.add(v);
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                latch.countDown();
                            }
                        }
                    });
                }

                try {
                    latch.await(14, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}

                List<VideoModel> resultList;
                synchronized (allVideos) {
                    resultList = new ArrayList<>(allVideos);
                }

                Collections.sort(resultList, new Comparator<VideoModel>() {
                    @Override
                    public int compare(VideoModel v1, VideoModel v2) {
                        String d1 = v1.getPublishedDate() != null ? v1.getPublishedDate() : "";
                        String d2 = v2.getPublishedDate() != null ? v2.getPublishedDate() : "";
                        return d2.compareTo(d1);
                    }
                });

                if (resultList.isEmpty()) {
                    postError(callback, "לא הצלחנו להביא סרטונים. בדוק את חיבור האינטרנט ונסה שוב.");
                } else {
                    postSuccess(callback, resultList);
                }
            }
        });
    }

    private static void postSuccess(final FetchCallback callback, final List<VideoModel> videos) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(videos);
            }
        });
    }

    private static void postError(final FetchCallback callback, final String error) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(error);
            }
        });
    }

    private static List<VideoModel> fetchVideosMultiSource(String channelId, String fallbackChannelName) {
        List<VideoModel> list = new ArrayList<>();

        // Tier 1: YouTube Official RSS Feed with follow-redirects
        list = fetchChannelRss(channelId, fallbackChannelName);
        if (!list.isEmpty()) {
            return list;
        }

        // Tier 2: Invidious API
        list = fetchFromInvidious(channelId, fallbackChannelName);
        if (!list.isEmpty()) {
            return list;
        }

        // Tier 3: Piped API
        list = fetchFromPiped(channelId, fallbackChannelName);
        if (!list.isEmpty()) {
            return list;
        }

        // Tier 4: Scrape YouTube channel videos page
        list = fetchChannelFromHtml(channelId, fallbackChannelName);
        return list;
    }

    private static List<VideoModel> fetchChannelRss(String channelId, String fallbackChannelName) {
        List<VideoModel> videos = new ArrayList<>();
        HttpURLConnection conn = null;
        InputStream is = null;

        try {
            String rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId;
            conn = openConnectionWithRedirects(rssUrl, 6000);
            conn.setRequestProperty("User-Agent", BROWSER_USER_AGENT);
            conn.setRequestProperty("Accept", "application/atom+xml,application/xml,text/xml,*/*");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();

                String xml = sb.toString();

                Pattern pEntry = Pattern.compile("<entry>(.*?)</entry>", Pattern.DOTALL);
                Matcher mEntry = pEntry.matcher(xml);

                Pattern pVideoId = Pattern.compile("<yt:videoId>(.*?)</yt:videoId>");
                Pattern pTitle = Pattern.compile("<title>(.*?)</title>");
                Pattern pAuthor = Pattern.compile("<name>(.*?)</name>");
                Pattern pPublished = Pattern.compile("<published>(.*?)</published>");
                Pattern pDesc = Pattern.compile("<media:description>(.*?)</media:description>", Pattern.DOTALL);

                while (mEntry.find()) {
                    String block = mEntry.group(1);

                    String videoId = "";
                    Matcher mId = pVideoId.matcher(block);
                    if (mId.find()) {
                        videoId = mId.group(1).trim();
                    } else {
                        Pattern pAltId = Pattern.compile("<id>yt:video:(.*?)</id>");
                        Matcher mAltId = pAltId.matcher(block);
                        if (mAltId.find()) {
                            videoId = mAltId.group(1).trim();
                        }
                    }

                    if (videoId.isEmpty()) continue;

                    String title = "וידאו";
                    Matcher mTitle = pTitle.matcher(block);
                    if (mTitle.find()) {
                        title = unescapeXml(mTitle.group(1).trim());
                    }

                    String channelTitle = fallbackChannelName;
                    Matcher mAuthor = pAuthor.matcher(block);
                    if (mAuthor.find()) {
                        channelTitle = unescapeXml(mAuthor.group(1).trim());
                    }

                    String published = "";
                    Matcher mPub = pPublished.matcher(block);
                    if (mPub.find()) {
                        published = formatDateIso(mPub.group(1).trim());
                    }

                    String desc = "";
                    Matcher mDesc = pDesc.matcher(block);
                    if (mDesc.find()) {
                        desc = unescapeXml(mDesc.group(1).trim());
                    }

                    String thumbUrl = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
                    boolean isShort = title.toLowerCase().contains("#shorts") || title.contains("שורטס");

                    videos.add(new VideoModel(videoId, title, channelId, channelTitle, published, thumbUrl, desc, 0, isShort));
                }
            }
        } catch (Exception e) {
            // RSS fail, fallback will catch it
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ignored) {}
            }
            if (conn != null) conn.disconnect();
        }

        return videos;
    }

    private static List<VideoModel> fetchFromInvidious(String channelId, String fallbackChannelName) {
        List<VideoModel> videos = new ArrayList<>();
        for (String instance : INVIDIOUS_INSTANCES) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(instance + "/api/v1/channels/" + channelId + "/videos");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4500);
                conn.setReadTimeout(4500);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", BROWSER_USER_AGENT);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONArray arr = new JSONArray(sb.toString());
                    for (int i = 0; i < arr.length() && i < 20; i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String videoId = obj.optString("videoId", "");
                        String title = obj.optString("title", "סרטון");
                        String author = obj.optString("author", fallbackChannelName);
                        int lengthSeconds = obj.optInt("lengthSeconds", 0);
                        long published = obj.optLong("published", 0);

                        String dateStr = "";
                        if (published > 0) {
                            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                            dateStr = sdf.format(new Date(published * 1000L));
                        }

                        if (!videoId.isEmpty()) {
                            boolean isShort = lengthSeconds > 0 && lengthSeconds <= 60 || title.toLowerCase().contains("#shorts");
                            String thumbUrl = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
                            videos.add(new VideoModel(videoId, title, channelId, author, dateStr, thumbUrl, "", lengthSeconds, isShort));
                        }
                    }

                    if (!videos.isEmpty()) {
                        break;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return videos;
    }

    private static List<VideoModel> fetchFromPiped(String channelId, String fallbackChannelName) {
        List<VideoModel> videos = new ArrayList<>();
        for (String instance : PIPED_INSTANCES) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(instance + "/channel/" + channelId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4500);
                conn.setReadTimeout(4500);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", BROWSER_USER_AGENT);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject root = new JSONObject(sb.toString());
                    JSONArray related = root.optJSONArray("relatedStreams");
                    if (related != null) {
                        for (int i = 0; i < related.length() && i < 20; i++) {
                            JSONObject stream = related.getJSONObject(i);
                            String streamUrl = stream.optString("url", "");
                            String videoId = "";
                            if (streamUrl.contains("/watch?v=")) {
                                videoId = streamUrl.substring(streamUrl.indexOf("/watch?v=") + 9);
                            } else if (streamUrl.startsWith("/")) {
                                videoId = streamUrl.replace("/", "");
                            }

                            if (!videoId.isEmpty()) {
                                String title = stream.optString("title", "סרטון");
                                String author = stream.optString("uploaderName", fallbackChannelName);
                                int duration = stream.optInt("duration", 0);
                                String uploadedDate = stream.optString("uploadedDate", "");
                                boolean isShort = stream.optBoolean("isShort", duration > 0 && duration <= 60);

                                String thumbUrl = stream.optString("thumbnail", "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg");
                                videos.add(new VideoModel(videoId, title, channelId, author, uploadedDate, thumbUrl, "", duration, isShort));
                            }
                        }
                    }
                    if (!videos.isEmpty()) {
                        break;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return videos;
    }

    private static List<VideoModel> fetchChannelFromHtml(String channelId, String fallbackChannelName) {
        List<VideoModel> list = new ArrayList<>();
        HttpURLConnection conn = null;

        try {
            String pageUrl = "https://www.youtube.com/channel/" + channelId + "/videos";
            conn = openConnectionWithRedirects(pageUrl, 6000);
            conn.setRequestProperty("User-Agent", BROWSER_USER_AGENT);
            conn.setRequestProperty("Accept-Language", "he,en;q=0.9");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                String html = sb.toString();

                Pattern pRenderer = Pattern.compile("\"videoRenderer\"\\s*:\\s*\\{(.+?)(?=\"videoRenderer\"|\"gridVideoRenderer\"|\\}\\s*,\\s*\"shelfRenderer\"|\\}\\s*\\]\\s*\\})", Pattern.DOTALL);
                Matcher mRenderer = pRenderer.matcher(html);

                Pattern pId = Pattern.compile("\"videoId\"\\s*:\\s*\"([a-zA-Z0-9_-]{11})\"");
                Pattern pTitle1 = Pattern.compile("\"title\"\\s*:\\s*\\{[^}]*\"text\"\\s*:\\s*\"([^\"]+)\"");
                Pattern pTitle2 = Pattern.compile("\"title\"\\s*:\\s*\\{[^}]*\"simpleText\"\\s*:\\s*\"([^\"]+)\"");
                Pattern pLength = Pattern.compile("\"lengthText\"\\s*:\\s*\\{[^}]*\"simpleText\"\\s*:\\s*\"([^\"]+)\"");
                Pattern pPublished = Pattern.compile("\"publishedTimeText\"\\s*:\\s*\\{[^}]*\"simpleText\"\\s*:\\s*\"([^\"]+)\"");

                Set<String> addedIds = new HashSet<>();

                while (mRenderer.find() && list.size() < 25) {
                    String block = mRenderer.group(1);

                    Matcher mId = pId.matcher(block);
                    if (!mId.find()) continue;
                    String videoId = mId.group(1);

                    if (addedIds.contains(videoId)) continue;
                    addedIds.add(videoId);

                    String title = "";
                    Matcher mTitle = pTitle1.matcher(block);
                    if (mTitle.find()) {
                        title = mTitle.group(1);
                    } else {
                        Matcher mTitleAlt = pTitle2.matcher(block);
                        if (mTitleAlt.find()) {
                            title = mTitleAlt.group(1);
                        }
                    }

                    if (title.isEmpty()) title = "סרטון יוטיוב";

                    String durationText = "";
                    Matcher mLen = pLength.matcher(block);
                    if (mLen.find()) {
                        durationText = mLen.group(1);
                    }

                    int durationSec = parseDurationSeconds(durationText);

                    String pubDate = "";
                    Matcher mPub = pPublished.matcher(block);
                    if (mPub.find()) {
                        pubDate = mPub.group(1);
                    }

                    boolean isShort = (durationSec > 0 && durationSec <= 60) || title.toLowerCase().contains("#shorts");
                    String thumbUrl = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";

                    list.add(new VideoModel(videoId, unescapeXml(title), channelId, fallbackChannelName, pubDate, thumbUrl, "", durationSec, isShort));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }

        return list;
    }

    private static HttpURLConnection openConnectionWithRedirects(String urlStr, int timeout) throws Exception {
        int redirects = 0;
        HttpURLConnection conn = null;
        String curUrl = urlStr;
        while (redirects < 4) {
            URL url = new URL(curUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl != null && !newUrl.isEmpty()) {
                    curUrl = newUrl;
                    redirects++;
                    conn.disconnect();
                    continue;
                }
            }
            break;
        }
        return conn;
    }

    private static int parseDurationSeconds(String text) {
        if (text == null || text.isEmpty()) return 0;
        try {
            String[] parts = text.split(":");
            if (parts.length == 2) {
                return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
            } else if (parts.length == 3) {
                return Integer.parseInt(parts[0].trim()) * 3600 + Integer.parseInt(parts[1].trim()) * 60 + Integer.parseInt(parts[2].trim());
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static String formatDateIso(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "";
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            Date date = isoFormat.parse(isoDate);
            SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return displayFormat.format(date);
        } catch (Exception e) {
            if (isoDate.length() >= 10) {
                return isoDate.substring(0, 10);
            }
            return isoDate;
        }
    }

    private static String unescapeXml(String text) {
        if (text == null) return "";
        return text.replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("\\u0026", "&");
    }
}
