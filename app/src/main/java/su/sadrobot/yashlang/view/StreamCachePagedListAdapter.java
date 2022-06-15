package su.sadrobot.yashlang.view;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2022.
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import su.sadrobot.yashlang.ConfigOptions;
import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.controller.StreamCacheFsManager;
import su.sadrobot.yashlang.controller.VideoThumbManager;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.StreamCache;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.util.StringFormatUtil;

public class StreamCachePagedListAdapter extends PagedListAdapter<StreamCache, StreamCachePagedListAdapter.StreamCacheViewHolder> {
    // https://guides.codepath.com/android/Paging-Library-Guide
    // https://github.com/codepath/android_guides/wiki/Using-the-RecyclerView#using-with-listadapter
    // https://developer.android.com/reference/android/support/v7/recyclerview/extensions/ListAdapter.html
    // https://developer.android.com/topic/libraries/architecture/paging/

    private final Activity context;
    private final OnListItemClickListener<StreamCache> onItemClickListener;
    private final OnListItemProgressControlListener<StreamCache> onItemProgressControlListener;

    //private ExecutorService dbQueryExecutor = Executors.newFixedThreadPool(10);
    //private ExecutorService thumbLoaderExecutor = Executors.newFixedThreadPool(10);

    // Извлекать задания на выполнение не в режиме очереди, а в режиме стека: последнее добавленное
    // задание отправляется на выполнение первым: этот режим лучше подходит при прокрутке списка, т.к.
    // пользователь видит часть иконок, до которых он уже домотал, их нужно загрузить в первую очередь,
    // а не ждать, пока загрузятся те иконки, которые уже пролистали раньше (это важнее для загрузки
    // иконок через интернет, но для обращение к базе данных тоже так сделаем)

    // код создания ThreadPool из Executors.newFixedThreadPool(10)
    private final ExecutorService dbQueryExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingDeque<Runnable>() {
                @Override
                public Runnable take() throws InterruptedException {
                    return super.takeLast();
                }
            });

    // код создания ThreadPool из Executors.newFixedThreadPool(10)
    private final ExecutorService thumbLoaderExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingDeque<Runnable>() {
                @Override
                public Runnable take() throws InterruptedException {
                    return super.takeLast();
                }
            });

    public static class StreamCacheViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumbImg;
        final TextView nameTxt;
        final TextView playlistTxt;
        final TextView fileNameTxt;
        final TextView streamTypeTxt;
        final TextView streamResTxt;
        final TextView streamFormatTxt;
        final TextView streamSizeTxt;
        final TextView downloadStateTxt;
        final View downloadProgressSizeView;
        final TextView downloadProgressTxt;
        final ProgressBar downloadProgress;
        final TextView downloadStatusTxt;
        final TextView downloadErrorTxt;
        final ProgressBar downloadStartPauseProgress;
        final ImageButton downloadStartBtn;
        final ImageButton downloadPauseBtn;
        final ImageButton redownloadStreamBtn;
        final ImageButton deleteStreamBtn;

        public StreamCacheViewHolder(final View itemView) {
            super(itemView);
            thumbImg = itemView.findViewById(R.id.video_thumb_img);
            nameTxt = itemView.findViewById(R.id.video_name_txt);
            playlistTxt = itemView.findViewById(R.id.video_pl_txt);
            fileNameTxt = itemView.findViewById(R.id.file_name_txt);
            streamTypeTxt = itemView.findViewById(R.id.stream_type_txt);
            streamResTxt = itemView.findViewById(R.id.stream_res_txt);
            streamFormatTxt = itemView.findViewById(R.id.stream_format_txt);
            streamSizeTxt = itemView.findViewById(R.id.stream_size_txt);
            downloadStateTxt = itemView.findViewById(R.id.stream_download_state_txt);
            downloadProgressSizeView = itemView.findViewById(R.id.stream_download_progress_size_view);
            downloadProgressTxt = itemView.findViewById(R.id.stream_download_progress_txt);
            downloadProgress = itemView.findViewById(R.id.stream_download_progress);
            downloadStatusTxt = itemView.findViewById(R.id.stream_download_status_txt);
            downloadErrorTxt = itemView.findViewById(R.id.stream_download_error_txt);
            downloadStartPauseProgress = itemView.findViewById(R.id.start_pause_progress);
            downloadStartBtn = itemView.findViewById(R.id.start_btn);
            downloadPauseBtn = itemView.findViewById(R.id.pause_btn);
            redownloadStreamBtn = itemView.findViewById(R.id.redownload_btn);
            deleteStreamBtn = itemView.findViewById(R.id.delete_btn);
        }
    }

    private static final DiffUtil.ItemCallback<StreamCache> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<StreamCache>() {
                @Override
                public boolean areItemsTheSame(StreamCache oldItem, StreamCache newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(StreamCache oldItem, StreamCache newItem) {
                    return oldItem.getVideoId() == newItem.getVideoId() &&
                            oldItem.getStreamType().equals(newItem.getStreamType()) &&
                            oldItem.getStreamTypeEnum().equals(newItem.getStreamTypeEnum()) &&
                            oldItem.getStreamFormat().equals(newItem.getStreamFormat()) &&
                            oldItem.getStreamRes().equals(newItem.getStreamRes());
                }
            };

    public StreamCachePagedListAdapter(
            final Activity context,
            final OnListItemClickListener<StreamCache> onItemClickListener,
            final OnListItemProgressControlListener<StreamCache> onItemProgressControlListener) {
        super(DIFF_CALLBACK);
        this.context = context;
        this.onItemClickListener = onItemClickListener;
        this.onItemProgressControlListener = onItemProgressControlListener;
    }

    @NonNull
    @Override
    public StreamCacheViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        final View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.stream_cache_item, parent, false);
        return new StreamCacheViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final StreamCacheViewHolder holder, final int position) {
        final StreamCache item = getItem(position);
        if (item == null) {
            return;
        }

        if (item.getVideoItem() != null) {
            holder.nameTxt.setText(item.getVideoItem().getName());
        } else {
            dbQueryExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    item.setVideoItem(VideoDatabase.getDbInstance(context).videoItemDao().getById(item.getVideoId()));
                    context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            StreamCachePagedListAdapter.this.notifyDataSetChanged();
                        }
                    });
                }
            });
        }

        if (item.getPlaylistInfo() != null) {
            holder.playlistTxt.setText(item.getPlaylistInfo().getName());
        } else {
            dbQueryExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (item.getVideoItem() == null) {
                        item.setVideoItem(VideoDatabase.getDbInstance(context).videoItemDao().getById(item.getVideoId()));
                    }
                    // проверка на тот случай, если все-таки не нашли VideoItem в базе данных
                    // (строго говоря, такой вариант невозможен, за это отвечает движок базы данных)
                    if (item.getVideoItem() != null) {
                        final PlaylistInfo plInfo = VideoDatabase.getDbInstance(context).
                                playlistInfoDao().getById(item.getVideoItem().getPlaylistId());
                        item.setPlaylistInfo(plInfo);
                        if (plInfo != null) {
                            context.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    StreamCachePagedListAdapter.this.notifyDataSetChanged();
                                }
                            });
                        }
                    }
                }
            });
        }

        holder.fileNameTxt.setText(item.getFileName());

        if (item.getStreamTypeEnum() == StreamCache.StreamType.BOTH) {
            holder.streamTypeTxt.setText(
                    StreamCache.StreamType.VIDEO.name() + "+" + StreamCache.StreamType.AUDIO);
        } else {
            holder.streamTypeTxt.setText(item.getStreamType());
        }

        holder.streamResTxt.setText(item.getStreamRes());
        holder.streamFormatTxt.setText(item.getStreamFormat());

        long streamSize = item.getStreamSize();
        final String sizeStr = StringFormatUtil.formatFileSize(context, streamSize);
        holder.streamSizeTxt.setText(sizeStr);

        if (ConfigOptions.DEVEL_MODE_ON) {
            holder.downloadStateTxt.setVisibility(View.VISIBLE);

            if (item.isDownloaded()) {
                holder.downloadStateTxt.setText(context.getText(R.string.stream_state_done));
            } else if (item.getStreamSize() == StreamCache.STREAM_SIZE_UNKNOWN) {
                holder.downloadStateTxt.setText(context.getText(R.string.stream_state_init));
            } else {
                holder.downloadStateTxt.setText(context.getText(R.string.stream_state_progress));
            }
        }

        holder.downloadProgress.setVisibility(View.GONE);

        // по умолчанию кнопка "загрузить заново" скрыта
        holder.redownloadStreamBtn.setVisibility(View.GONE);
        if (item.isDownloaded()) {
            // если стрим помечен как закачанный, проверить, на месте ли файл
            final File cacheFile = StreamCacheFsManager.getFileForStream(context, item);
            if (!cacheFile.exists()) {
                holder.downloadErrorTxt.setVisibility(View.VISIBLE);
                holder.downloadErrorTxt.setText(context.getString(R.string.stream_cache_file_missing));

                holder.redownloadStreamBtn.setVisibility(View.VISIBLE);
            } else {
                holder.downloadErrorTxt.setVisibility(View.GONE);
            }
        } else {
            holder.downloadErrorTxt.setVisibility(View.GONE);
        }


        if (item.getVideoItem() != null && item.getVideoItem().getThumbBitmap() != null) {
            holder.thumbImg.setImageBitmap(item.getVideoItem().getThumbBitmap());
        } else {
            holder.thumbImg.setImageResource(R.drawable.ic_yashlang_thumb);
            thumbLoaderExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (item.getVideoItem() == null) {
                        item.setVideoItem(VideoDatabase.getDbInstance(context).videoItemDao().getById(item.getVideoId()));
                    }
                    // проверка на тот случай, если все-таки не нашли VideoItem в базе данных
                    // (строго говоря, такой вариант невозможен, за это отвечает движок базы данных)
                    if (item.getVideoItem() != null) {
                        final Bitmap thumb =
                                VideoThumbManager.getInstance().loadVideoThumb(context, item.getVideoItem());
                        item.getVideoItem().setThumbBitmap(thumb);
                        context.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                StreamCachePagedListAdapter.this.notifyDataSetChanged();
                            }
                        });
                    }
                }
            });
        }

        holder.downloadStartPauseProgress.setVisibility(View.GONE);
        holder.downloadStartBtn.setVisibility(View.GONE);
        holder.downloadPauseBtn.setVisibility(View.GONE);

        holder.downloadStartBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (onItemProgressControlListener != null) {
                    onItemProgressControlListener.onItemProgressStartClick(view, holder.getBindingAdapterPosition(), item);
                }
            }
        });

        holder.downloadPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (onItemProgressControlListener != null) {
                    onItemProgressControlListener.onItemProgressPauseClick(view, holder.getBindingAdapterPosition(), item);
                }
            }
        });

        holder.redownloadStreamBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (onItemProgressControlListener != null) {
                    onItemProgressControlListener.onItemRedownloadClick(view, holder.getBindingAdapterPosition(), item);
                }
            }
        });

        holder.deleteStreamBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (onItemProgressControlListener != null) {
                    onItemProgressControlListener.onItemDeleteClick(view, holder.getBindingAdapterPosition(), item);
                }
            }
        });

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
}
