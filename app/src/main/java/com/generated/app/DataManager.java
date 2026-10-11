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
        boolean firstTime = !prefs.contains(KEY_APPROVED_CHANNELS);

        // Pre-populated high quality channels list
        addChannelToMemory("UCsooa4yRKGN_zEE8iknghZA", "TED-Ed", "חינוך וידע", firstTime || approvedSet.contains("UCsooa4yRKGN_zEE8iknghZA"));
        addChannelToMemory("UCpVm7bg6pXKo1Pr6k5S7Dtw", "National Geographic", "מדע וטכנולוגיה", firstTime || approvedSet.contains("UCpVm7bg6pXKo1Pr6k5S7Dtw"));
        addChannelToMemory("UCmPee3p_SjR0yS_D0kR4Irg", "כאן 11", "כללי ותוכן מומלץ", firstTime || approvedSet.contains("UCmPee3p_SjR0yS_D0kR4Irg"));
        addChannelToMemory("UCB47S5-2aHqM4B8N_G4qSJQ", "כאן חינוכית", "חינוך וידע", firstTime || approvedSet.contains("UCB47S5-2aHqM4B8N_G4qSJQ"));
        addChannelToMemory("UC1gG09OcfYqL0lS9N3XfT8A", "ערוץ הידברות", "כללי ותוכן מומלץ", approvedSet.contains("UC1gG09OcfYqL0lS9N3XfT8A"));
        addChannelToMemory("UCx5X8B7uK9zZ23W0G4m_T2A", "מכון דוידסון", "מדע וטכנולוגיה", firstTime || approvedSet.contains("UCx5X8B7uK9zZ23W0G4m_T2A"));
        addChannelToMemory("UCsXVk37bltHxD1rDPwtNM8Q", "Kurzgesagt – In a Nutshell", "מדע וטכנולוגיה", approvedSet.contains("UCsXVk37bltHxD1rDPwtNM8Q"));
        addChannelToMemory("UC1D260X1B4pQnO_c0wM8V4g", "BBC Earth", "מדע וטכנולוגיה", approvedSet.contains("UC1D260X1B4pQnO_c0wM8V4g"));

        // Load custom user added channels
        String customStr = prefs.getString(KEY_CUSTOM_CHANNELS, "");
        if (!customStr.isEmpty()) {
            String[] customItems = customStr.split(";;");
            for (String item : customItems) {
                String[] parts = item.split("::");
                if (parts.length >= 2) {
                    String id = parts[0];
                    String name = parts[1];
                    addChannelToMemory(id, name, "ערוץ מותאם", approvedSet.contains(id));
                }
            }
        }

        if (firstTime) {
            saveApprovedChannels();
        }
    }

    private void addChannelToMemory(String id, String name, String category, boolean approved) {
        allChannelsList.add(new ChannelModel(id, name, category, approved));
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

    public void addCustomChannel(String channelId, String name) {
        for (ChannelModel ch : allChannelsList) {
            if (ch.getId().equals(channelId)) {
                ch.setApproved(true);
                saveApprovedChannels();
                return;
            }
        }
        ChannelModel newCh = new ChannelModel(channelId, name, "ערוץ מותאם", true);
        allChannelsList.add(0, newCh);
        saveApprovedChannels();

        // Save custom string
        StringBuilder sb = new StringBuilder();
        String customStr = prefs.getString(KEY_CUSTOM_CHANNELS, "");
        if (!customStr.isEmpty()) {
            sb.append(customStr).append(";;");
        }
        sb.append(channelId).append("::").append(name);
        prefs.edit().putString(KEY_CUSTOM_CHANNELS, sb.toString()).apply();
    }
}
