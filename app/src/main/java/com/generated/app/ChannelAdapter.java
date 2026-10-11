package com.generated.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

public class ChannelAdapter extends BaseAdapter {

    private Context context;
    private List<ChannelModel> channels;
    private LayoutInflater inflater;
    private OnChannelToggleListener toggleListener;

    public interface OnChannelToggleListener {
        void onToggle(ChannelModel channel, boolean isApproved);
    }

    public ChannelAdapter(Context context, List<ChannelModel> channels, OnChannelToggleListener listener) {
        this.context = context;
        this.channels = channels;
        this.toggleListener = listener;
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
            holder.tvName = convertView.findViewById(R.id.tvChannelName);
            holder.tvCategory = convertView.findViewById(R.id.tvChannelCategory);
            holder.tvId = convertView.findViewById(R.id.tvChannelId);
            holder.switchApproved = convertView.findViewById(R.id.switchApproved);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final ChannelModel channel = channels.get(position);
        holder.tvName.setText(channel.getName());
        holder.tvCategory.setText(channel.getCategory());
        holder.tvId.setText("ID: " + channel.getId());

        holder.switchApproved.setOnCheckedChangeListener(null);
        holder.switchApproved.setChecked(channel.isApproved());

        holder.switchApproved.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                channel.setApproved(isChecked);
                if (toggleListener != null) {
                    toggleListener.onToggle(channel, isChecked);
                }
            }
        });

        return convertView;
    }

    private static class ViewHolder {
        TextView tvName;
        TextView tvCategory;
        TextView tvId;
        Switch switchApproved;
    }
}
