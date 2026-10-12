package com.generated.app;

import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RssFetcher {

    public interface FetchCallback {
        void onSuccess(List<VideoModel> videos);
        void onError(String error);
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String[] INVIDIOUS_INSTANCES = new String[]{
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://vid.puffyan.us"
    };

    public static void fetchVideosForChannels(final List<ChannelModel> channels, final FetchCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                final List<VideoModel> allVideos = new ArrayList<>();
                Set<String> seenIds = new HashSet<>();

                for (ChannelModel channel : channels) {
                    if (!channel.isApproved()) continue;

                    // Fetch comprehensively from both RSS feed and Invidious API
                    List<VideoModel> channelVideos = fetchChannelAllVideos(channel.getId(), channel.getName());
                    
                    for (VideoModel v : channelVideos) {
                        if (!seenIds.contains(v.getId())) {
                            seenIds.add(v.getId());
                            allVideos.add(v);
                        }
                    }
                }

                Collections.sort(allVideos, new Comparator<VideoModel>() {
                    @Override
                    public int compare(VideoModel v1, VideoModel v2) {
                        return v2.getPublishedDate().compareTo(v1.getPublishedDate());
                    }
                });

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(allVideos);
                    }
                });
            }
        });
    }

    private static List<VideoModel> fetchChannelAllVideos(String channelId, String fallbackChannelName) {
        List<VideoModel> list = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        // 1. Fetch from Invidious API pages (fetches many pages of videos per channel)
        for (int page = 1; page <= 3; page++) {
            List<VideoModel> pageVideos = fetchInvidiousChannelPage(channelId, fallbackChannelName, page);
            if (pageVideos.isEmpty()) break;

            for (VideoModel v : pageVideos) {
                if (!ids.contains(v.getId())) {
                    ids.add(v.getId());
                    list.add(v);
                }
            }
        }

        // 2. Fetch standard YouTube RSS feed as reliable baseline
        List<VideoModel> rssList = fetchChannelRss(channelId, fallbackChannelName);
        for (VideoModel v : rssList) {
            if (!ids.contains(v.getId())) {
                ids.add(v.getId());
                list.add(v);
            }
        }

        return list;
    }

    private static List<VideoModel> fetchInvidiousChannelPage(String channelId, String channelName, int page) {
        List<VideoModel> list = new ArrayList<>();

        for (String instance : INVIDIOUS_INSTANCES) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(instance + "/api/v1/channels/" + channelId + "/videos?page=" + page);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONArray array = new JSONArray(sb.toString());
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.getJSONObject(i);
                        String videoId = obj.optString("videoId", "");
                        String title = obj.optString("title", "");
                        long publishedUnix = obj.optLong("published", 0);
                        int lengthSeconds = obj.optInt("lengthSeconds", 0);
                        String desc = obj.optString("description", "");

                        if (!videoId.isEmpty() && !title.isEmpty()) {
                            String thumb = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
                            String dateStr = publishedUnix > 0 ? formatDateUnix(publishedUnix) : "";

                            boolean isShort = lengthSeconds > 0 && lengthSeconds <= 60 || title.toLowerCase().contains("#shorts");

                            list.add(new VideoModel(videoId, title, channelId, channelName, dateStr, thumb, desc, lengthSeconds, isShort));
                        }
                    }

                    if (!list.isEmpty()) {
                        break;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        return list;
    }

    private static List<VideoModel> fetchChannelRss(String channelId, String fallbackChannelName) {
        List<VideoModel> videos = new ArrayList<>();
        HttpURLConnection conn = null;
        InputStream inputStream = null;

        try {
            String rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId;
            URL url = new URL(rssUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                inputStream = conn.getInputStream();
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(inputStream, "UTF-8");

                int eventType = parser.getEventType();
                boolean insideEntry = false;

                String videoId = "";
                String title = "";
                String channelTitle = fallbackChannelName;
                String published = "";
                String description = "";

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    String name = parser.getName();

                    switch (eventType) {
                        case XmlPullParser.START_TAG:
                            if ("entry".equalsIgnoreCase(name)) {
                                insideEntry = true;
                                videoId = "";
                                title = "";
                                published = "";
                                description = "";
                            } else if (insideEntry) {
                                if (name != null && name.endsWith("videoId")) {
                                    videoId = parser.nextText();
                                } else if ("title".equalsIgnoreCase(name)) {
                                    title = parser.nextText();
                                } else if ("name".equalsIgnoreCase(name) && channelTitle.equals(fallbackChannelName)) {
                                    channelTitle = parser.nextText();
                                } else if ("published".equalsIgnoreCase(name)) {
                                    published = parser.nextText();
                                } else if (name != null && name.endsWith("description")) {
                                    description = parser.nextText();
                                }
                            }
                            break;

                        case XmlPullParser.END_TAG:
                            if ("entry".equalsIgnoreCase(name)) {
                                insideEntry = false;
                                if (!videoId.isEmpty()) {
                                    String thumbUrl = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
                                    String formattedDate = formatDateIso(published);

                                    boolean isShort = title.toLowerCase().contains("#shorts");

                                    videos.add(new VideoModel(videoId, title, channelId, channelTitle, formattedDate, thumbUrl, description, 0, isShort));
                                }
                            }
                            break;
                    }
                    eventType = parser.next();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try { inputStream.close(); } catch (Exception ignored) {}
            }
            if (conn != null) conn.disconnect();
        }

        return videos;
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

    private static String formatDateUnix(long unixTimestamp) {
        try {
            Date date = new Date(unixTimestamp * 1000L);
            SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return displayFormat.format(date);
        } catch (Exception e) {
            return "";
        }
    }
}
