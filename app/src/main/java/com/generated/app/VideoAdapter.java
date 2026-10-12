package com.generated.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class VideoAdapter extends BaseAdapter {

    private Context context;
    private List<VideoModel> videos;
    private LayoutInflater inflater;

    public VideoAdapter(Context context, List<VideoModel> videos) {
        this.context = context;
        this.videos = videos;
        this.inflater = LayoutInflater.from(context);
    }

    public void updateData(List<VideoModel> newVideos) {
        this.videos = newVideos;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return videos != null ? videos.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        return videos.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_video, parent, false);
            holder = new ViewHolder();
            holder.ivThumbnail = convertView.findViewById(R.id.ivThumbnail);
            holder.tvTitle = convertView.findViewById(R.id.tvVideoTitle);
            holder.tvChannel = convertView.findViewById(R.id.tvChannelTitle);
            holder.tvDate = convertView.findViewById(R.id.tvPublishDate);
            holder.tvDurationBadge = convertView.findViewById(R.id.tvDurationBadge);
            holder.tvShortsBadge = convertView.findViewById(R.id.tvShortsBadge);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        VideoModel video = videos.get(position);
        holder.tvTitle.setText(video.getTitle());
        holder.tvChannel.setText(video.getChannelTitle());
        holder.tvDate.setText(video.getPublishedDate());

        if (video.isShort()) {
            holder.tvShortsBadge.setVisibility(View.VISIBLE);
            holder.tvDurationBadge.setVisibility(View.GONE);
        } else if (!video.getFormattedDuration().isEmpty()) {
            holder.tvDurationBadge.setText(video.getFormattedDuration());
            holder.tvDurationBadge.setVisibility(View.VISIBLE);
            holder.tvShortsBadge.setVisibility(View.GONE);
        } else {
            holder.tvDurationBadge.setVisibility(View.GONE);
            holder.tvShortsBadge.setVisibility(View.GONE);
        }

        ImageLoader.getInstance().displayImage(video.getThumbnailUrl(), holder.ivThumbnail);

        return convertView;
    }

    private static class ViewHolder {
        ImageView ivThumbnail;
        TextView tvTitle;
        TextView tvChannel;
        TextView tvDate;
        TextView tvDurationBadge;
        TextView tvShortsBadge;
    }
}
