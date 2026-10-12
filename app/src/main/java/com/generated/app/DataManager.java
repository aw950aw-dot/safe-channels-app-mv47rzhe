package com.generated.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DataManager {
    private static final String PREF_NAME = "SafeChannelsPrefs";
    private static final String KEY_PASSWORD = "app_password";
    private static final String KEY_APPROVED_CHANNELS = "approved_channels_set";
    private static final String KEY_DELETED_CHANNELS = "deleted_channels_set";
    private static final String KEY_CUSTOM_CHANNELS = "custom_channels_str";

    private static DataManager instance;
    private SharedPreferences prefs;
    private List<ChannelModel> allChannelsList;

    private DataManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        initChannels();
    }

    public static synchronized DataManager getInstance(Context context) {
        if (instance == null) {
            instance = new DataManager(context);
        }
        return instance;
    }

    private void initChannels() {
        allChannelsList = new ArrayList<>();

        Set<String> approvedSet = getApprovedChannelIds();
        Set<String> deletedSet = getDeletedChannelIds();
        boolean firstTime = !prefs.contains(KEY_APPROVED_CHANNELS);

        // Curated, verified YouTube Channels
        addInitialChannel("UCsooa4yRKGN_zEE8iknghZA", "TED-Ed", "חינוך וידע", "https://yt3.googleusercontent.com/ytc/AIdro_n-XgPsqL6Wq79eI_rL8g=s88-c-k-c0x00ffffff-no-rj", firstTime, approvedSet, deletedSet);
        addInitialChannel("UCpVm7bg6pXKo1Pr6k5S7Dtw", "National Geographic", "מדע וטכנולוגיה", "https://yt3.googleusercontent.com/ytc/AIdro_n-vF7c=s88-c-k-c0x00ffffff-no-rj", firstTime, approvedSet, deletedSet);
        addInitialChannel("UCn4Y1f1TjSjJt5L9G0_u7pA", "כאן 11", "כללי ותוכן מומלץ", "https://yt3.googleusercontent.com/ytc/AIdro_k46=s88-c-k-c0x00ffffff-no-rj", firstTime, approvedSet, deletedSet);
        addInitialChannel("UCB47S5-2aHqM4B8N_G4qSJQ", "כאן חינוכית", "חינוך וידע", "https://yt3.googleusercontent.com/ytc/AIdro_k47=s88-c-k-c0x00ffffff-no-rj", firstTime, approvedSet, deletedSet);
        addInitialChannel("UC1gG09OcfYqL0lS9N3XfT8A", "ערוץ הידברות", "כללי ותוכן מומלץ", "https://yt3.googleusercontent.com/ytc/AIdro_k48=s88-c-k-c0x00ffffff-no-rj", false, approvedSet, deletedSet);
        addInitialChannel("UCx5X8B7uK9zZ23W0G4m_T2A", "מכון דוידסון", "מדע וטכנולוגיה", "https://yt3.googleusercontent.com/ytc/AIdro_k49=s88-c-k-c0x00ffffff-no-rj", firstTime, approvedSet, deletedSet);
        addInitialChannel("UCsXVk37bltHxD1rDPwtNM8Q", "Kurzgesagt – In a Nutshell", "מדע וטכנולוגיה", "https://yt3.googleusercontent.com/ytc/AIdro_k50=s88-c-k-c0x00ffffff-no-rj", false, approvedSet, deletedSet);
        addInitialChannel("UC1D260X1B4pQnO_c0wM8V4g", "BBC Earth", "מדע וטכנולוגיה", "https://yt3.googleusercontent.com/ytc/AIdro_k51=s88-c-k-c0x00ffffff-no-rj", false, approvedSet, deletedSet);

        // Load custom user added channels
        String customStr = prefs.getString(KEY_CUSTOM_CHANNELS, "");
        if (!customStr.isEmpty()) {
            String[] customItems = customStr.split(";;");
            for (String item : customItems) {
                String[] parts = item.split("::");
                if (parts.length >= 2) {
                    String id = parts[0];
                    String name = parts[1];
                    String avatarUrl = parts.length >= 3 ? parts[2] : "";

                    if (!deletedSet.contains(id)) {
                        allChannelsList.add(new ChannelModel(id, name, "ערוץ מותאם", approvedSet.contains(id), avatarUrl));
                    }
                }
            }
        }

        if (firstTime) {
            saveApprovedChannels();
        }
    }

    private void addInitialChannel(String id, String name, String category, String avatarUrl, boolean defaultApproved, Set<String> approvedSet, Set<String> deletedSet) {
        if (deletedSet.contains(id)) {
            return;
        }
        boolean approved = defaultApproved || approvedSet.contains(id);
        allChannelsList.add(new ChannelModel(id, name, category, approved, avatarUrl));
    }

    public String getPassword() {
        return prefs.getString(KEY_PASSWORD, "1234");
    }

    public void setPassword(String newPassword) {
        prefs.edit().putString(KEY_PASSWORD, newPassword).apply();
    }

    public boolean checkPassword(String input) {
        return getPassword().equals(input);
    }

    public List<ChannelModel> getAllChannels() {
        return allChannelsList;
    }

    public Set<String> getApprovedChannelIds() {
        return prefs.getStringSet(KEY_APPROVED_CHANNELS, new HashSet<String>());
    }

    public Set<String> getDeletedChannelIds() {
        return prefs.getStringSet(KEY_DELETED_CHANNELS, new HashSet<String>());
    }

    public List<ChannelModel> getApprovedChannels() {
        List<ChannelModel> approvedList = new ArrayList<>();
        for (ChannelModel ch : allChannelsList) {
            if (ch.isApproved()) {
                approvedList.add(ch);
            }
        }
        return approvedList;
    }

    public void setChannelApproved(String channelId, boolean approved) {
        for (ChannelModel ch : allChannelsList) {
            if (ch.getId().equals(channelId)) {
                ch.setApproved(approved);
                break;
            }
        }
        saveApprovedChannels();
    }

    private void saveApprovedChannels() {
        Set<String> set = new HashSet<>();
        for (ChannelModel ch : allChannelsList) {
            if (ch.isApproved()) {
                set.add(ch.getId());
            }
        }
        prefs.edit().putStringSet(KEY_APPROVED_CHANNELS, set).apply();
    }

    public boolean channelExists(String channelId) {
        for (ChannelModel ch : allChannelsList) {
            if (ch.getId().equals(channelId)) {
                return true;
            }
        }
        return false;
    }

    public void addCustomChannel(String channelId, String name, String avatarUrl) {
        Set<String> deletedSet = new HashSet<>(getDeletedChannelIds());
        if (deletedSet.contains(channelId)) {
            deletedSet.remove(channelId);
            prefs.edit().putStringSet(KEY_DELETED_CHANNELS, deletedSet).apply();
        }

        for (ChannelModel ch : allChannelsList) {
            if (ch.getId().equals(channelId)) {
                ch.setApproved(true);
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    ch.setAvatarUrl(avatarUrl);
                }
                saveApprovedChannels();
                return;
            }
        }

        ChannelModel newCh = new ChannelModel(channelId, name, "ערוץ מותאם", true, avatarUrl);
        allChannelsList.add(0, newCh);
        saveApprovedChannels();

        String customStr = prefs.getString(KEY_CUSTOM_CHANNELS, "");
        StringBuilder sb = new StringBuilder();
        if (!customStr.isEmpty()) {
            sb.append(customStr).append(";;");
        }
        sb.append(channelId).append("::").append(name).append("::").append(avatarUrl != null ? avatarUrl : "");
        prefs.edit().putString(KEY_CUSTOM_CHANNELS, sb.toString()).apply();
    }

    public void deleteChannel(String channelId) {
        for (int i = 0; i < allChannelsList.size(); i++) {
            if (allChannelsList.get(i).getId().equals(channelId)) {
                allChannelsList.remove(i);
                break;
            }
        }

        Set<String> deletedSet = new HashSet<>(getDeletedChannelIds());
        deletedSet.add(channelId);
        prefs.edit().putStringSet(KEY_DELETED_CHANNELS, deletedSet).apply();

        saveApprovedChannels();

        String customStr = prefs.getString(KEY_CUSTOM_CHANNELS, "");
        if (!customStr.isEmpty()) {
            String[] customItems = customStr.split(";;");
            StringBuilder sb = new StringBuilder();
            for (String item : customItems) {
                String[] parts = item.split("::");
                if (parts.length >= 2 && !parts[0].equals(channelId)) {
                    if (sb.length() > 0) {
                        sb.append(";;");
                    }
                    sb.append(item);
                }
            }
            prefs.edit().putString(KEY_CUSTOM_CHANNELS, sb.toString()).apply();
        }
    }
}
