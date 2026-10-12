package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private GridView gvVideos;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private EditText etSearchVideos;
    private LinearLayout llChannelChips;
    private LinearLayout llTypeChips;
    private ImageButton btnSettings;
    private ImageButton btnRefresh;

    private DataManager dataManager;
    private VideoAdapter videoAdapter;

    private List<VideoModel> masterVideoList = new ArrayList<>();
    private List<VideoModel> filteredVideoList = new ArrayList<>();
    private String selectedChannelIdFilter = "ALL";
    private String selectedTypeFilter = "ALL";
    private String currentQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dataManager = DataManager.getInstance(this);

        initViews();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupTypeChips();
        setupChannelChips();
        loadApprovedVideos();
    }

    private void initViews() {
        gvVideos = findViewById(R.id.gvVideos);
        progressBar = findViewById(R.id.progressBar);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        etSearchVideos = findViewById(R.id.etSearchVideos);
        llChannelChips = findViewById(R.id.llChannelChips);
        llTypeChips = findViewById(R.id.llTypeChips);
        btnSettings = findViewById(R.id.btnSettings);
        btnRefresh = findViewById(R.id.btnRefresh);

        videoAdapter = new VideoAdapter(this, filteredVideoList);
        gvVideos.setAdapter(videoAdapter);
    }

    private void setupListeners() {
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPasswordDialog();
            }
        });

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadApprovedVideos();
            }
        });

        etSearchVideos.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString().trim().toLowerCase();
                filterVideos();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        gvVideos.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= filteredVideoList.size()) return;
                VideoModel video = filteredVideoList.get(position);
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                intent.putExtra("VIDEO_ID", video.getId());
                intent.putExtra("VIDEO_TITLE", video.getTitle());
                intent.putExtra("CHANNEL_TITLE", video.getChannelTitle());
                intent.putExtra("PUBLISHED_DATE", video.getPublishedDate());
                intent.putExtra("DESCRIPTION", video.getDescription());
                startActivity(intent);
            }
        });
    }

    private void setupTypeChips() {
        llTypeChips.removeAllViews();
        addTypeChip(getString(R.string.type_all), "ALL", selectedTypeFilter.equals("ALL"));
        addTypeChip(getString(R.string.type_regular), "REGULAR", selectedTypeFilter.equals("REGULAR"));
        addTypeChip(getString(R.string.type_shorts), "SHORTS", selectedTypeFilter.equals("SHORTS"));
    }

    private void addTypeChip(final String label, final String typeKey, boolean isSelected) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(13);
        chip.setPadding(28, 14, 28, 14);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(12);
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
                selectedTypeFilter = typeKey;
                setupTypeChips();
                filterVideos();
            }
        });

        llTypeChips.addView(chip);
    }

    private void setupChannelChips() {
        llChannelChips.removeAllViews();

        List<ChannelModel> approvedChannels = dataManager.getApprovedChannels();

        addChip(getString(R.string.all_channels), "ALL", selectedChannelIdFilter.equals("ALL"));

        for (ChannelModel ch : approvedChannels) {
            addChip(ch.getName(), ch.getId(), selectedChannelIdFilter.equals(ch.getId()));
        }
    }

    private void addChip(final String label, final String channelId, boolean isSelected) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(13);
        chip.setPadding(28, 14, 28, 14);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(12);
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
                selectedChannelIdFilter = channelId;
                setupChannelChips();
                filterVideos();
            }
        });

        llChannelChips.addView(chip);
    }

    private void loadApprovedVideos() {
        List<ChannelModel> approvedChannels = dataManager.getApprovedChannels();

        if (approvedChannels.isEmpty()) {
            masterVideoList.clear();
            filteredVideoList.clear();
            videoAdapter.updateData(filteredVideoList);
            tvEmptyState.setText(R.string.no_approved_channels);
            tvEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);

        RssFetcher.fetchVideosForChannels(approvedChannels, new RssFetcher.FetchCallback() {
            @Override
            public void onSuccess(List<VideoModel> videos) {
                progressBar.setVisibility(View.GONE);
                masterVideoList = videos;
                filterVideos();
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterVideos() {
        filteredVideoList.clear();

        for (VideoModel video : masterVideoList) {
            boolean matchesChannel = selectedChannelIdFilter.equals("ALL") || video.getChannelId().equals(selectedChannelIdFilter);

            boolean matchesType = true;
            if (selectedTypeFilter.equals("REGULAR")) {
                matchesType = !video.isShort();
            } else if (selectedTypeFilter.equals("SHORTS")) {
                matchesType = video.isShort();
            }

            boolean matchesQuery = currentQuery.isEmpty() ||
                    video.getTitle().toLowerCase().contains(currentQuery) ||
                    video.getChannelTitle().toLowerCase().contains(currentQuery);

            if (matchesChannel && matchesType && matchesQuery) {
                filteredVideoList.add(video);
            }
        }

        videoAdapter.updateData(filteredVideoList);

        if (filteredVideoList.isEmpty()) {
            tvEmptyState.setText(R.string.no_videos);
            tvEmptyState.setVisibility(View.VISIBLE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
        }
    }

    private void showPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_password, null);
        final EditText etPassword = view.findViewById(R.id.etPassword);

        builder.setView(view)
                .setPositiveButton(R.string.btn_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String inputPass = etPassword.getText().toString().trim();
                        if (dataManager.checkPassword(inputPass)) {
                            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                            startActivity(intent);
                        } else {
                            Toast.makeText(MainActivity.this, R.string.incorrect_password, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create()
                .show();
    }
}
