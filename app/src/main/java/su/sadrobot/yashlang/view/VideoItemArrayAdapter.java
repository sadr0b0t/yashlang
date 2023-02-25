package su.sadrobot.yashlang.view;

/*
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
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
import android.widget.ProgressBar;
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
import su.sadrobot.yashlang.controller.ThumbManager;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.util.StringFormatUtil;

public class VideoItemArrayAdapter extends RecyclerView.Adapter<VideoItemArrayAdapter.VideoItemViewHolder> {


    public static int ORIENTATION_VERTICAL = 0;
    public static int ORIENTATION_HORIZONTAL = 1;

    private Activity context;
    private final List<VideoItem> videoItems;
    private OnListItemClickListener<VideoItem> onItemClickListener;
    private final ListItemSwitchController<VideoItem> itemSwitchController;
    private int orientation;


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

    public static class VideoItemViewHolder extends RecyclerView.ViewHolder {
        final TextView nameTxt;
        final TextView durationTxt;
        final View hasOfflineView;
        final View starredView;
        final ProgressBar watchProgress;
        final ImageView thumbImg;
        final Switch onoffSwitch;

        public VideoItemViewHolder(final View itemView) {
            super(itemView);
            nameTxt = itemView.findViewById(R.id.video_name_txt);
            durationTxt = itemView.findViewById(R.id.video_duration_txt);
            hasOfflineView = itemView.findViewById(R.id.video_has_offline_view);
            starredView = itemView.findViewById(R.id.video_starred_view);
            watchProgress = itemView.findViewById(R.id.video_progress);
            thumbImg = itemView.findViewById(R.id.video_thumb_img);
            onoffSwitch = itemView.findViewById(R.id.video_onoff_switch);
        }
    }

    public VideoItemArrayAdapter(final Activity context,
                                 final List<VideoItem> videoItems,
                                 final OnListItemClickListener<VideoItem> onItemClickListener,
                                 final ListItemSwitchController<VideoItem> itemSwitchController,
                                 final int orientation) {
        this.context = context;
        this.videoItems = videoItems;
        this.onItemClickListener = onItemClickListener;
        this.itemSwitchController = itemSwitchController;
        this.orientation = orientation;
    }

    @NonNull
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

        if (holder.durationTxt != null) {
            final String durStr = StringFormatUtil.formatDuration(context, item.getDuration());
            holder.durationTxt.setText(durStr);
        }

        if (holder.hasOfflineView != null) {
            holder.hasOfflineView.setVisibility(item.isHasOffline() ? View.VISIBLE : View.GONE);
        }

        if (holder.starredView != null) {
            holder.starredView.setVisibility(item.isStarred() ? View.VISIBLE : View.GONE);
        }

        if (holder.watchProgress != null) {
            if(item.getDuration() > 0 && item.getPausedAt() > 5000) {
                holder.watchProgress.setMax(100);
                holder.watchProgress.setProgress( (int) ( ((float)item.getPausedAt() / ((float)item.getDuration()*1000.)) * 100. ) );

                holder.watchProgress.setVisibility(View.VISIBLE);
            } else {
                holder.watchProgress.setVisibility(View.GONE);
            }
        }

        if (holder.thumbImg != null) {
            if (item.getThumbBitmap() != null) {
                holder.thumbImg.setImageBitmap(item.getThumbBitmap());
            } else {
                holder.thumbImg.setImageResource(R.drawable.ic_yashlang_thumb);
                thumbLoaderExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final Bitmap thumb =
                                ThumbManager.getInstance().loadVideoThumb(context, item);
                        item.setThumbBitmap(thumb);
                        context.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                VideoItemArrayAdapter.this.notifyItemChanged(holder.getBindingAdapterPosition());
                            }
                        });
                    }
                });
            }
        }

        if (itemSwitchController == null) {
            // состояние "вкл/выкл" будем брать как флаг isBlacklisted для плейлиста
            if (holder.nameTxt != null) {
                holder.nameTxt.setEnabled(!item.isBlacklisted());
            }
            if (holder.durationTxt != null) {
                holder.durationTxt.setEnabled(!item.isBlacklisted());
            }
        } else {
            // состояние "вкл/выкл" будем брать не напрямую из ролика, а из itemSwitchController
            if (holder.nameTxt != null) {
                holder.nameTxt.setEnabled(itemSwitchController.isItemChecked(item));
            }
            if (holder.durationTxt != null) {
                holder.durationTxt.setEnabled(itemSwitchController.isItemChecked(item));
            }
        }

        if (holder.onoffSwitch != null) {
            // обнулить слушателя событий выключателя:
            // вот это важно здесь здесь, иначе не оберешься трудноуловимых глюков
            // в списках с прокруткой
            holder.onoffSwitch.setOnCheckedChangeListener(null);
            if (itemSwitchController == null) {
                // вот так - не передали слушателя вкл/выкл - прячем кнопку
                // немного не феншуй, зато пока не будем городить отдельный флаг
                holder.onoffSwitch.setVisibility(View.GONE);
            } else {
                holder.onoffSwitch.setVisibility(View.VISIBLE);

                holder.onoffSwitch.setChecked(itemSwitchController.isItemChecked(item));
                holder.onoffSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        itemSwitchController.onItemCheckedChanged(buttonView, holder.getBindingAdapterPosition(), item, isChecked);
                    }
                });

            }
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
        return videoItems.size();
    }

    public VideoItem getItem(int position) {
        return videoItems.get(position);
    }


    public void setContext(final Activity context) {
        this.context = context;
    }

    public void setOnItemClickListener(final OnListItemClickListener<VideoItem> onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }
}
