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
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChannelSearchHelper {

    public static class ChannelSearchResult {
        private String id;
        private String name;
        private String description;
        private String avatarUrl;

        public ChannelSearchResult(String id, String name, String description, String avatarUrl) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.avatarUrl = avatarUrl;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getAvatarUrl() { return avatarUrl; }
    }

    public interface SearchCallback {
        void onSuccess(List<ChannelSearchResult> results);
        void onError(String message);
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public static void searchChannels(final String query, final SearchCallback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    String cleanQuery = query != null ? query.trim() : "";
                    if (cleanQuery.isEmpty()) {
                        postSuccess(callback, new ArrayList<ChannelSearchResult>());
                        return;
                    }

                    if (cleanQuery.startsWith("UC") && cleanQuery.length() >= 20) {
                        List<ChannelSearchResult> singleResult = new ArrayList<>();
                        singleResult.add(new ChannelSearchResult(cleanQuery, "ערוץ " + cleanQuery, "מזהה ערוץ ישיר", ""));
                        postSuccess(callback, singleResult);
                        return;
                    }

                    List<ChannelSearchResult> results = scrapeYouTubeSearch(cleanQuery);

                    if (results.isEmpty()) {
                        results = searchInvidious(cleanQuery);
                    }

                    final List<ChannelSearchResult> finalResults = results;
                    postSuccess(callback, finalResults);

                } catch (Exception e) {
                    e.printStackTrace();
                    postError(callback, "שגיאה בחיפוש ערוצים: " + e.getMessage());
                }
            }
        });
    }

    private static void postSuccess(final SearchCallback callback, final List<ChannelSearchResult> results) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(results);
            }
        });
    }

    private static void postError(final SearchCallback callback, final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(message);
            }
        });
    }

    private static List<ChannelSearchResult> scrapeYouTubeSearch(String query) {
        List<ChannelSearchResult> list = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            String encoded = URLEncoder.encode(query, "UTF-8");
            String searchUrl = "https://www.youtube.com/results?search_query=" + encoded + "&sp=EgIQAg%3D%3D";

            URL url = new URL(searchUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept-Language", "he,en;q=0.9");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                String html = sb.toString();

                Pattern pRenderer = Pattern.compile("\"channelRenderer\"\\s*:\\s*\\{(.+?)(?=\"channelRenderer\"|\"videoRenderer\"|\\}\\s*,\\s*\"shelfRenderer\"|\\}\\s*\\]\\s*\\})", Pattern.DOTALL);
                Matcher mRenderer = pRenderer.matcher(html);

                Pattern pId = Pattern.compile("\"channelId\"\\s*:\\s*\"(UC[a-zA-Z0-9_-]{22})\"");
                Pattern pTitle1 = Pattern.compile("\"title\"\\s*:\\s*\\{[^}]*\"simpleText\"\\s*:\\s*\"([^\"]+)\"");
                Pattern pTitle2 = Pattern.compile("\"title\"\\s*:\\s*\\{[^}]*\"text\"\\s*:\\s*\"([^\"]+)\"");
                Pattern pThumb = Pattern.compile("\"thumbnails\"\\s*:\\s*\\[\\s*\\{\\s*\"url\"\\s*:\\s*\"([^\"]+)\"");
                Pattern pSubs = Pattern.compile("\"subscriberCountText\"\\s*:\\s*\\{[^}]*\"simpleText\"\\s*:\\s*\"([^\"]+)\"");

                while (mRenderer.find() && list.size() < 12) {
                    String block = mRenderer.group(1);

                    Matcher mId = pId.matcher(block);
                    if (!mId.find()) continue;
                    String channelId = mId.group(1);

                    boolean duplicate = false;
                    for (ChannelSearchResult item : list) {
                        if (item.getId().equals(channelId)) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (duplicate) continue;

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

                    if (title.isEmpty()) {
                        title = "ערוץ יוטיוב";
                    }

                    String avatar = "";
                    Matcher mThumb = pThumb.matcher(block);
                    if (mThumb.find()) {
                        avatar = mThumb.group(1);
                        if (avatar.startsWith("//")) {
                            avatar = "https:" + avatar;
                        }
                    }

                    String description = "";
                    Matcher mSubs = pSubs.matcher(block);
                    if (mSubs.find()) {
                        description = mSubs.group(1);
                    }

                    title = unescapeHtml(title);
                    description = unescapeHtml(description);

                    list.add(new ChannelSearchResult(channelId, title, description, avatar));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) conn.disconnect();
        }
        return list;
    }

    private static List<ChannelSearchResult> searchInvidious(String query) {
        List<ChannelSearchResult> list = new ArrayList<>();
        String[] instances = new String[]{
                "https://inv.nadeko.net",
                "https://invidious.nerdvpn.de",
                "https://vid.puffyan.us"
        };

        for (String instance : instances) {
            HttpURLConnection conn = null;
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                URL url = new URL(instance + "/api/v1/search?q=" + encoded + "&type=channel");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
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
                    for (int i = 0; i < array.length() && list.size() < 10; i++) {
                        JSONObject obj = array.getJSONObject(i);
                        String channelId = obj.optString("authorId", "");
                        String name = obj.optString("author", "");
                        String desc = obj.optString("description", "");
                        String avatar = "";

                        JSONArray thumbs = obj.optJSONArray("authorThumbnails");
                        if (thumbs != null && thumbs.length() > 0) {
                            JSONObject thumbObj = thumbs.getJSONObject(thumbs.length() - 1);
                            avatar = thumbObj.optString("url", "");
                        }

                        if (!channelId.isEmpty() && !name.isEmpty()) {
                            list.add(new ChannelSearchResult(channelId, name, desc, avatar));
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

    private static String unescapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("\\u0026", "&");
    }
}
