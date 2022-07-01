package su.sadrobot.yashlang.view;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * PlaylistInfoArrayAdapter.java is part of YaShlang.
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
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.controller.VideoThumbManager;
import su.sadrobot.yashlang.model.PlaylistInfo;

public class PlaylistInfoArrayAdapter extends RecyclerView.Adapter<PlaylistInfoArrayAdapter.PlaylistInfoViewHolder> {

    private final Activity context;
    private final List<PlaylistInfo> playlistInfos;
    private final OnListItemClickListener<PlaylistInfo> onItemClickListener;
    private final OnListItemSwitchListener<PlaylistInfo> onItemSwitchListener;
    private ListItemCheckedProvider<PlaylistInfo> itemCheckedProvider;

    //private ExecutorService thumbLoaderExecutor = Executors.newFixedThreadPool(10);

    // Извлекать задания на выполнение не в режиме очереди, а в режиме стека: последнее добавленное
    // задание отправляется на выполнение первым: этот режим лучше подходит при прокрутке списка, т.к.
    // пользователь видит часть иконок, до которых он уже домотал, их нужно загрузить в первую очередь,
    // а не ждать, пока загрузятся те иконки, которые уже пролистали раньше

    // код создания ThreadPool из Executors.newFixedThreadPool(10)
    private final ExecutorService thumbLoaderExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingDeque<Runnable>() {
                @Override
                public Runnable take() throws InterruptedException {
                    return super.takeLast();
                }
            });

    public static class PlaylistInfoViewHolder extends RecyclerView.ViewHolder {
        final TextView nameTxt;
        final TextView urlTxt;
        final ImageView thumbImg;
        final Switch onoffSwitch;
        final View checkedView;


        public PlaylistInfoViewHolder(final View itemView) {
            super(itemView);
            nameTxt = itemView.findViewById(R.id.playlist_name_txt);
            urlTxt = itemView.findViewById(R.id.playlist_url_txt);
            thumbImg = itemView.findViewById(R.id.playlist_thumb_img);
            onoffSwitch = itemView.findViewById(R.id.playlist_onoff_switch);
            checkedView = itemView.findViewById(R.id.playlist_checked_view);
        }
    }

    public PlaylistInfoArrayAdapter(final Activity context, final List<PlaylistInfo> playlistInfos,
                                    final OnListItemClickListener<PlaylistInfo> onItemClickListener,
                                    final OnListItemSwitchListener<PlaylistInfo> onItemSwitchListener) {
        this.context = context;
        this.playlistInfos = playlistInfos;
        this.onItemClickListener = onItemClickListener;
        this.onItemSwitchListener = onItemSwitchListener;
    }

    public PlaylistInfoArrayAdapter(final Activity context, final List<PlaylistInfo> playlistInfos,
                                    final OnListItemClickListener<PlaylistInfo> onItemClickListener,
                                    final OnListItemSwitchListener<PlaylistInfo> onItemSwitchListener,
                                    final ListItemCheckedProvider<PlaylistInfo> itemCheckedProvider) {
        this.context = context;
        this.playlistInfos = playlistInfos;
        this.onItemClickListener = onItemClickListener;
        this.onItemSwitchListener = onItemSwitchListener;
        this.itemCheckedProvider = itemCheckedProvider;
    }

    @NonNull
    @Override
    public PlaylistInfoViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        final View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.playlist_info_list_item, parent, false);
        return new PlaylistInfoViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final PlaylistInfoViewHolder holder, final int position) {
        final PlaylistInfo item = playlistInfos.get(position);

        holder.nameTxt.setText(item.getName());
        holder.urlTxt.setText(item.getUrl().replaceFirst(
                "https://", "").replaceFirst("www.", ""));

        if (item.getThumbBitmap() != null) {
            holder.thumbImg.setImageBitmap(item.getThumbBitmap());
        } else {
            holder.thumbImg.setImageResource(R.drawable.ic_yashlang_thumb);
            thumbLoaderExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap thumb =
                            VideoThumbManager.getInstance().loadPlaylistThumb(context, item.getThumbUrl());
                    item.setThumbBitmap(thumb);
                    context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            PlaylistInfoArrayAdapter.this.notifyItemChanged(holder.getBindingAdapterPosition());
                        }
                    });
                }
            });
        }

        if (onItemSwitchListener != null) {
            holder.nameTxt.setEnabled(onItemSwitchListener.isItemChecked(item));
            holder.urlTxt.setEnabled(onItemSwitchListener.isItemChecked(item));
            holder.checkedView.setEnabled(onItemSwitchListener.isItemChecked(item));
        } else {
            holder.nameTxt.setEnabled(item.isEnabled());
            holder.urlTxt.setEnabled(item.isEnabled());
            holder.checkedView.setEnabled(item.isEnabled());
        }

        // обнулить слушателя событий выключателя:
        // вот это важно здесь, иначе не оберешься трудноуловимых глюков в списках с прокруткой
        holder.onoffSwitch.setOnCheckedChangeListener(null);
        if (onItemSwitchListener == null || !onItemSwitchListener.showItemCheckbox(item)) {
            // вот так - не передали слушателя вкл/выкл - прячем кнопку
            // немного не феншуй, зато пока не будем городить отдельный флаг
            holder.onoffSwitch.setVisibility(View.GONE);
        } else {
            holder.onoffSwitch.setVisibility(View.VISIBLE);

            holder.onoffSwitch.setChecked(onItemSwitchListener.isItemChecked(item));
            holder.onoffSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    onItemSwitchListener.onItemCheckedChanged(buttonView, holder.getBindingAdapterPosition(), item, isChecked);
                    notifyItemChanged(holder.getBindingAdapterPosition());
                }
            });
        }

        if (itemCheckedProvider != null && itemCheckedProvider.isItemChecked(item)) {
            holder.checkedView.setVisibility(View.VISIBLE);
        } else {
            holder.checkedView.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(view, holder.getBindingAdapterPosition(), item);
                }
            }
        });

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(final View view) {
                if (onItemClickListener != null) {
                    return onItemClickListener.onItemLongClick(view, holder.getBindingAdapterPosition(), item);
                } else {
                    return false;
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return playlistInfos.size();
    }
}
