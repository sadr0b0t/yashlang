package su.sadrobot.yashlang.view;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
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
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.controller.ThumbManager;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;

public class VideoItemPagedListAdapter extends PagedListAdapter<VideoItem, VideoItemPagedListAdapter.VideoItemViewHolder> {
    // https://guides.codepath.com/android/Paging-Library-Guide
    // https://github.com/codepath/android_guides/wiki/Using-the-RecyclerView#using-with-listadapter
    // https://developer.android.com/reference/android/support/v7/recyclerview/extensions/ListAdapter.html
    // https://developer.android.com/topic/libraries/architecture/paging/

    public static int ORIENTATION_VERTICAL = 0;
    public static int ORIENTATION_HORIZONTAL = 1;

    private final Activity context;
    private final OnListItemClickListener<VideoItem> onItemClickListener;
    private final ListItemSwitchController<VideoItem> itemSwitchController;
    private int orientation = ORIENTATION_VERTICAL;

    //private ExecutorService dbQueryExecutor = Executors.newFixedThreadPool(10);
    //private ExecutorService thumbLoaderExecutor = Executors.newFixedThreadPool(10);

    // Извлекать задания на выполнение не в режиме очереди, а в режиме стека: последнее добавленное
    // задание отправляется на выполнение первым: этот режим лучше подходит при прокрутке списка, т.к.
    // пользователь видит часть иконок, до которых он уже домотал, их нужно загрузить в первую очередь,
    // а не ждать, пока загрузятся те иконки, которые уже пролистали раньше (это важнее для загрузки
    // иконок через интернет, но для обращение к базе данных тоже так сделаем)

    // код создания ThreadPool из Executors.newFixedThreadPool(10)
    private final ExecutorService dbQueryExecutor = new ThreadPoolExecutor(10, 10, 0L,TimeUnit.MILLISECONDS,
            new LinkedBlockingDeque<Runnable>() {
                @Override
                public Runnable take() throws InterruptedException {
                    return super.takeLast();
                }
            });

    // код создания ThreadPool из Executors.newFixedThreadPool(10)
    private final ExecutorService thumbLoaderExecutor = new ThreadPoolExecutor(10, 10, 0L,TimeUnit.MILLISECONDS,
                    new LinkedBlockingDeque<Runnable>() {
        @Override
        public Runnable take() throws InterruptedException {
            return super.takeLast();
        }
    });

    public static class VideoItemViewHolder extends RecyclerView.ViewHolder {
        final TextView nameTxt;
        final TextView playlistTxt;
        final TextView durationTxt;
        final View hasOfflineView;
        final View starredView;
        final ProgressBar watchProgress;
        final ImageView thumbImg;
        final Switch onoffSwitch;

        public VideoItemViewHolder(final View itemView) {
            super(itemView);
            nameTxt = itemView.findViewById(R.id.video_name_txt);
            playlistTxt = itemView.findViewById(R.id.video_pl_txt);
            durationTxt = itemView.findViewById(R.id.video_duration_txt);
            hasOfflineView = itemView.findViewById(R.id.video_has_offline_view);
            starredView = itemView.findViewById(R.id.video_starred_view);
            watchProgress = itemView.findViewById(R.id.video_progress);
            thumbImg = itemView.findViewById(R.id.video_thumb_img);
            onoffSwitch = itemView.findViewById(R.id.video_onoff_switch);
        }
    }

    private static final DiffUtil.ItemCallback<VideoItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<VideoItem>() {
                @Override
                public boolean areItemsTheSame(VideoItem oldItem, VideoItem newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(VideoItem oldItem, VideoItem newItem) {
                    // вставим небольшой хак, чтобы не моргала иконка обновленного элемента -
                    // перекинем кэш загруженной иконки со старого элемента на новый элемент,
                    // раз уж он к нам сюда пришел
                    newItem.setThumbBitmap(oldItem.getThumbBitmap());

                    // Здесь следует перечислить все поля, которые в том или ином виде
                    // отображены на элементе списка: например, прогресс (pausedAt), или
                    // статус "любомое" (starred). В таком случае при изменении этих полей
                    // (например, при сохранении новой позиции или переключении состояния
                    // звездочки) будет автоматически изменяться состояние элемента в загруженном
                    // списке (на текущем экране - например, в рекомендациях на экране плеера,
                    // или в списке на экране, из которого был открыт плеер)
                    // Но внесем сюда только изменяемые позиции (имя или адрес ролика не изменяются,
                    // поэтому сравнивать дополнительно их нет большого смысла)
                    return (oldItem.isStarred() == newItem.isStarred() &&
                            oldItem.getPausedAt() == newItem.getPausedAt() &&
                            oldItem.isHasOffline() == newItem.isHasOffline() &&
                            oldItem.isBlacklisted() == newItem.isBlacklisted() &&
                            oldItem.isEnabled() == newItem.isEnabled() &&
                            oldItem.getViewCount() == newItem.getViewCount() &&
                            ( (oldItem.getLastViewedDate() != null && newItem.getLastViewedDate() != null &&
                                    oldItem.getLastViewedDate().equals(newItem.getLastViewedDate())) ||
                                    (oldItem.getLastViewedDate() == null && newItem.getLastViewedDate() == null) ) &&
                            ( (oldItem.getStarredDate() != null && newItem.getStarredDate() != null &&
                                    oldItem.getStarredDate().equals(newItem.getStarredDate())) ||
                                    (oldItem.getStarredDate() == null && newItem.getStarredDate() == null) ) );
                }
            };


    public VideoItemPagedListAdapter(final Activity context,
                                     final OnListItemClickListener<VideoItem> onItemClickListener,
                                     final ListItemSwitchController<VideoItem> itemSwitchController) {
        super(DIFF_CALLBACK);
        this.context = context;
        this.onItemClickListener = onItemClickListener;
        this.itemSwitchController = itemSwitchController;
    }

    public VideoItemPagedListAdapter(final Activity context,
                                     final OnListItemClickListener<VideoItem> onItemClickListener,
                                     final ListItemSwitchController<VideoItem> itemSwitchController,
                                     final int orientation) {
        super(DIFF_CALLBACK);
        this.context = context;
        this.onItemClickListener = onItemClickListener;
        this.itemSwitchController = itemSwitchController;
        this.orientation = orientation;
    }

    @NonNull
    @Override
    public VideoItemViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
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

        if (holder.playlistTxt != null) {
            if(item.getPlaylistInfo() != null) {
                holder.playlistTxt.setText(item.getPlaylistInfo().getName());
            } else if(item.getPlaylistId() != PlaylistInfo.ID_NONE) {
                dbQueryExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final PlaylistInfo plInfo = VideoDatabase.getDbInstance(context).
                                playlistInfoDao().getById(item.getPlaylistId());
                        item.setPlaylistInfo(plInfo);
                        if (plInfo != null) {
                            context.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    VideoItemPagedListAdapter.this.notifyItemChanged(holder.getBindingAdapterPosition());
                                }
                            });
                        }
                    }
                });
            }
        }

        if (holder.durationTxt != null) {
            long sec = item.getDuration();
            final String durStr = sec > 0 ?
                    (sec / 3600) > 0 ?
                        String.format("%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, (sec % 60)) :
                        String.format("%02d:%02d", (sec % 3600) / 60, (sec % 60))
                    : "[dur undef]";
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
                                VideoItemPagedListAdapter.this.notifyItemChanged(holder.getBindingAdapterPosition());
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
            if (holder.playlistTxt != null) {
                holder.playlistTxt.setEnabled(!item.isBlacklisted());
            }
            if (holder.durationTxt != null) {
                holder.durationTxt.setEnabled(!item.isBlacklisted());
            }
        } else {
            // состояние "вкл/выкл" будем брать не напрямую из ролика, а из itemSwitchController
            if (holder.nameTxt != null) {
                holder.nameTxt.setEnabled(itemSwitchController.isItemChecked(item));
            }
            if (holder.playlistTxt != null) {
                holder.playlistTxt.setEnabled(itemSwitchController.isItemChecked(item));
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

    // сделаем метод публичным
    @Override
    public VideoItem getItem(int position) {
        return super.getItem(position);
    }
}
