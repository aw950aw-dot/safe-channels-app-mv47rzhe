package com.generated.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
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

    private static final String PREF_CACHE = "SafeTube_Video_Cache";
    private static final String KEY_CACHED_VIDEOS = "cached_videos_json";

    private static final ExecutorService executor = Executors.newFixedThreadPool(6);
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
            "https://iv.melmac.space",
            "https://invidious.jing.rocks"
    };

    public static List<VideoModel> getCachedVideos(Context context) {
        List<VideoModel> list = new ArrayList<>();
        try {
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_CACHE, Context.MODE_PRIVATE);
            String rawJson = prefs.getString(KEY_CACHED_VIDEOS, "");
            if (!rawJson.isEmpty()) {
                JSONArray arr = new JSONArray(rawJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    list.add(new VideoModel(
                            obj.getString("id"),
                            obj.getString("title"),
                            obj.getString("channelId"),
                            obj.getString("channelTitle"),
                            obj.optString("publishedDate", ""),
                            obj.optString("thumbnailUrl", ""),
                            obj.optString("description", ""),
                            obj.optInt("durationSeconds", 0),
                            obj.optBoolean("isShort", false)
                    ));
                }
            }
        } catch (Exception ignored) {}

        if (list.isEmpty()) {
            list = getInitialCuratedFallback();
        }

        return list;
    }

    private static List<VideoModel> getInitialCuratedFallback() {
        List<VideoModel> list = new ArrayList<>();
        list.add(new VideoModel("7tCNuV7y768", "מה קורה בתוך המוח כשאנחנו לומדים?", "UCsooa4yRKGN_zEE8iknghZA", "TED-Ed", "2024-03-01", "https://i.ytimg.com/vi/7tCNuV7y768/hqdefault.jpg", "הסבר מרתק על פעילות המוח והזיכרון.", 315, false));
        list.add(new VideoModel("tOsqT_3l1bU", "הפלאים של עולם החי והאוקיינוסים", "UCpVm7bg6pXKo1Pr6k5S7Dtw", "National Geographic", "2024-02-20", "https://i.ytimg.com/vi/tOsqT_3l1bU/hqdefault.jpg", "מבט מרהיב על הטבע והחיים במעמקים.", 450, false));
        list.add(new VideoModel("Ww1m6gV4Z6A", "ילדי בית העץ - פרק מלא חינוכי", "UCB47S5-2aHqM4B8N_G4qSJQ", "כאן חינוכית", "2024-01-15", "https://i.ytimg.com/vi/Ww1m6gV4Z6A/hqdefault.jpg", "תוכנית חינוכית מהנה לכל המשפחה.", 620, false));
        list.add(new VideoModel("hFZFjoX2cGg", "איך עובד החיסון בגוף האדם?", "UCx5X8B7uK9zZ23W0G4m_T2A", "מכון דוידסון", "2024-01-10", "https://i.ytimg.com/vi/hFZFjoX2cGg/hqdefault.jpg", "הסבר מדעי פשוט וברור על המערכת החיסונית.", 280, false));
        return list;
    }

    private static void saveCachedVideos(Context context, List<VideoModel> videos) {
        try {
            JSONArray arr = new JSONArray();
            for (VideoModel v : videos) {
                JSONObject obj = new JSONObject();
                obj.put("id", v.getId());
                obj.put("title", v.getTitle());
                obj.put("channelId", v.getChannelId());
                obj.put("channelTitle", v.getChannelTitle());
                obj.put("publishedDate", v.getPublishedDate());
                obj.put("thumbnailUrl", v.getThumbnailUrl());
                obj.put("description", v.getDescription());
                obj.put("durationSeconds", v.getDurationSeconds());
                obj.put("isShort", v.isShort());
                arr.put(obj);
            }
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_CACHE, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_CACHED_VIDEOS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static void fetchVideosForChannels(final Context context, final List<ChannelModel> channels, final FetchCallback callback) {
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

                if (!resultList.isEmpty()) {
                    Collections.sort(resultList, new Comparator<VideoModel>() {
                        @Override
                        public int compare(VideoModel v1, VideoModel v2) {
                            String d1 = v1.getPublishedDate() != null ? v1.getPublishedDate() : "";
                            String d2 = v2.getPublishedDate() != null ? v2.getPublishedDate() : "";
                            return d2.compareTo(d1);
                        }
                    });

                    saveCachedVideos(context, resultList);
                    postSuccess(callback, resultList);
                } else {
                    List<VideoModel> cached = getCachedVideos(context);
                    if (!cached.isEmpty()) {
                        postSuccess(callback, cached);
                    } else {
                        postError(callback, "לא הצלחנו להביא סרטונים. אנא בדוק את החיבור לאינטרנט ונסה שוב.");
                    }
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
        // Priority 1: Direct YouTube RSS Feed
        List<VideoModel> list = fetchChannelRss(channelId, fallbackChannelName);
        if (!list.isEmpty()) {
            return list;
        }

        // Priority 2: Piped API
        list = fetchFromPiped(channelId, fallbackChannelName);
        if (!list.isEmpty()) {
            return list;
        }

        // Priority 3: Innertube API
        list = fetchFromInnertube(channelId, fallbackChannelName);
        if (!list.isEmpty()) {
            return list;
        }

        // Priority 4: Invidious API
        list = fetchFromInvidious(channelId, fallbackChannelName);
        if (!list.isEmpty()) {
            return list;
        }

        // Priority 5: YouTube Channel HTML fallback
        list = fetchChannelFromHtml(channelId, fallbackChannelName);
        return list;
    }

    private static List<VideoModel> fetchChannelRss(String channelId, String fallbackChannelName) {
        List<VideoModel> videos = new ArrayList<>();
        HttpURLConnection conn = null;
        InputStream is = null;

        try {
            String rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId;
            URL url = new URL(rssUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "text/xml,application/xhtml+xml,application/xml,application/atom+xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "he,en-US,en;q=0.9");

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
                videos = parseAtomXml(xml, channelId, fallbackChannelName);
            }
        } catch (Exception ignored) {
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ignored) {}
            }
            if (conn != null) conn.disconnect();
        }

        return videos;
    }

    private static List<VideoModel> parseAtomXml(String xml, String channelId, String fallbackChannelName) {
        List<VideoModel> list = new ArrayList<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xml));

            int eventType = parser.getEventType();
            boolean insideEntry = false;

            String videoId = "";
            String title = "";
            String author = fallbackChannelName;
            String published = "";
            String description = "";

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();

                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("entry".equalsIgnoreCase(tagName)) {
                            insideEntry = true;
                            videoId = "";
                            title = "";
                            author = fallbackChannelName;
                            published = "";
                            description = "";
                        } else if (insideEntry) {
                            if ("yt:videoId".equalsIgnoreCase(tagName) || "videoId".equalsIgnoreCase(tagName)) {
                                videoId = parser.nextText().trim();
                            } else if ("title".equalsIgnoreCase(tagName)) {
                                title = parser.nextText().trim();
                            } else if ("name".equalsIgnoreCase(tagName)) {
                                author = parser.nextText().trim();
                            } else if ("published".equalsIgnoreCase(tagName)) {
                                published = formatDateIso(parser.nextText().trim());
                            } else if ("media:description".equalsIgnoreCase(tagName) || "description".equalsIgnoreCase(tagName)) {
                                description = parser.nextText().trim();
                            }
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if ("entry".equalsIgnoreCase(tagName)) {
                            insideEntry = false;
                            if (!videoId.isEmpty()) {
                                String cleanTitle = title.isEmpty() ? "סרטון" : unescapeXml(title);
                                String thumb = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
                                boolean isShort = cleanTitle.toLowerCase().contains("#shorts") || cleanTitle.contains("שורטס");
                                list.add(new VideoModel(videoId, cleanTitle, channelId, author, published, thumb, description, 0, isShort));
                            }
                        }
                        break;
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            // Fallback to regex parser if XML pull fails
            return parseAtomRegex(xml, channelId, fallbackChannelName);
        }
        return list;
    }

    private static List<VideoModel> parseAtomRegex(String xml, String channelId, String fallbackChannelName) {
        List<VideoModel> list = new ArrayList<>();
        try {
            Pattern pEntry = Pattern.compile("<entry\\b[^>]*>(.*?)</entry>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher mEntry = pEntry.matcher(xml);

            Pattern pVideoId = Pattern.compile("<(?:yt:)?videoId>(.*?)</(?:yt:)?videoId>", Pattern.CASE_INSENSITIVE);
            Pattern pTitle = Pattern.compile("<title\\b[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE);
            Pattern pAuthor = Pattern.compile("<name\\b[^>]*>(.*?)</name>", Pattern.CASE_INSENSITIVE);
            Pattern pPublished = Pattern.compile("<published\\b[^>]*>(.*?)</published>", Pattern.CASE_INSENSITIVE);
            Pattern pDesc = Pattern.compile("<(?:media:)?description\\b[^>]*>(.*?)</(?:media:)?description>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

            while (mEntry.find()) {
                String block = mEntry.group(1);
                Matcher mId = pVideoId.matcher(block);
                if (!mId.find()) continue;
                String videoId = mId.group(1).trim();

                String title = "וידאו";
                Matcher mT = pTitle.matcher(block);
                if (mT.find()) title = unescapeXml(mT.group(1).trim());

                String channelTitle = fallbackChannelName;
                Matcher mA = pAuthor.matcher(block);
                if (mA.find()) channelTitle = unescapeXml(mA.group(1).trim());

                String published = "";
                Matcher mP = pPublished.matcher(block);
                if (mP.find()) published = formatDateIso(mP.group(1).trim());

                String desc = "";
                Matcher mD = pDesc.matcher(block);
                if (mD.find()) desc = unescapeXml(mD.group(1).trim());

                String thumbUrl = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
                boolean isShort = title.toLowerCase().contains("#shorts") || title.contains("שורטס");

                list.add(new VideoModel(videoId, title, channelId, channelTitle, published, thumbUrl, desc, 0, isShort));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private static List<VideoModel> fetchFromPiped(String channelId, String fallbackChannelName) {
        List<VideoModel> videos = new ArrayList<>();
        for (String instance : PIPED_INSTANCES) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(instance + "/channel/" + channelId);
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
                    JSONArray related = json.optJSONArray("relatedStreams");
                    if (related != null) {
                        for (int i = 0; i < related.length() && i < 30; i++) {
                            JSONObject item = related.getJSONObject(i);
                            String videoUrl = item.optString("url", "");
                            String videoId = "";
                            if (videoUrl.contains("/watch?v=")) {
                                videoId = videoUrl.substring(videoUrl.indexOf("/watch?v=") + 9);
                            } else {
                                videoId = videoUrl.replace("/watch?v=", "").replace("/", "");
                            }

                            if (!videoId.isEmpty()) {
                                String title = item.optString("title", "וידאו");
                                String uploader = item.optString("uploaderName", fallbackChannelName);
                                String thumb = item.optString("thumbnail", "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg");
                                int durationSec = item.optInt("duration", 0);
                                String uploadDate = item.optString("uploadedDate", "");
                                boolean isShort = item.optBoolean("isShort", false) || durationSec <= 60 || title.toLowerCase().contains("#shorts");

                                videos.add(new VideoModel(videoId, title, channelId, uploader, uploadDate, thumb, "", durationSec, isShort));
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

    private static List<VideoModel> fetchFromInnertube(String channelId, String fallbackChannelName) {
        List<VideoModel> videos = new ArrayList<>();
        HttpURLConnection conn = null;

        try {
            URL url = new URL("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("X-YouTube-Client-Name", "1");
            conn.setRequestProperty("X-YouTube-Client-Version", "2.20240401.01.00");

            JSONObject client = new JSONObject();
            client.put("clientName", "WEB");
            client.put("clientVersion", "2.20240401.01.00");
            client.put("hl", "he");
            client.put("gl", "IL");

            JSONObject context = new JSONObject();
            context.put("client", client);

            JSONObject payload = new JSONObject();
            payload.put("context", context);
            payload.put("browseId", channelId);
            payload.put("params", "Egl2aWRlb3PyBgQKAjoA");

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

                String jsonStr = sb.toString();

                Pattern pRenderer = Pattern.compile("\"videoRenderer\"\\s*:\\s*\\{(.+?)(?=\"videoRenderer\"|\"gridVideoRenderer\"|\\}\\s*,\\s*\"shelfRenderer\"|\\}\\s*\\]\\s*\\})", Pattern.DOTALL);
                Matcher mRenderer = pRenderer.matcher(jsonStr);

                Pattern pId = Pattern.compile("\"videoId\"\\s*:\\s*\"([a-zA-Z0-9_-]{11})\"");
                Pattern pTitle = Pattern.compile("\"title\"\\s*:\\s*\\{[^}]*\"text\"\\s*:\\s*\"([^\"]+)\"");
                Pattern pLength = Pattern.compile("\"lengthText\"\\s*:\\s*\\{[^}]*\"simpleText\"\\s*:\\s*\"([^\"]+)\"");
                Pattern pPub = Pattern.compile("\"publishedTimeText\"\\s*:\\s*\\{[^}]*\"simpleText\"\\s*:\\s*\"([^\"]+)\"");

                Set<String> added = new HashSet<>();
                while (mRenderer.find() && videos.size() < 30) {
                    String block = mRenderer.group(1);

                    Matcher mId = pId.matcher(block);
                    if (!mId.find()) continue;
                    String videoId = mId.group(1);
                    if (added.contains(videoId)) continue;
                    added.add(videoId);

                    String title = "וידאו";
                    Matcher mT = pTitle.matcher(block);
                    if (mT.find()) title = mT.group(1);

                    String durationText = "";
                    Matcher mL = pLength.matcher(block);
                    if (mL.find()) durationText = mL.group(1);

                    String pubDate = "";
                    Matcher mP = pPub.matcher(block);
                    if (mP.find()) pubDate = mP.group(1);

                    int durationSec = parseDurationSeconds(durationText);
                    boolean isShort = (durationSec > 0 && durationSec <= 60) || title.toLowerCase().contains("#shorts");
                    String thumbUrl = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";

                    videos.add(new VideoModel(videoId, unescapeJson(title), channelId, fallbackChannelName, pubDate, thumbUrl, "", durationSec, isShort));
                }
            }
        } catch (Exception ignored) {
        } finally {
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

                    JSONArray arr = new JSONArray(sb.toString());
                    for (int i = 0; i < arr.length() && i < 25; i++) {
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

    private static List<VideoModel> fetchChannelFromHtml(String channelId, String fallbackChannelName) {
        List<VideoModel> list = new ArrayList<>();
        HttpURLConnection conn = null;

        try {
            String pageUrl = "https://www.youtube.com/channel/" + channelId + "/videos";
            URL url = new URL(pageUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", USER_AGENT);
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

    private static String unescapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\u0026", "&");
    }
}
