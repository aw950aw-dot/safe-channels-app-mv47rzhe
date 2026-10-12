package com.generated.app;

public class VideoModel {
    private String id;
    private String title;
    private String channelId;
    private String channelTitle;
    private String publishedDate;
    private String thumbnailUrl;
    private String description;
    private int durationSeconds;
    private boolean isShort;

    public VideoModel(String id, String title, String channelId, String channelTitle, String publishedDate, String thumbnailUrl, String description, int durationSeconds, boolean isShort) {
        this.id = id;
        this.title = title;
        this.channelId = channelId;
        this.channelTitle = channelTitle;
        this.publishedDate = publishedDate;
        this.thumbnailUrl = thumbnailUrl;
        this.description = description;
        this.durationSeconds = durationSeconds;
        this.isShort = isShort;
    }

    public VideoModel(String id, String title, String channelId, String channelTitle, String publishedDate, String thumbnailUrl, String description) {
        this(id, title, channelId, channelTitle, publishedDate, thumbnailUrl, description, 0, false);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getChannelId() { return channelId; }
    public String getChannelTitle() { return channelTitle; }
    public String getPublishedDate() { return publishedDate; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getDescription() { return description; }
    public int getDurationSeconds() { return durationSeconds; }
    public boolean isShort() { return isShort; }

    public String getFormattedDuration() {
        if (durationSeconds <= 0) return "";
        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;
        if (minutes >= 60) {
            int hours = minutes / 60;
            minutes = minutes % 60;
            return String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds);
    }
}
