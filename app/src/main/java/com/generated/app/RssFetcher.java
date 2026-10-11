package com.generated.app;

import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RssFetcher {

    public interface FetchCallback {
        void onSuccess(List<VideoModel> videos);
        void onError(String error);
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void fetchVideosForChannels(final List<ChannelModel> channels, final FetchCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                final List<VideoModel> allVideos = new ArrayList<>();

                for (ChannelModel channel : channels) {
                    if (!channel.isApproved()) continue;

                    List<VideoModel> channelVideos = fetchChannelRss(channel.getId(), channel.getName());
                    allVideos.addAll(channelVideos);
                }

                // Sort videos by publication date descending
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

    private static List<VideoModel> fetchChannelRss(String channelId, String fallbackChannelName) {
        List<VideoModel> videos = new ArrayList<>();
        HttpURLConnection conn = null;
        InputStream inputStream = null;

        try {
            String rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId;
            URL url = new URL(rssUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
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
                                    String formattedDate = formatDate(published);
                                    videos.add(new VideoModel(videoId, title, channelId, channelTitle, formattedDate, thumbUrl, description));
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

    private static String formatDate(String isoDate) {
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
}
