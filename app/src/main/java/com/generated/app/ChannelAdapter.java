package com.generated.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

public class ChannelAdapter extends BaseAdapter {

    private Context context;
    private List<ChannelModel> channels;
    private LayoutInflater inflater;
    private OnChannelActionListener actionListener;

    public interface OnChannelActionListener {
        void onToggle(ChannelModel channel, boolean isApproved);
        void onDelete(ChannelModel channel);
    }

    public ChannelAdapter(Context context, List<ChannelModel> channels, OnChannelActionListener listener) {
        this.context = context;
        this.channels = channels;
        this.actionListener = listener;
        this.inflater = LayoutInflater.from(context);
    }

    public void updateData(List<ChannelModel> newChannels) {
        this.channels = newChannels;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return channels != null ? channels.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        return channels.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_channel, parent, false);
            holder = new ViewHolder();
            holder.ivAvatar = convertView.findViewById(R.id.ivChannelAvatar);
            holder.tvName = convertView.findViewById(R.id.tvChannelName);
            holder.tvCategory = convertView.findViewById(R.id.tvChannelCategory);
            holder.tvId = convertView.findViewById(R.id.tvChannelId);
            holder.switchApproved = convertView.findViewById(R.id.switchApproved);
            holder.btnDelete = convertView.findViewById(R.id.btnDeleteChannel);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final ChannelModel channel = channels.get(position);
        holder.tvName.setText(channel.getName());
        holder.tvCategory.setText(channel.getCategory());
        holder.tvId.setText("ID: " + channel.getId());

        if (channel.getAvatarUrl() != null && !channel.getAvatarUrl().isEmpty()) {
            ImageLoader.getInstance().displayImage(channel.getAvatarUrl(), holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_channel_default);
        }

        holder.switchApproved.setOnCheckedChangeListener(null);
        holder.switchApproved.setChecked(channel.isApproved());

        holder.switchApproved.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                channel.setApproved(isChecked);
                if (actionListener != null) {
                    actionListener.onToggle(channel, isChecked);
                }
            }
        });

        holder.btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (actionListener != null) {
                    actionListener.onDelete(channel);
                }
            }
        });

        return convertView;
    }

    private static class ViewHolder {
        ImageView ivAvatar;
        TextView tvName;
        TextView tvCategory;
        TextView tvId;
        Switch switchApproved;
        ImageButton btnDelete;
    }
}
