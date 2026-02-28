package su.sadrobot.yashlang.view;

/*
 * Copyright (C) Anton Moiseev 2026 <github.com/sadr0b0t>
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.controller.PlaylistImportTask;
import su.sadrobot.yashlang.controller.ThumbManager;

public class PlaylistImportTaskArrayAdapter extends RecyclerView.Adapter<PlaylistImportTaskArrayAdapter.PlaylistImportTaskViewHolder> {

    private final Activity context;
    private final List<PlaylistImportTask> playlistImportTasks;

    private final OnPlaylistImportTaskListItemControlListener onItemControlListener;

    private final Map<Long, PlaylistImportTaskViewHolder> importTaskToViewHolderMap = new HashMap<>();


    // Извлекать задания на выполнение не в режиме очереди, а в режиме стека: последнее добавленное
    // задание отправляется на выполнение первым: этот режим лучше подходит при прокрутке списка, т.к.
    // пользователь видит часть иконок, до которых он уже домотал, их нужно загрузить в первую очередь,
    // а не ждать, пока загрузятся те иконки, которые уже пролистали раньше

    // код создания ThreadPool из Executors.newFixedThreadPool(10)
    //private ExecutorService thumbLoaderExecutor = Executors.newFixedThreadPool(10);
    private final ExecutorService thumbLoaderExecutor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingDeque<Runnable>() {
                @Override
                public Runnable take() throws InterruptedException {
                    return super.takeLast();
                }
            });

    public static class PlaylistImportTaskViewHolder extends RecyclerView.ViewHolder {
        final TextView nameTxt;
        final TextView urlTxt;
        final ImageView thumbImg;
        final TextView taskTypeAddNewPlaylistTxt;
        final TextView taskTypeAddNewItemsToPlaylistTxt;
        final TextView taskStatusMsgTxt;
        final TextView taskErrorMsgTxt;
        final ProgressBar taskProgress;
        final View taskEnqueuedView;
        final View taskCheckedView;
        final Button retryTaskBtn;
        final Button dismissTaskBtn;
        final Button cancelTaskBtn;

        public PlaylistImportTaskViewHolder(final View itemView) {
            super(itemView);
            nameTxt = itemView.findViewById(R.id.playlist_name_txt);
            urlTxt = itemView.findViewById(R.id.playlist_url_txt);
            thumbImg = itemView.findViewById(R.id.playlist_thumb_img);

            taskTypeAddNewPlaylistTxt = itemView.findViewById(R.id.task_type_add_new_playlist_txt);
            taskTypeAddNewItemsToPlaylistTxt = itemView.findViewById(R.id.task_type_add_new_items_to_playlist_txt);

            taskStatusMsgTxt = itemView.findViewById(R.id.task_status_msg_txt);
            taskErrorMsgTxt = itemView.findViewById(R.id.task_error_msg_txt);
            taskProgress = itemView.findViewById(R.id.task_progress);
            taskEnqueuedView = itemView.findViewById(R.id.task_enqueued_view);
            taskCheckedView = itemView.findViewById(R.id.task_checked_view);

            retryTaskBtn = itemView.findViewById(R.id.retry_task_btn);
            dismissTaskBtn = itemView.findViewById(R.id.dismiss_task_btn);
            cancelTaskBtn = itemView.findViewById(R.id.cancel_task_btn);
        }
    }

    public PlaylistImportTaskArrayAdapter(final Activity context, final List<PlaylistImportTask> playlistImportTasks,
                                          final OnPlaylistImportTaskListItemControlListener onItemControlListener) {
        this.context = context;
        this.playlistImportTasks = playlistImportTasks;
        this.onItemControlListener = onItemControlListener;
    }

    @NonNull
    @Override
    public PlaylistImportTaskViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        final View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.playlist_import_task_list_item, parent, false);
        return new PlaylistImportTaskViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final PlaylistImportTaskViewHolder holder, final int position) {
        final PlaylistImportTask item = playlistImportTasks.get(position);
        importTaskToViewHolderMap.put(item.getId(), holder);

        holder.nameTxt.setText(item.getPlaylistInfo().getName());
        holder.urlTxt.setText(item.getPlaylistInfo().getUrl().replaceFirst(
                "https://", "").replaceFirst("www.", ""));

        if (item.getPlaylistInfo().getThumbBitmap() != null) {
            holder.thumbImg.setImageBitmap(item.getPlaylistInfo().getThumbBitmap());
        } else {
            holder.thumbImg.setImageResource(R.drawable.ic_yashlang_thumb);
            thumbLoaderExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap thumb =
                            ThumbManager.getInstance().loadPlaylistThumb(context, item.getPlaylistInfo());
                    item.getPlaylistInfo().setThumbBitmap(thumb);
                    context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            PlaylistImportTaskArrayAdapter.this.notifyItemChanged(holder.getBindingAdapterPosition());
                        }
                    });
                }
            });
        }

        switch (item.getTaskType()) {
            case ADD_NEW_PLAYLIST:
                holder.taskTypeAddNewPlaylistTxt.setVisibility(View.VISIBLE);
                holder.taskTypeAddNewItemsToPlaylistTxt.setVisibility(View.GONE);
                break;
            case ADD_NEW_ITEMS_TO_PLAYLIST:
                holder.taskTypeAddNewPlaylistTxt.setVisibility(View.GONE);
                holder.taskTypeAddNewItemsToPlaylistTxt.setVisibility(View.VISIBLE);
                break;
        }

        holder.taskStatusMsgTxt.setText(item.getTaskController().getStatusMsg());
        switch (item.getTaskController().getState()) {
            case WAIT:
                // ожидаение действия пользователя:
                // если canceled: dismiss или retry
                // если не сanceled и ошибка, то cancel или retry
                // если не сanceled и ошибки нет (завершено успешно): dismiss
                if (item.getTaskController().isCanceled()) {
                    // canceled
                    holder.taskErrorMsgTxt.setText("");

                    holder.taskErrorMsgTxt.setVisibility(View.GONE);
                    holder.taskProgress.setVisibility(View.GONE);
                    holder.taskEnqueuedView.setVisibility(View.GONE);
                    holder.taskCheckedView.setVisibility(View.GONE);
                    holder.retryTaskBtn.setVisibility(View.VISIBLE);
                    holder.dismissTaskBtn.setVisibility(View.VISIBLE);
                    holder.cancelTaskBtn.setVisibility(View.GONE);
                } else if (item.getTaskController().getException() != null) {
                    final Exception e = item.getTaskController().getException();
                    holder.taskErrorMsgTxt.setText(e.getMessage()
                                    + (e.getCause() != null ? "\n(" + e.getCause().getMessage() + ")" : ""));

                    holder.taskErrorMsgTxt.setVisibility(View.VISIBLE);
                    holder.taskProgress.setVisibility(View.GONE);
                    holder.taskEnqueuedView.setVisibility(View.GONE);
                    holder.taskCheckedView.setVisibility(View.GONE);
                    holder.retryTaskBtn.setVisibility(View.VISIBLE);
                    holder.dismissTaskBtn.setVisibility(View.GONE);
                    holder.cancelTaskBtn.setVisibility(View.VISIBLE);
                } else {
                    // не canceled и нет ошибки, значит задача выполнена
                    holder.taskErrorMsgTxt.setText("");

                    holder.taskErrorMsgTxt.setVisibility(View.GONE);
                    holder.taskProgress.setVisibility(View.GONE);
                    holder.taskEnqueuedView.setVisibility(View.GONE);
                    holder.taskCheckedView.setVisibility(View.VISIBLE);
                    holder.retryTaskBtn.setVisibility(View.GONE);
                    holder.dismissTaskBtn.setVisibility(View.VISIBLE);
                    holder.cancelTaskBtn.setVisibility(View.GONE);
                }
                holder.cancelTaskBtn.setEnabled(true);
                break;
            case ENQUEUED:
                // в очереди на выполнение
                // доступные действия: cancel
                holder.taskErrorMsgTxt.setText("");

                holder.taskErrorMsgTxt.setVisibility(View.GONE);
                holder.taskProgress.setVisibility(View.GONE);
                holder.taskEnqueuedView.setVisibility(View.VISIBLE);
                holder.taskCheckedView.setVisibility(View.GONE);
                holder.retryTaskBtn.setVisibility(View.GONE);
                holder.dismissTaskBtn.setVisibility(View.GONE);
                holder.cancelTaskBtn.setVisibility(View.VISIBLE);
                holder.cancelTaskBtn.setEnabled(true);
                break;
            case ACTIVE:
                // выполняется
                // доступные действия: cancel
                holder.taskErrorMsgTxt.setText("");

                holder.taskErrorMsgTxt.setVisibility(View.GONE);
                holder.taskProgress.setVisibility(View.VISIBLE);
                holder.taskEnqueuedView.setVisibility(View.GONE);
                holder.taskCheckedView.setVisibility(View.GONE);
                holder.retryTaskBtn.setVisibility(View.GONE);
                holder.dismissTaskBtn.setVisibility(View.GONE);
                holder.cancelTaskBtn.setVisibility(View.VISIBLE);

                if (item.getTaskController().isCanceled()) {
                    // задача уже отменена, но всё еще выполрняется, т.е. находится в процессе отмены -
                    // пользовтаель уже нажал кнопку отмена, кликать её повторно нет смысла
                    holder.cancelTaskBtn.setEnabled(false);
                } else {
                    holder.cancelTaskBtn.setEnabled(true);
                }
                break;
        }

        holder.retryTaskBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (onItemControlListener != null) {
                    onItemControlListener.onTaskItemRetryClick(view, holder.getBindingAdapterPosition(), item);
                }
            }
        });

        holder.dismissTaskBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (onItemControlListener != null) {
                    onItemControlListener.onTaskItemDismissClick(view, holder.getBindingAdapterPosition(), item);
                }
            }
        });

        holder.cancelTaskBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (onItemControlListener != null) {
                    onItemControlListener.onTaskItemCancelClick(view, holder.getBindingAdapterPosition(), item);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return playlistImportTasks.size();
    }

    public void notifyImportTaskItemChanged(final long importTaskId) {
        if (importTaskToViewHolderMap.containsKey(importTaskId)) {
            notifyItemChanged(importTaskToViewHolderMap.get(importTaskId).getBindingAdapterPosition());
        }
    }
}
