package com.generated.app;

public class ChannelModel {
    private String id;
    private String name;
    private String category;
    private boolean isApproved;
    private String avatarUrl;

    public ChannelModel(String id, String name, String category, boolean isApproved) {
        this(id, name, category, isApproved, "");
    }

    public ChannelModel(String id, String name, String category, boolean isApproved, String avatarUrl) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.isApproved = isApproved;
        this.avatarUrl = avatarUrl != null ? avatarUrl : "";
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public boolean isApproved() { return isApproved; }
    public String getAvatarUrl() { return avatarUrl; }

    public void setApproved(boolean approved) { isApproved = approved; }
    public void setName(String name) { this.name = name; }
    public void setCategory(String category) { this.category = category; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}
