package com.generated.app;

public class VideoModel {
    private String id;
    private String title;
    private String channelId;
    private String channelTitle;
    private String publishedDate;
    private String thumbnailUrl;
    private String description;

    public VideoModel(String id, String title, String channelId, String channelTitle, String publishedDate, String thumbnailUrl, String description) {
        this.id = id;
        this.title = title;
        this.channelId = channelId;
        this.channelTitle = channelTitle;
        this.publishedDate = publishedDate;
        this.thumbnailUrl = thumbnailUrl;
        this.description = description;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getChannelId() { return channelId; }
    public String getChannelTitle() { return channelTitle; }
    public String getPublishedDate() { return publishedDate; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getDescription() { return description; }
}
