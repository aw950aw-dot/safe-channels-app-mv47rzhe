package com.generated.app;

public class ChannelModel {
    private String id;
    private String name;
    private String category;
    private boolean isApproved;

    public ChannelModel(String id, String name, String category, boolean isApproved) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.isApproved = isApproved;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public boolean isApproved() { return isApproved; }

    public void setApproved(boolean approved) { isApproved = approved; }
    public void setName(String name) { this.name = name; }
}
