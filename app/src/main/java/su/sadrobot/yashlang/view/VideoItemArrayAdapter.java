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
import su.sadrobot.yashlang.model.VideoItem;

public class VideoItemArrayAdapter extends RecyclerView.Adapter<VideoItemArrayAdapter.VideoItemViewHolder> {


    public static int ORIENTATION_VERTICAL = 0;
    public static int ORIENTATION_HORIZONTAL = 1;

    private Activity context;
    private List<VideoItem> videoItems;
    private OnListItemClickListener<VideoItem> onItemClickListener;
    private OnListItemSwitchListener<VideoItem> onItemSwitchListener;
    private int orientation = ORIENTATION_VERTICAL;


    //private ExecutorService thumbLoaderExecutor = Executors.newFixedThreadPool(10);

    // Извлекать задания на выполнение не в режиме очереди, а в режиме стека: последнее добавленное
    // задание отправляется на выполнение первым: этот режим лучше подходит при прокрутке списка, т.к.
    // пользователь видит часть иконок, до которых он уже домотал, их нужно загрузить в первую очередь,
    // а не ждать, пока загрузятся те иконки, которые уже пролистали раньше

    // код создания ThreadPool из Executors.newFixedThreadPool(10)
    private ExecutorService thumbLoaderExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingDeque<Runnable>() {
                @Override
                public Runnable take() throws InterruptedException {
                    return super.takeLast();
                }
            });

    public static class VideoItemViewHolder extends RecyclerView.ViewHolder {
        TextView nameTxt;
        TextView durationTxt;
        ImageView thumbImg;
        Switch onoffSwitch;

        public VideoItemViewHolder(final View itemView) {
            super(itemView);
            nameTxt = itemView.findViewById(R.id.video_name_txt);
            durationTxt = itemView.findViewById(R.id.video_duration_txt);
            thumbImg = itemView.findViewById(R.id.video_thumb_img);
            onoffSwitch = itemView.findViewById(R.id.video_onoff_switch);
        }
    }

    public VideoItemArrayAdapter(final Activity context,
                                 final List<VideoItem> videoItems,
                                 final OnListItemClickListener<VideoItem> onItemClickListener,
                                 final OnListItemSwitchListener<VideoItem> onItemSwitchListener) {
        this.context = context;
        this.videoItems = videoItems;
        this.onItemClickListener = onItemClickListener;
        this.onItemSwitchListener = onItemSwitchListener;
    }

    public VideoItemArrayAdapter(final Activity context,
                                 final List<VideoItem> videoItems,
                                 final OnListItemClickListener<VideoItem> onItemClickListener,
                                 final OnListItemSwitchListener<VideoItem> onItemSwitchListener,
                                 final int orientation) {
        this.context = context;
        this.videoItems = videoItems;
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

        if (holder.nameTxt != null) {
            holder.nameTxt.setText(item.getName());
            //holder.name.setEnabled(!item.isBlacklisted());
        }

        if(holder.durationTxt != null) {
            long sec = item.getDuration();
            final String durStr = sec > 0 ?
                    (sec / 3600) > 0 ?
                            String.format("%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, (sec % 60)) :
                            String.format("%02d:%02d", (sec % 3600) / 60, (sec % 60))
                    : "[dur undef]";
            holder.durationTxt.setText(durStr);
        }

        if (holder.thumbImg != null) {
            if (item.getThumbBitmap() != null) {
                holder.thumbImg.setImageBitmap(item.getThumbBitmap());
            } else {
                thumbLoaderExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final Bitmap thumb =
                                VideoThumbManager.getInstance().loadVideoThumb(context, item);
                        item.setThumbBitmap(thumb);
                        context.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                VideoItemArrayAdapter.this.notifyDataSetChanged();
                            }
                        });
                    }
                });
            }
        }

        if (holder.onoffSwitch != null) {
            // обнулить слушателя событий выключателя:
            // вот это важно здесь здесь, иначе не оберешься трудноуловимых глюков
            // в списках с прокруткой
            holder.onoffSwitch.setOnCheckedChangeListener(null);
            if (onItemSwitchListener == null) {
                // вот так - не передали слушателя вкл/выкл - прячем кнопку
                // немного не феншуй, зато пока не будем городить отдельный флаг
                holder.onoffSwitch.setVisibility(View.GONE);
            } else {
                holder.onoffSwitch.setVisibility(View.VISIBLE);

                holder.onoffSwitch.setChecked(!item.isBlacklisted());
                holder.onoffSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
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

    @Override
    public int getItemCount() {
        return videoItems.size();
    }

    public VideoItem getItem(int position) {
        return videoItems.get(position);
    }
}
