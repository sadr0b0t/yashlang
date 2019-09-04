package su.sadrobot.yashlang.view;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * VideoItemArrayAdapter.java is part of YaShlang.
 *
 * YaShlang is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YaShlang is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with YaShlang.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.model.VideoItem;

public class VideoItemArrayAdapter extends RecyclerView.Adapter {


    private List<VideoItem> videoItems;
    private OnListItemClickListener<VideoItem> onItemClickListener;

    public class VideoItemViewHolder extends RecyclerView.ViewHolder {
        TextView name;

        public VideoItemViewHolder(final View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.video_name_txt);
        }
    }

    public VideoItemArrayAdapter(final List<VideoItem> videoItems,
                                 final OnListItemClickListener<VideoItem> onItemClickListener) {
        this.videoItems = videoItems;
        this.onItemClickListener = onItemClickListener;
    }

    @Override
    public VideoItemViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        final View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.video_list_item_vert, parent, false);
        return new VideoItemViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int position) {
        ((VideoItemViewHolder)holder).name.setText(
                videoItems.get(position).getName() + ":" + videoItems.get(position).getYtId());


        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                if(onItemClickListener != null) {
                    onItemClickListener.onItemClick(view, position, videoItems.get(position));
                }
            }
        });

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(final View view) {
                if(onItemClickListener != null) {
                    return onItemClickListener.onItemLongClick(view, position, videoItems.get(position));
                } else {
                    return false;
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return videoItems.size();
    }
}
