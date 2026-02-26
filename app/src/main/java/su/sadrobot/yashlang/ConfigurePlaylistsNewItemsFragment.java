package su.sadrobot.yashlang;

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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import su.sadrobot.yashlang.controller.PlaylistImportTask;
import su.sadrobot.yashlang.controller.TaskController;
import su.sadrobot.yashlang.controller.VideoItemActions;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.player.RecommendationsProviderFactory;
import su.sadrobot.yashlang.service.PlaylistsImportService;
import su.sadrobot.yashlang.view.DataSourceListener;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.VideoItemMultPlaylistsOnlyNewOnlineDataSourceFactory;
import su.sadrobot.yashlang.view.VideoItemPagedListAdapter;

/**
 *
 */
public class ConfigurePlaylistsNewItemsFragment extends Fragment {
    // https://developer.android.com/guide/components/fragments
    // https://developer.android.com/guide/navigation/navigation-swipe-view

    // Элементы списка
    private View playlistItemsView;

    private View emptyView;
    private View checkInitialView;
    private ProgressBar checkProgress;
    private View checkErrorView;
    private TextView checkErrorTxt;
    private RadioButton checkAllRadio;
    private RadioButton checkOnlyEnabledRadio;
    private Button checkNewItemsBtn;

    // Новые элементы всех плейлистов
    private View newItemsView;
    private Button addNewItemsBtn;
    private RecyclerView videoList;

    //
    // Панель - прогресс добавления новых элементов в плейлист
    private View playlistsNewItemsAddTasksProgressView;

    private TextView playlistsNewItemsAddTasksStatusTxt;
    private ProgressBar playlistsNewItemsAddTasksProgress;
    private TextView playlistsNewItemsAddTasksDoneTxt;
    private TextView playlistsNewItemsAddTasksHaveCanceledTxt;
    private TextView playlistsNewItemsAddTasksHaveErrorsTxt;

    private Button playlistsNewItemsAddTasksRetryAllBtn;
    private Button playlistsNewItemsAddTasksCancelAllBtn;
    private Button playlistsNewItemsAddTasksDismissAllBtn;


    // режим разработки
    private Button develModeStartFakeAddNewItemsTaskBtn;
    private Button develModeStartFakeAddNewItemsTaskWithErrorBtn;

    private PlaylistsImportService playlistsImportService;
    private ServiceConnection playlistImportServiceConnection;

    private List<PlaylistImportTask> playlistsAddNewItemsTasks = new ArrayList<>();

    private final Handler handler = new Handler();

    // потоки для сетевых операций (могут включать обращения к базе данных) - при плохой связи
    // сетевая операция может затупить и незаметно задерживать время выполнения других фоновых
    // операций, которые не связаны с сетью
    private final ExecutorService dbAndNetworkExecutor = Executors.newSingleThreadExecutor();

    private LiveData<PagedList<VideoItem>> videoItemsLiveData;

    private enum State {
        NEW_ITEMS_LIST_EMPTY, NEW_ITEMS_LIST_LOAD_PROGRESS, NEW_ITEMS_LIST_LOAD_ERROR, NEW_ITEMS_LIST_LOADED,
        PLAYLIST_UPDATE_ALL_TASKS_PROGRESS,
        PLAYLIST_UPDATE_ALL_TASKS_HAVE_CANCELED_AND_ERRORS,
        PLAYLIST_UPDATE_ALL_TASKS_HAVE_CANCELED,
        PLAYLIST_UPDATE_ALL_TASKS_HAVE_ERRORS,
        PLAYLIST_UPDATE_ALL_TASKS_DONE
    }

    private State state = State.NEW_ITEMS_LIST_EMPTY;
    private boolean checkError = false;

    private final RecyclerView.AdapterDataObserver emptyListObserver = new RecyclerView.AdapterDataObserver() {
        // https://stackoverflow.com/questions/47417645/empty-view-on-a-recyclerview
        // https://stackoverflow.com/questions/27414173/equivalent-of-listview-setemptyview-in-recyclerview
        // https://gist.github.com/sheharyarn/5602930ad84fa64c30a29ab18eb69c6e
        private void checkIfEmpty() {
            // пришли какие-то данные (или сообщение, что их нет) - в любом случае прячем прогресс
            // плюс, сюда же попадаем в случае ошибки

            if (state != State.PLAYLIST_UPDATE_ALL_TASKS_PROGRESS &&
                    state != State.PLAYLIST_UPDATE_ALL_TASKS_HAVE_ERRORS &&
                    state != State.PLAYLIST_UPDATE_ALL_TASKS_DONE) {
                // Будем менять состояние здесь только в том случае, если у нас сейчас активна панель
                // со списком новых видео. Если мы в процессе добавления новых видео в базу, то
                // состояние здесь менять не будем, чтобы оно не скрыло панели статуса добавления.
                // Такое может произойти, например, если мы крутанули список новых видео вниз
                // и нажали кнопку "добавить новые видео": элементы списка начали подгружаться в фоне
                // и генерировать события, которые приведут программу сюда уже после того, как
                // пользователь начнет наблюдать панели со статусом добавления новых элементов.

                final boolean listIsEmpty = videoList.getAdapter() == null || videoList.getAdapter().getItemCount() == 0;
                if (checkError) {
                    state = State.NEW_ITEMS_LIST_LOAD_ERROR;
                } else if (listIsEmpty) {
                    state = State.NEW_ITEMS_LIST_EMPTY;
                } else {
                    state = State.NEW_ITEMS_LIST_LOADED;
                }

                updateControlsVisibility();
            }
        }

        @Override
        public void onChanged() {
            checkIfEmpty();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            checkIfEmpty();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            checkIfEmpty();
        }
    };

    private final TaskController.TaskListener addNewItemsTaskListener = new TaskController.TaskAdapter() {
        @Override
        public void onStart(final TaskController taskController) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    updateStateFromImportTasksList();
                }
            });
        }

        @Override
        public void onFinish(final TaskController taskController) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    updateStateFromImportTasksList();
                }
            });
        }

        @Override
        public void onStatusMsgChange(final TaskController taskController, final String status, final Exception e) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    updateStateFromImportTasksList();
                }
            });
        }

        @Override
        public void onCancel(final TaskController taskController) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    updateStateFromImportTasksList();
                }
            });
        }

        @Override
        public void onReset(final TaskController taskController) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    updateStateFromImportTasksList();
                }
            });
        }

        @Override
        public void onStateChange(final TaskController taskController, final TaskController.TaskState state) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    updateStateFromImportTasksList();
                }
            });
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_configure_playlists_new_items, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        // Cписок элементов
        playlistItemsView = view.findViewById(R.id.playlist_items_view);

        emptyView = view.findViewById(R.id.empty_view);
        checkInitialView = view.findViewById(R.id.check_initial_view);
        checkProgress = view.findViewById(R.id.check_progress);
        checkErrorView = view.findViewById(R.id.check_error_view);
        checkErrorTxt = view.findViewById(R.id.check_error_txt);
        checkAllRadio = view.findViewById(R.id.check_all_radio);
        checkOnlyEnabledRadio = view.findViewById(R.id.check_only_enabled_radio);
        checkNewItemsBtn = view.findViewById(R.id.check_new_items_btn);

        newItemsView = view.findViewById(R.id.playlist_new_items_view);
        addNewItemsBtn = view.findViewById(R.id.add_new_items_btn);
        videoList = view.findViewById(R.id.video_list);

        // Операции и прогресс добавления
        playlistsNewItemsAddTasksProgressView = view.findViewById(R.id.playlists_new_items_add_tasks_progress_view);

        playlistsNewItemsAddTasksStatusTxt = view.findViewById(R.id.playlists_new_items_add_tasks_status_txt);
        playlistsNewItemsAddTasksProgress = view.findViewById(R.id.playlists_new_items_add_tasks_progress);
        playlistsNewItemsAddTasksDoneTxt = view.findViewById(R.id.playlists_new_items_add_tasks_done_txt);
        playlistsNewItemsAddTasksHaveCanceledTxt = view.findViewById(R.id.playlists_new_items_add_tasks_have_canceled_txt);
        playlistsNewItemsAddTasksHaveErrorsTxt = view.findViewById(R.id.playlists_new_items_add_tasks_have_errors_txt);

        playlistsNewItemsAddTasksRetryAllBtn = view.findViewById(R.id.playlists_new_items_add_tasks_retry_all_btn);
        playlistsNewItemsAddTasksCancelAllBtn = view.findViewById(R.id.playlists_new_items_add_tasks_cancel_all_btn);
        playlistsNewItemsAddTasksDismissAllBtn = view.findViewById(R.id.playlists_new_items_add_tasks_dismiss_all_btn);

        develModeStartFakeAddNewItemsTaskBtn = view.findViewById(R.id.devel_mode_fake_add_new_items_btn);
        develModeStartFakeAddNewItemsTaskWithErrorBtn = view.findViewById(R.id.devel_mode_fake_add_new_items_with_error_btn);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        videoList.setLayoutManager(linearLayoutManager);
        videoList.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                // чтобы картинки и текст не сбивались в кучку при быстрой промотке
                // см: https://github.com/sadr0b0t/yashlang/issues/129
                return true;
            }
        });

        checkNewItemsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateVideoListBg();
            }
        });

        addNewItemsBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewItemsBg();
            }
        });

        playlistsNewItemsAddTasksRetryAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // todo: не перазпускать успешно завершенные
                // перезапускать только с ошибкой или отмененные
                for (final PlaylistImportTask importTask : playlistsAddNewItemsTasks) {
                    PlaylistsImportService.retryPlaylistImportTask(ConfigurePlaylistsNewItemsFragment.this.getContext(),
                            importTask.getId());
                }
            }
        });

        playlistsNewItemsAddTasksCancelAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (final PlaylistImportTask importTask : playlistsAddNewItemsTasks) {
                    PlaylistsImportService.cancelPlaylistImportTask(ConfigurePlaylistsNewItemsFragment.this.getContext(),
                            importTask.getId());
                }
            }
        });

        playlistsNewItemsAddTasksDismissAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (final PlaylistImportTask importTask : playlistsAddNewItemsTasks) {
                    PlaylistsImportService.dismissPlaylistImportTask(ConfigurePlaylistsNewItemsFragment.this.getContext(),
                            importTask.getId());
                }
            }
        });

        if (ConfigOptions.DEVEL_MODE_ON) {
            develModeStartFakeAddNewItemsTaskBtn.setVisibility(View.VISIBLE);
            develModeStartFakeAddNewItemsTaskBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    PlaylistsImportService.develModeStartFakeTask(
                            ConfigurePlaylistsNewItemsFragment.this.getContext(),
                            PlaylistImportTask.PlaylistImportTaskType.ADD_NEW_ITEMS_TO_PLAYLIST,
                            0,
                            20000, false);
                }
            });

            develModeStartFakeAddNewItemsTaskWithErrorBtn.setVisibility(View.VISIBLE);
            develModeStartFakeAddNewItemsTaskWithErrorBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    PlaylistsImportService.develModeStartFakeTask(
                            ConfigurePlaylistsNewItemsFragment.this.getContext(),
                            PlaylistImportTask.PlaylistImportTaskType.ADD_NEW_ITEMS_TO_PLAYLIST,
                            PlaylistInfo.ID_NONE,
                            20000, true);
                }
            });
        }

        // здесь можно было бы сразу грузить список новых видео, но, возможно,
        // будет не очень правильно грузить сеть проверкой всех плейлистов каждый
        // раз, когда мы заходим в настройки - есть и кнопка
        //updateVideoListBg();
    }

    @Override
    public void onResume() {
        super.onResume();

        playlistImportServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName name, final IBinder service) {
                playlistsImportService = ((PlaylistsImportService.PlaylistsImportServiceBinder) service).getService();

                playlistsAddNewItemsTasks.clear();
                playlistsAddNewItemsTasks.addAll(playlistsImportService.getPlaylistImportTasks(
                        PlaylistImportTask.PlaylistImportTaskType.ADD_NEW_ITEMS_TO_PLAYLIST));

                for (final PlaylistImportTask importTask : playlistsAddNewItemsTasks) {
                    importTask.getTaskController().setTaskListener(addNewItemsTaskListener);
                }
                updateStateFromImportTasksList();

                playlistsImportService.setServiceListener(new PlaylistsImportService.PlaylistsImportServiceListener() {
                    @Override
                    public void onImportPlaylistTaskAdded(final PlaylistImportTask importTask) {
                        if (importTask.getTaskType() == PlaylistImportTask.PlaylistImportTaskType.ADD_NEW_ITEMS_TO_PLAYLIST) {
                            playlistsAddNewItemsTasks.add(importTask);
                            importTask.getTaskController().setTaskListener(addNewItemsTaskListener);
                            updateStateFromImportTasksList();
                        }
                    }

                    @Override
                    public void onImportPlaylistTaskRemoved(final PlaylistImportTask importTask) {
                        playlistsAddNewItemsTasks.remove(importTask);
                        importTask.getTaskController().removeTaskListener(addNewItemsTaskListener);
                        updateStateFromImportTasksList();
                    }
                });
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
                playlistsImportService.removeServiceListener();
                playlistsImportService = null;

                for (final PlaylistImportTask importTask : playlistsAddNewItemsTasks) {
                    importTask.getTaskController().removeTaskListener(addNewItemsTaskListener);
                }
                playlistsAddNewItemsTasks.clear();
                updateStateFromImportTasksList();
            }
        };

        this.getContext().bindService(
                new Intent(this.getContext(), PlaylistsImportService.class),
                playlistImportServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onPause() {
        if (playlistsImportService != null) {
            playlistsImportService.stopIfFinished();
        }
        this.getContext().unbindService(playlistImportServiceConnection);

        super.onPause();
    }

    private void updateStateFromImportTasksList() {
        int numEnqueuedOrRunningTaks = 0;
        int numCanceledTasks = 0;
        int numTasksWithErrors = 0;
        int numDoneTasks = 0;

        playlistsNewItemsAddTasksStatusTxt.setText(getString(R.string.adding_new_items_to_n_playlists).
                replace("%s", String.valueOf(playlistsAddNewItemsTasks.size())));

        playlistsNewItemsAddTasksDoneTxt.setText("");
        playlistsNewItemsAddTasksHaveCanceledTxt.setText("");
        playlistsNewItemsAddTasksHaveErrorsTxt.setText("");

        for (final PlaylistImportTask importTask : playlistsAddNewItemsTasks) {
            if (importTask.getTaskController().getState() == TaskController.TaskState.ENQUEUED ||
                    importTask.getTaskController().getState() == TaskController.TaskState.ACTIVE) {
                numEnqueuedOrRunningTaks++;
            } else if (importTask.getTaskController().getState() == TaskController.TaskState.WAIT) {
                if (importTask.getTaskController().isCanceled()) {
                    numCanceledTasks++;
                } else if (importTask.getTaskController().getException() != null) {
                    numTasksWithErrors++;
                } else {
                    numDoneTasks++;
                }
            }
        }

        if (numEnqueuedOrRunningTaks > 0) {
            state = State.PLAYLIST_UPDATE_ALL_TASKS_PROGRESS;
        } else if (numCanceledTasks > 0 && numTasksWithErrors > 0) {
            playlistsNewItemsAddTasksHaveCanceledTxt.setText(getString(
                    R.string.tasks_n_canceled).
                    replace("%s", String.valueOf(numCanceledTasks)));
            playlistsNewItemsAddTasksHaveErrorsTxt.setText(getString(
                    R.string.tasks_n_have_errors).
                    replace("%s", String.valueOf(numTasksWithErrors)));
            state = State.PLAYLIST_UPDATE_ALL_TASKS_HAVE_CANCELED_AND_ERRORS;
        } else if (numCanceledTasks > 0) {
            playlistsNewItemsAddTasksHaveCanceledTxt.setText(getString(
                    R.string.tasks_n_canceled).
                    replace("%s", String.valueOf(numCanceledTasks)));
            state = State.PLAYLIST_UPDATE_ALL_TASKS_HAVE_CANCELED;
        } else if (numTasksWithErrors > 0) {
            playlistsNewItemsAddTasksHaveErrorsTxt.setText(getString(
                    R.string.tasks_n_have_errors).
                    replace("%s", String.valueOf(numTasksWithErrors)));
            state = State.PLAYLIST_UPDATE_ALL_TASKS_HAVE_ERRORS;
        } else if (numDoneTasks > 0) {
            playlistsNewItemsAddTasksDoneTxt.setText(getString(
                R.string.tasks_n_done).
                replace("%s", String.valueOf(numDoneTasks)));
            state = State.PLAYLIST_UPDATE_ALL_TASKS_DONE;
        } else {
            state = State.NEW_ITEMS_LIST_EMPTY;
        }
        updateControlsVisibility();
    }

    private void updateControlsVisibility() {
        switch (state){
            case NEW_ITEMS_LIST_EMPTY:
                playlistItemsView.setVisibility(View.VISIBLE);
                playlistsNewItemsAddTasksProgressView.setVisibility(View.GONE);

                emptyView.setVisibility(View.VISIBLE);
                newItemsView.setVisibility(View.GONE);

                checkInitialView.setVisibility(View.VISIBLE);
                checkErrorView.setVisibility(View.GONE);
                checkProgress.setVisibility(View.INVISIBLE);

                checkAllRadio.setEnabled(true);
                checkOnlyEnabledRadio.setEnabled(true);
                checkNewItemsBtn.setEnabled(true);

                break;
            case NEW_ITEMS_LIST_LOAD_PROGRESS:
                playlistItemsView.setVisibility(View.VISIBLE);
                playlistsNewItemsAddTasksProgressView.setVisibility(View.GONE);

                emptyView.setVisibility(View.VISIBLE);
                newItemsView.setVisibility(View.GONE);

                checkInitialView.setVisibility(View.INVISIBLE);
                checkErrorView.setVisibility(View.GONE);
                checkProgress.setVisibility(View.VISIBLE);

                checkAllRadio.setEnabled(false);
                checkOnlyEnabledRadio.setEnabled(false);
                checkNewItemsBtn.setEnabled(false);

                break;
            case NEW_ITEMS_LIST_LOAD_ERROR:
                playlistItemsView.setVisibility(View.VISIBLE);
                playlistsNewItemsAddTasksProgressView.setVisibility(View.GONE);

                emptyView.setVisibility(View.VISIBLE);
                newItemsView.setVisibility(View.GONE);

                checkInitialView.setVisibility(View.GONE);
                checkErrorView.setVisibility(View.VISIBLE);
                checkProgress.setVisibility(View.GONE);

                checkAllRadio.setEnabled(true);
                checkOnlyEnabledRadio.setEnabled(true);
                checkNewItemsBtn.setEnabled(true);

                break;
            case NEW_ITEMS_LIST_LOADED:
                playlistItemsView.setVisibility(View.VISIBLE);
                playlistsNewItemsAddTasksProgressView.setVisibility(View.GONE);

                emptyView.setVisibility(View.GONE);
                newItemsView.setVisibility(View.VISIBLE);

                break;
            case PLAYLIST_UPDATE_ALL_TASKS_PROGRESS:
                playlistItemsView.setVisibility(View.GONE);
                playlistsNewItemsAddTasksProgressView.setVisibility(View.VISIBLE);

                playlistsNewItemsAddTasksProgress.setVisibility(View.VISIBLE);
                playlistsNewItemsAddTasksDoneTxt.setVisibility(View.GONE);
                playlistsNewItemsAddTasksHaveCanceledTxt.setVisibility(View.GONE);
                playlistsNewItemsAddTasksHaveErrorsTxt.setVisibility(View.GONE);

                playlistsNewItemsAddTasksRetryAllBtn.setVisibility(View.GONE);
                playlistsNewItemsAddTasksCancelAllBtn.setVisibility(View.VISIBLE);
                playlistsNewItemsAddTasksDismissAllBtn.setVisibility(View.GONE);

                break;
            case PLAYLIST_UPDATE_ALL_TASKS_HAVE_CANCELED_AND_ERRORS:
                playlistItemsView.setVisibility(View.GONE);
                playlistsNewItemsAddTasksProgressView.setVisibility(View.VISIBLE);

                playlistsNewItemsAddTasksProgress.setVisibility(View.GONE);
                playlistsNewItemsAddTasksDoneTxt.setVisibility(View.GONE);
                playlistsNewItemsAddTasksHaveCanceledTxt.setVisibility(View.VISIBLE);
                playlistsNewItemsAddTasksHaveErrorsTxt.setVisibility(View.VISIBLE);

                playlistsNewItemsAddTasksRetryAllBtn.setVisibility(View.VISIBLE);
                playlistsNewItemsAddTasksCancelAllBtn.setVisibility(View.VISIBLE);
                playlistsNewItemsAddTasksDismissAllBtn.setVisibility(View.GONE);

                break;
            case PLAYLIST_UPDATE_ALL_TASKS_HAVE_CANCELED:
                playlistItemsView.setVisibility(View.GONE);
                playlistsNewItemsAddTasksProgressView.setVisibility(View.VISIBLE);

                playlistsNewItemsAddTasksProgress.setVisibility(View.GONE);
                playlistsNewItemsAddTasksDoneTxt.setVisibility(View.GONE);
                playlistsNewItemsAddTasksHaveCanceledTxt.setVisibility(View.VISIBLE);
                playlistsNewItemsAddTasksHaveErrorsTxt.setVisibility(View.GONE);

                playlistsNewItemsAddTasksRetryAllBtn.setVisibility(View.VISIBLE);
                playlistsNewItemsAddTasksCancelAllBtn.setVisibility(View.GONE);
                playlistsNewItemsAddTasksDismissAllBtn.setVisibility(View.VISIBLE);

                break;
            case PLAYLIST_UPDATE_ALL_TASKS_HAVE_ERRORS:
                playlistItemsView.setVisibility(View.GONE);
                playlistsNewItemsAddTasksProgressView.setVisibility(View.VISIBLE);

                playlistsNewItemsAddTasksProgress.setVisibility(View.GONE);
                playlistsNewItemsAddTasksDoneTxt.setVisibility(View.GONE);
                playlistsNewItemsAddTasksHaveCanceledTxt.setVisibility(View.GONE);
                playlistsNewItemsAddTasksHaveErrorsTxt.setVisibility(View.VISIBLE);

                playlistsNewItemsAddTasksRetryAllBtn.setVisibility(View.VISIBLE);
                playlistsNewItemsAddTasksCancelAllBtn.setVisibility(View.VISIBLE);
                playlistsNewItemsAddTasksDismissAllBtn.setVisibility(View.GONE);

                break;
            case PLAYLIST_UPDATE_ALL_TASKS_DONE:
                playlistItemsView.setVisibility(View.GONE);
                playlistsNewItemsAddTasksProgressView.setVisibility(View.VISIBLE);

                playlistsNewItemsAddTasksProgress.setVisibility(View.GONE);
                playlistsNewItemsAddTasksDoneTxt.setVisibility(View.VISIBLE);
                playlistsNewItemsAddTasksHaveCanceledTxt.setVisibility(View.GONE);
                playlistsNewItemsAddTasksHaveErrorsTxt.setVisibility(View.GONE);

                playlistsNewItemsAddTasksRetryAllBtn.setVisibility(View.GONE);
                playlistsNewItemsAddTasksCancelAllBtn.setVisibility(View.GONE);
                playlistsNewItemsAddTasksDismissAllBtn.setVisibility(View.VISIBLE);

                break;
        }
    }

    /**
     * Обновить список новых роликов в фоне
     */
    private void updateVideoListBg() {
        // прогресс будет видно до тех пор, пока в адаптер не придут какие-то данные
        // или не произойдет ошибка
        checkError = false;
        checkErrorTxt.setText("");
        state = State.NEW_ITEMS_LIST_LOAD_PROGRESS;
        updateControlsVisibility();

        dbAndNetworkExecutor.execute(new Runnable() {
            @Override
            public void run() {
                // информация из базы данных - загрузится быстро и без интернета
                final List<Long> plIds;
                if (checkAllRadio.isChecked()) {
                    plIds = VideoDatabase.getDbInstance(getContext()).playlistInfoDao().getAllIds();
                } else { // checkOnlyEnabledRadio.isChecked()
                    plIds = VideoDatabase.getDbInstance(getContext()).playlistInfoDao().getEnabledIds();
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        setupVideoListAdapter(plIds);
                    }
                });
            }
        });
    }

    private void setupVideoListAdapter(final List<Long> plIds) {
        if (videoItemsLiveData != null) {
            videoItemsLiveData.removeObservers(this);
        }
        if (videoList.getAdapter() != null) {
            videoList.getAdapter().unregisterAdapterDataObserver(emptyListObserver);
        }

        final VideoItemPagedListAdapter adapter = new VideoItemPagedListAdapter(getActivity(),
                new OnListItemClickListener<VideoItem>() {
                    @Override
                    public void onItemClick(final View view, final int position, final VideoItem videoItem) {
                        final Intent intent = new Intent(ConfigurePlaylistsNewItemsFragment.this.getContext(), WatchVideoActivity.class);
                        intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ITEM_URL, videoItem.getItemUrl());
                        intent.putExtra(WatchVideoActivity.PARAM_SCROLL_TO_IN_RECOMMENDATIONS, position);
                        intent.putExtra(WatchVideoActivity.PARAM_RECOMMENDATIONS_MODE, RecommendationsProviderFactory.RecommendationsMode.ALL_NEW);
                        startActivity(intent);
                    }

                    @Override
                    public boolean onItemLongClick(final View view, final int position, final VideoItem videoItem) {
                        final PopupMenu popup = new PopupMenu(ConfigurePlaylistsNewItemsFragment.this.getContext(),
                                view.findViewById(R.id.video_name_txt));
                        popup.getMenuInflater().inflate(R.menu.video_item_actions, popup.getMenu());
                        popup.getMenu().removeItem(R.id.action_play_in_playlist);
                        popup.getMenu().removeItem(R.id.action_play_in_playlist_shuffle);
                        popup.getMenu().removeItem(R.id.action_copy_playlist_name);
                        popup.getMenu().removeItem(R.id.action_copy_playlist_url);
                        popup.getMenu().removeItem(R.id.action_blacklist);
                        popup.getMenu().removeItem(R.id.action_download_streams);
                        popup.setOnMenuItemClickListener(
                                new PopupMenu.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(final MenuItem item) {
                                        switch (item.getItemId()) {
                                            case R.id.action_copy_video_name: {
                                                VideoItemActions.actionCopyVideoName(ConfigurePlaylistsNewItemsFragment.this.getContext(), videoItem);
                                                break;
                                            }
                                            case R.id.action_copy_video_url: {
                                                VideoItemActions.actionCopyVideoUrl(ConfigurePlaylistsNewItemsFragment.this.getContext(), videoItem);
                                                break;
                                            }
                                        }
                                        return true;
                                    }
                                }
                        );
                        popup.show();
                        return true;
                    }
                },
                null);
        // если список пустой, показываем специальный экранчик с кнопкой
        adapter.registerAdapterDataObserver(emptyListObserver);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(ConfigOptions.PAGED_LIST_PAGE_SIZE).build();

        final DataSource.Factory factory =
                new VideoItemMultPlaylistsOnlyNewOnlineDataSourceFactory(this.getContext(), plIds, false,
                        new DataSourceListener() {
                            @Override
                            public void onLoadInitialError(final Exception e) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        checkError = true;
                                        checkErrorTxt.setText(e.getMessage());

                                        //updateControlsVisibility();
                                        // смена статуса и настройки видимости элементов управления
                                        // будут выше в emptyListObserver.checkIfEmpty
                                        // (мы туда попадем после завершения loadInitial, видимо,
                                        // в любом случае, даже если ничего не добавлено в callback.onResult)
                                    }
                                });
                            }

                            @Override
                            public void onLoadBeforeError(final Exception e) {

                            }

                            @Override
                            public void onLoadAfterError(final Exception e) {

                            }
                        });

        videoItemsLiveData = new LivePagedListBuilder(factory, config).build();

        videoItemsLiveData.observe(this.getViewLifecycleOwner(), new Observer<PagedList<VideoItem>>() {
            @Override
            public void onChanged(@Nullable PagedList<VideoItem> videos) {
                adapter.submitList(videos);
            }
        });

        videoList.setAdapter(adapter);
    }

    private void addNewItemsBg() {
        dbAndNetworkExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final List<PlaylistInfo> allPlaylists;
                if (checkAllRadio.isChecked()) {
                    allPlaylists = VideoDatabase.getDbInstance(getContext()).playlistInfoDao().getAll();
                } else { // checkOnlyEnabledRadio.isChecked()
                    allPlaylists = VideoDatabase.getDbInstance(getContext()).playlistInfoDao().getEnabled();
                }

                for (final PlaylistInfo playlistInfo : allPlaylists) {
                    PlaylistsImportService.addPlaylistNewItems(ConfigurePlaylistsNewItemsFragment.this.getContext(),
                            playlistInfo.getId());
                }
            }
        });
    }
}
