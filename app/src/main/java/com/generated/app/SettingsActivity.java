package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {

    private ImageButton btnBack;
    private Button btnChangePass;
    private EditText etSearchChannels;
    private Button btnAddCustomChannel;
    private LinearLayout llCategoryChips;
    private ListView lvSettingsChannels;

    private DataManager dataManager;
    private ChannelAdapter channelAdapter;

    private List<ChannelModel> allChannels = new ArrayList<>();
    private List<ChannelModel> filteredChannels = new ArrayList<>();
    private String selectedCategory = "ALL";
    private String currentSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        dataManager = DataManager.getInstance(this);

        initViews();
        setupListeners();
        loadChannelsData();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnChangePass = findViewById(R.id.btnChangePass);
        etSearchChannels = findViewById(R.id.etSearchChannels);
        btnAddCustomChannel = findViewById(R.id.btnAddCustomChannel);
        llCategoryChips = findViewById(R.id.llCategoryChips);
        lvSettingsChannels = findViewById(R.id.lvSettingsChannels);

        channelAdapter = new ChannelAdapter(this, filteredChannels, new ChannelAdapter.OnChannelToggleListener() {
            @Override
            public void onToggle(ChannelModel channel, boolean isApproved) {
                dataManager.setChannelApproved(channel.getId(), isApproved);
            }
        });
        lvSettingsChannels.setAdapter(channelAdapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnChangePass.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChangePasswordDialog();
            }
        });

        btnAddCustomChannel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddChannelDialog();
            }
        });

        etSearchChannels.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().trim().toLowerCase();
                filterChannels();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadChannelsData() {
        allChannels = dataManager.getAllChannels();
        setupCategoryChips();
        filterChannels();
    }

    private void setupCategoryChips() {
        llCategoryChips.removeAllViews();

        addCategoryChip(getString(R.string.category_all), "ALL", selectedCategory.equals("ALL"));
        addCategoryChip(getString(R.string.category_science), "מדע וטכנולוגיה", selectedCategory.equals("מדע וטכנולוגיה"));
        addCategoryChip(getString(R.string.category_education), "חינוך וידע", selectedCategory.equals("חינוך וידע"));
        addCategoryChip(getString(R.string.category_general), "כללי ותוכן מומלץ", selectedCategory.equals("כללי ותוכן מומלץ"));
    }

    private void addCategoryChip(final String label, final String catKey, boolean isSelected) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(12);
        chip.setPadding(24, 12, 24, 12);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(8);
        chip.setLayoutParams(params);

        if (isSelected) {
            chip.setBackgroundResource(R.drawable.bg_chip_active);
            chip.setTextColor(0xFFFFFFFF);
        } else {
            chip.setBackgroundResource(R.drawable.bg_chip_inactive);
            chip.setTextColor(0xFF475569);
        }

        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedCategory = catKey;
                setupCategoryChips();
                filterChannels();
            }
        });

        llCategoryChips.addView(chip);
    }

    private void filterChannels() {
        filteredChannels.clear();

        for (ChannelModel ch : allChannels) {
            boolean matchesCat = selectedCategory.equals("ALL") || ch.getCategory().equalsIgnoreCase(selectedCategory);
            boolean matchesSearch = currentSearchQuery.isEmpty() ||
                    ch.getName().toLowerCase().contains(currentSearchQuery) ||
                    ch.getId().toLowerCase().contains(currentSearchQuery) ||
                    ch.getCategory().toLowerCase().contains(currentSearchQuery);

            if (matchesCat && matchesSearch) {
                filteredChannels.add(ch);
            }
        }

        channelAdapter.updateData(filteredChannels);
    }

    private void showAddChannelDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_channel, null);
        final EditText etId = view.findViewById(R.id.etAddChannelId);
        final EditText etName = view.findViewById(R.id.etAddChannelName);

        builder.setView(view)
                .setPositiveButton(R.string.btn_add_channel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String rawInput = etId.getText().toString().trim();
                        String channelName = etName.getText().toString().trim();

                        String channelId = extractChannelId(rawInput);
                        if (channelId.isEmpty()) {
                            Toast.makeText(SettingsActivity.this, R.string.invalid_channel_id, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (channelName.isEmpty()) {
                            channelName = "ערוץ " + channelId.substring(0, Math.min(6, channelId.length()));
                        }

                        dataManager.addCustomChannel(channelId, channelName);
                        Toast.makeText(SettingsActivity.this, R.string.channel_added, Toast.LENGTH_SHORT).show();
                        loadChannelsData();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create()
                .show();
    }

    private String extractChannelId(String input) {
        if (input == null || input.isEmpty()) return "";
        if (input.startsWith("UC") && input.length() >= 20) {
            return input;
        }
        if (input.contains("channel/")) {
            String[] parts = input.split("channel/");
            if (parts.length > 1) {
                String idPart = parts[1];
                if (idPart.contains("?")) idPart = idPart.split("\\?")[0];
                if (idPart.contains("/")) idPart = idPart.split("/")[0];
                return idPart;
            }
        }
        return input;
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        final EditText etNewPassword = view.findViewById(R.id.etNewPassword);

        builder.setView(view)
                .setPositiveButton(R.string.btn_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newPass = etNewPassword.getText().toString().trim();
                        if (!newPass.isEmpty()) {
                            dataManager.setPassword(newPass);
                            Toast.makeText(SettingsActivity.this, R.string.password_updated, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create()
                .show();
    }
}
