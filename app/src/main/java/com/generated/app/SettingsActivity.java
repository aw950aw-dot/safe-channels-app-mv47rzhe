package com.generated.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
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

        channelAdapter = new ChannelAdapter(this, filteredChannels, new ChannelAdapter.OnChannelActionListener() {
            @Override
            public void onToggle(ChannelModel channel, boolean isApproved) {
                dataManager.setChannelApproved(channel.getId(), isApproved);
            }

            @Override
            public void onDelete(ChannelModel channel) {
                confirmDeleteChannel(channel);
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
                showSearchAddChannelDialog();
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
        addCategoryChip(getString(R.string.category_custom), "ערוץ מותאם", selectedCategory.equals("ערוץ מותאם"));
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

    private void confirmDeleteChannel(final ChannelModel channel) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_channel_title)
                .setMessage(String.format(getString(R.string.delete_channel_confirm), channel.getName()))
                .setPositiveButton(R.string.btn_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dataManager.deleteChannel(channel.getId());
                        Toast.makeText(SettingsActivity.this, R.string.channel_deleted, Toast.LENGTH_SHORT).show();
                        loadChannelsData();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showSearchAddChannelDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search_channel, null);

        final EditText etQuery = dialogView.findViewById(R.id.etSearchChannelQuery);
        final Button btnSearch = dialogView.findViewById(R.id.btnDoSearch);
        final ListView lvResults = dialogView.findViewById(R.id.lvSearchResults);
        final ProgressBar pbLoading = dialogView.findViewById(R.id.pbSearchLoading);
        final TextView tvStatus = dialogView.findViewById(R.id.tvSearchStatus);

        final List<ChannelSearchHelper.ChannelSearchResult> searchResults = new ArrayList<>();
        final SearchResultsAdapter adapter = new SearchResultsAdapter(this, searchResults);
        lvResults.setAdapter(adapter);

        builder.setView(dialogView)
                .setNegativeButton(R.string.btn_cancel, null);

        final AlertDialog dialog = builder.create();

        adapter.setOnAddClickListener(new SearchResultsAdapter.OnAddClickListener() {
            @Override
            public void onAdd(ChannelSearchHelper.ChannelSearchResult item) {
                if (dataManager.channelExists(item.getId())) {
                    Toast.makeText(SettingsActivity.this, R.string.channel_already_exists, Toast.LENGTH_SHORT).show();
                } else {
                    dataManager.addCustomChannel(item.getId(), item.getName(), item.getAvatarUrl());
                    Toast.makeText(SettingsActivity.this, R.string.channel_added, Toast.LENGTH_SHORT).show();
                    loadChannelsData();
                    dialog.dismiss();
                }
            }
        });

        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = etQuery.getText().toString().trim();
                if (query.isEmpty()) {
                    Toast.makeText(SettingsActivity.this, "נא להקליד שם ערוץ", Toast.LENGTH_SHORT).show();
                    return;
                }

                pbLoading.setVisibility(View.VISIBLE);
                tvStatus.setVisibility(View.GONE);
                searchResults.clear();
                adapter.notifyDataSetChanged();

                ChannelSearchHelper.searchChannels(query, new ChannelSearchHelper.SearchCallback() {
                    @Override
                    public void onSuccess(List<ChannelSearchHelper.ChannelSearchResult> results) {
                        pbLoading.setVisibility(View.GONE);
                        if (results.isEmpty()) {
                            tvStatus.setText(R.string.no_channel_results);
                            tvStatus.setVisibility(View.VISIBLE);
                        } else {
                            searchResults.addAll(results);
                            adapter.notifyDataSetChanged();
                            tvStatus.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        pbLoading.setVisibility(View.GONE);
                        tvStatus.setText(message);
                        tvStatus.setVisibility(View.VISIBLE);
                    }
                });
            }
        });

        dialog.show();
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);

        final EditText etCurrent = view.findViewById(R.id.etCurrentPassword);
        final EditText etNew = view.findViewById(R.id.etNewPassword);
        final EditText etConfirm = view.findViewById(R.id.etConfirmPassword);

        builder.setView(view)
                .setPositiveButton(R.string.btn_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String currentPass = etCurrent.getText().toString().trim();
                        String newPass = etNew.getText().toString().trim();
                        String confirmPass = etConfirm.getText().toString().trim();

                        if (!dataManager.checkPassword(currentPass)) {
                            Toast.makeText(SettingsActivity.this, R.string.incorrect_password, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (newPass.isEmpty()) {
                            Toast.makeText(SettingsActivity.this, R.string.password_empty, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (!newPass.equals(confirmPass)) {
                            Toast.makeText(SettingsActivity.this, R.string.passwords_dont_match, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        dataManager.setPassword(newPass);
                        Toast.makeText(SettingsActivity.this, R.string.password_updated, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create()
                .show();
    }

    private static class SearchResultsAdapter extends BaseAdapter {
        public interface OnAddClickListener {
            void onAdd(ChannelSearchHelper.ChannelSearchResult item);
        }

        private Context context;
        private List<ChannelSearchHelper.ChannelSearchResult> list;
        private LayoutInflater inflater;
        private OnAddClickListener onAddClickListener;

        public SearchResultsAdapter(Context context, List<ChannelSearchHelper.ChannelSearchResult> list) {
            this.context = context;
            this.list = list;
            this.inflater = LayoutInflater.from(context);
        }

        public void setOnAddClickListener(OnAddClickListener listener) {
            this.onAddClickListener = listener;
        }

        @Override
        public int getCount() { return list != null ? list.size() : 0; }

        @Override
        public Object getItem(int position) { return list.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_search_channel, parent, false);
                holder = new ViewHolder();
                holder.ivAvatar = convertView.findViewById(R.id.ivSearchChannelAvatar);
                holder.tvName = convertView.findViewById(R.id.tvSearchChannelName);
                holder.tvDesc = convertView.findViewById(R.id.tvSearchChannelDesc);
                holder.btnAdd = convertView.findViewById(R.id.btnAddThisChannel);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final ChannelSearchHelper.ChannelSearchResult item = list.get(position);
            holder.tvName.setText(item.getName());
            holder.tvDesc.setText(!item.getDescription().isEmpty() ? item.getDescription() : ("ID: " + item.getId()));

            if (item.getAvatarUrl() != null && !item.getAvatarUrl().isEmpty()) {
                ImageLoader.getInstance().displayImage(item.getAvatarUrl(), holder.ivAvatar);
            } else {
                holder.ivAvatar.setImageResource(R.drawable.ic_channel_default);
            }

            holder.btnAdd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onAddClickListener != null) {
                        onAddClickListener.onAdd(item);
                    }
                }
            });

            return convertView;
        }

        private static class ViewHolder {
            ImageView ivAvatar;
            TextView tvName;
            TextView tvDesc;
            Button btnAdd;
        }
    }
}
