package su.sadrobot.yashlang;

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


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import su.sadrobot.yashlang.controller.PlaylistImportTask;
import su.sadrobot.yashlang.controller.TaskController;
import su.sadrobot.yashlang.service.PlaylistsImportService;
import su.sadrobot.yashlang.view.OnPlaylistImportTaskListItemControlListener;
import su.sadrobot.yashlang.view.PlaylistImportTaskArrayAdapter;

/**
 *
 */
public class ImportPlaylistsProgressActivity extends AppCompatActivity {

    private Button cancelAllLiveBtn;
    private Button dismissAllFinishedBtn;

    private RecyclerView playlistImportTaskList;
    private View emptyView;

    private PlaylistsImportService playlistsImportService;
    private ServiceConnection playlistImportServiceConnection;

    private List<PlaylistImportTask> playlistImportTasks = new ArrayList<>();

    private Handler handler = new Handler();

    private final TaskController.TaskListener importTaskControllerListener = new TaskController.TaskAdapter() {
        @Override
        public void onStart(final TaskController taskController) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    final PlaylistImportTask importTask = findImportTaskForTaskController(taskController);
                    if (importTask != null && playlistImportTaskList.getAdapter() != null) {
                        ((PlaylistImportTaskArrayAdapter) playlistImportTaskList.getAdapter()).notifyImportTaskItemChanged(
                                importTask.getId());
                        updateControlsVisibility();
                    }
                }
            });
        }

        @Override
        public void onFinish(final TaskController taskController) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    final PlaylistImportTask importTask = findImportTaskForTaskController(taskController);
                    if (importTask != null && playlistImportTaskList.getAdapter() != null) {
                        ((PlaylistImportTaskArrayAdapter) playlistImportTaskList.getAdapter()).notifyImportTaskItemChanged(
                                importTask.getId());
                        updateControlsVisibility();
                    }
                }
            });
        }

        @Override
        public void onCancel(final TaskController taskController) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    final PlaylistImportTask importTask = findImportTaskForTaskController(taskController);
                    if (importTask != null && playlistImportTaskList.getAdapter() != null) {
                        ((PlaylistImportTaskArrayAdapter) playlistImportTaskList.getAdapter()).notifyImportTaskItemChanged(
                                importTask.getId());
                        updateControlsVisibility();
                    }
                }
            });
        }

        @Override
        public void onReset(final TaskController taskController) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    final PlaylistImportTask importTask = findImportTaskForTaskController(taskController);
                    if (importTask != null && playlistImportTaskList.getAdapter() != null) {
                        ((PlaylistImportTaskArrayAdapter) playlistImportTaskList.getAdapter()).notifyImportTaskItemChanged(
                                importTask.getId());
                        updateControlsVisibility();
                    }
                }
            });
        }

        @Override
        public void onStateChange(final TaskController taskController, final TaskController.TaskState state) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    final PlaylistImportTask importTask = findImportTaskForTaskController(taskController);
                    if (importTask != null && playlistImportTaskList.getAdapter() != null) {
                        ((PlaylistImportTaskArrayAdapter) playlistImportTaskList.getAdapter()).notifyImportTaskItemChanged(
                                importTask.getId());
                        updateControlsVisibility();
                    }
                }
            });
        }

        @Override
        public void onStatusMsgChange(final TaskController taskController, final String statusMsg, final Exception e) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    final PlaylistImportTask importTask = findImportTaskForTaskController(taskController);
                    if (importTask != null && playlistImportTaskList.getAdapter() != null) {
                        ((PlaylistImportTaskArrayAdapter) playlistImportTaskList.getAdapter()).notifyImportTaskItemChanged(
                                importTask.getId());
                        updateControlsVisibility();
                    }
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_import_playlists_progress);

        cancelAllLiveBtn = findViewById(R.id.cancel_all_live_btn);
        dismissAllFinishedBtn = findViewById(R.id.dismiss_all_finished_btn);

        playlistImportTaskList = findViewById(R.id.playlist_import_task_list);
        emptyView = findViewById(R.id.empty_view);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        playlistImportTaskList.setLayoutManager(linearLayoutManager);
        playlistImportTaskList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        playlistImportTaskList.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                // чтобы картинки и текст не сбивались в кучку при быстрой промотке
                // см: https://github.com/sadr0b0t/yashlang/issues/129
                return true;
            }
        });

        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        cancelAllLiveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PlaylistsImportService.cancelAllLivePlaylistImportTasks(ImportPlaylistsProgressActivity.this);
            }
        });

        dismissAllFinishedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PlaylistsImportService.dismissAllFinishedPlaylistImportTask(ImportPlaylistsProgressActivity.this);
            }
        });

        // имеет смысл только после подключения к сервису
        //setupPlaylistImportTasksAdapter();
    }

    @Override
    protected void onResume() {
        super.onResume();

        playlistImportServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName name, final IBinder service) {
                playlistsImportService = ((PlaylistsImportService.PlaylistsImportServiceBinder) service).getService();

                playlistsImportService.setServiceListener(new PlaylistsImportService.PlaylistsImportServiceListener() {
                    @Override
                    public void onImportPlaylistTaskAdded(final PlaylistImportTask importTask) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                setupPlaylistImportTasksAdapter();
                            }
                        });
                    }

                    @Override
                    public void onImportPlaylistTaskRemoved(final PlaylistImportTask importTask) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                setupPlaylistImportTasksAdapter();
                            }
                        });
                    }
                });

                setupPlaylistImportTasksAdapter();
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
                for (final PlaylistImportTask importTask : playlistImportTasks) {
                    importTask.getTaskController().removeTaskListener(importTaskControllerListener);
                }

                playlistsImportService.removeServiceListener();
                playlistsImportService = null;
            }
        };

        this.bindService(
                new Intent(this, PlaylistsImportService.class),
                playlistImportServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        if (playlistsImportService != null) {
            playlistsImportService.stopIfFinished();
        }
        this.unbindService(playlistImportServiceConnection);

        super.onPause();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private PlaylistImportTask findImportTaskForTaskController(final TaskController taskController) {
        PlaylistImportTask targetTask = null;
        for (final PlaylistImportTask importTask : playlistImportTasks) {
            if (importTask.getTaskController() == taskController) {
                targetTask = importTask;
            }
        }
        return targetTask;
    }

    private void updateControlsVisibility() {
        final boolean listIsEmpty = playlistImportTaskList.getAdapter() == null ||
                playlistImportTaskList.getAdapter().getItemCount() == 0;

        if (listIsEmpty) {
            emptyView.setVisibility(View.VISIBLE);
            playlistImportTaskList.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            playlistImportTaskList.setVisibility(View.VISIBLE);
        }


        boolean hasTasksToCancel = false;
        boolean hasTasksToDismiss = false;
        if (playlistsImportService != null) {
            for (PlaylistImportTask importTask: playlistsImportService.getPlaylistImportTasks()) {
                switch (importTask.getTaskController().getState()) {
                    case WAIT:
                        // ожидаение действия пользователя:
                        // если canceled: dismiss или retry
                        // если не сanceled и ошибка, то cancel или retry
                        // если не сanceled и ошибки нет (завершено успешно): dismiss
                        if (importTask.getTaskController().isCanceled()) {
                            // canceled
                            //hasTasksToCancel = false;
                            hasTasksToDismiss = true;
                        } else if (importTask.getTaskController().getException() != null) {
                            hasTasksToCancel = true;
                            //hasTasksToDismiss = false;
                        } else {
                            // не canceled и нет ошибки, значит задача выполнена
                            //hasTasksToCancel = false;
                            hasTasksToDismiss = true;
                        }
                        break;
                    case ENQUEUED:
                        // в очереди на выполнение
                        // доступные действия: cancel
                        hasTasksToCancel = true;
                        //hasTasksToDismiss = false;
                        break;
                    case ACTIVE:
                        // выполняется
                        // доступные действия: cancel
                        hasTasksToCancel = true;
                        //hasTasksToDismiss = false;
                        break;
                }
            }
        }
        cancelAllLiveBtn.setEnabled(hasTasksToCancel);
        dismissAllFinishedBtn.setEnabled(hasTasksToDismiss);
    }

    private void setupPlaylistImportTasksAdapter() {
        if (playlistsImportService == null) {
            return;
        }

        for (final PlaylistImportTask importTask : playlistImportTasks) {
            importTask.getTaskController().removeTaskListener(importTaskControllerListener);
        }

        playlistImportTasks.clear();
        playlistImportTasks.addAll(playlistsImportService.getPlaylistImportTasks());

        for (final PlaylistImportTask importTask : playlistImportTasks) {
            importTask.getTaskController().setTaskListener(importTaskControllerListener);
        }

        final PlaylistImportTaskArrayAdapter importTasksAdapter = new PlaylistImportTaskArrayAdapter(
                this, playlistImportTasks,
                new OnPlaylistImportTaskListItemControlListener() {
                    @Override
                    public void onTaskItemCancelClick(View view, int position, PlaylistImportTask importTask) {
                        PlaylistsImportService.cancelPlaylistImportTask(ImportPlaylistsProgressActivity.this, importTask.getId());
                    }

                    @Override
                    public void onTaskItemRetryClick(View view, int position, PlaylistImportTask importTask) {
                        PlaylistsImportService.retryPlaylistImportTask(ImportPlaylistsProgressActivity.this, importTask.getId());
                    }

                    @Override
                    public void onTaskItemDismissClick(View view, int position, PlaylistImportTask importTask) {
                        PlaylistsImportService.dismissPlaylistImportTask(ImportPlaylistsProgressActivity.this, importTask.getId());
                    }
                });
        playlistImportTaskList.setAdapter(importTasksAdapter);

        updateControlsVisibility();
    }
}
