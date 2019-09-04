package su.sadrobot.yashlang.view;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * VideoItemPagedListAdapter.java is part of YaShlang.
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

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.controller.VideoThumbManager;
import su.sadrobot.yashlang.model.VideoItem;

public class VideoItemPagedListAdapter extends PagedListAdapter<VideoItem, VideoItemPagedListAdapter.VideoItemViewHolder> {
    // https://guides.codepath.com/android/Paging-Library-Guide
    // https://github.com/codepath/android_guides/wiki/Using-the-RecyclerView#using-with-listadapter
    // https://developer.android.com/reference/android/support/v7/recyclerview/extensions/ListAdapter.html
    // https://developer.android.com/topic/libraries/architecture/paging/

    public static int ORIENTATION_VERTICAL = 0;
    public static int ORIENTATION_HORIZONTAL = 1;

    private Activity context;
    private OnListItemClickListener<VideoItem> onItemClickListener;
    private OnListItemSwitchListener<VideoItem> onItemSwitchListener;
    private int orientation = ORIENTATION_VERTICAL;


    public static class VideoItemViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        ImageView thumb;
        Switch onoff;

        public VideoItemViewHolder(final View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.video_name_txt);
            thumb = itemView.findViewById(R.id.video_thumb_img);
            onoff = itemView.findViewById(R.id.video_onoff_switch);
        }
    }

    public static final DiffUtil.ItemCallback<VideoItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<VideoItem>() {
                @Override
                public boolean areItemsTheSame(VideoItem oldItem, VideoItem newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(VideoItem oldItem, VideoItem newItem) {
                    return (oldItem.getYtId().equals(newItem.getYtId()));
                }
            };


    public VideoItemPagedListAdapter(final Activity context,
                                     final OnListItemClickListener<VideoItem> onItemClickListener,
                                     final OnListItemSwitchListener<VideoItem> onItemSwitchListener) {
        super(DIFF_CALLBACK);
        this.context = context;
        this.onItemClickListener = onItemClickListener;
        this.onItemSwitchListener = onItemSwitchListener;
    }

    public VideoItemPagedListAdapter(final Activity context,
                                     final OnListItemClickListener<VideoItem> onItemClickListener,
                                     final OnListItemSwitchListener<VideoItem> onItemSwitchListener,
                                     final int orientation) {
        super(DIFF_CALLBACK);
        this.context = context;
        this.onItemClickListener = onItemClickListener;
        this.onItemSwitchListener = onItemSwitchListener;
        this.orientation = orientation;
    }

    @Override
    public VideoItemViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        final View v;
        if (orientation == ORIENTATION_VERTICAL) {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.video_list_item_vert, parent, false);
        } else {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.video_list_item_hor, parent, false);
        }
        return new VideoItemViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final VideoItemViewHolder holder, final int position) {
        final VideoItem item = getItem(position);
        if (item == null) {
            // [DONE]TO-DO: Для Союзмультфильма срабатывает:
            // null item at position: 200
            // null item at position: 201
            // null item at position: 202
            // null item at position: 203
            // null item at position: 204
            // null item at position: 205
            // null item at position: 220
            // null item at position: 700
            // null item at position: 701
            // DONE: на этих позиция мультики: Охота, Контакт, Конфлик, Когда-то давно и т.п.
            // т.е. в конечном итоге они в список попали, значит, это такая особенность
            // сгенерированного датасорса - время от времени генерировать события для позиций,
            // для которых getItem вернет null (возможно, при быстрой прокрутке)
            // ИТОГО РЕШЕНИЕ: игнорируем такие ситуации
            //System.out.println("##### VideoItemPagedListAdapter: null item at position: " + position);
            return;
        }

        if (holder.name != null) {
            holder.name.setText(item.getName());
            //holder.name.setEnabled(!item.isBlacklisted());
        }

        if (holder.thumb != null) {
            if (item.getThumbBitmap() != null) {
                holder.thumb.setImageBitmap(item.getThumbBitmap());
            } else {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final Bitmap thumb =
                                VideoThumbManager.getInstance().loadVideoThumb(context, item);
                        item.setThumbBitmap(thumb);
                        context.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                VideoItemPagedListAdapter.this.notifyDataSetChanged();
                            }
                        });
                    }
                }).start();
            }
        }

        if (holder.onoff != null) {
            // обнулить слушателя событий выключателя:
            // вот это важно здесь здесь, иначе не оберешься трудноуловимых глюков
            // в списках с прокруткой
            holder.onoff.setOnCheckedChangeListener(null);
            if (onItemSwitchListener == null) {
                // вот так - не передали слушателя вкл/выкл - прячем кнопку
                // немного не феншуй, зато пока не будем городить отдельный флаг
                holder.onoff.setVisibility(View.GONE);
            } else {
                holder.onoff.setVisibility(View.VISIBLE);

                holder.onoff.setChecked(!item.isBlacklisted());
                holder.onoff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (onItemSwitchListener != null) {
                            onItemSwitchListener.onItemCheckedChanged(buttonView, position, item, isChecked);
                        }
                    }
                });

            }
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(view, position, item);
                }
            }
        });

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(final View view) {
                if (onItemClickListener != null) {
                    return onItemClickListener.onItemLongClick(view, position, item);
                } else {
                    return false;
                }
            }
        });
    }
}
