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
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import su.sadrobot.yashlang.controller.PlaylistImportTask;
import su.sadrobot.yashlang.controller.TaskController;
import su.sadrobot.yashlang.controller.ThumbManager;
import su.sadrobot.yashlang.controller.VideoItemActions;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.service.PlaylistsImportService;
import su.sadrobot.yashlang.util.PlaylistUrlUtil;
import su.sadrobot.yashlang.view.DataSourceListener;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.VideoItemOnlyNewOnlineDataSourceFactory;
import su.sadrobot.yashlang.view.VideoItemPagedListAdapter;

/**
 *
 */
public class ConfigurePlaylistNewItemsFragment extends Fragment {
    // https://developer.android.com/guide/components/fragments
    // https://developer.android.com/guide/navigation/navigation-swipe-view

    public interface PlaylistUpdateListener {
        void onPlaylistUpdated();
    }

    public static final String PARAM_PLAYLIST_ID = "PARAM_PLAYLIST_ID";

    private ImageView playlistThumbImg;
    private TextView playlistNameTxt;
    private TextView playlistUrlTxt;

    // Элементы списка
    private View playlistItemsView;

    private View emptyView;
    private View checkInitialView;
    private ProgressBar checkProgress;
    private View checkErrorView;
    private TextView checkErrorTxt;
    private Button checkNewItemsBtn;

    private View newItemsView;
    private Button addNewItemsBtn;
    private RecyclerView videoList;

    // Добавление элементов
    private View playlistNewItemsAddTaskProgressView;

    private TextView playlistNewItemsAddTaskEnqueuedTxt;
    private TextView playlistNewItemsAddTaskStatusTxt;
    private ProgressBar playlistNewItemsAddTaskProgress;
    private TextView playlistNewItemsAddTaskCanceledTxt;
    private TextView playlistNewItemsAddTaskErrorTxt;

    private Button playlistNewItemsAddTaskRetryBtn;
    private Button playlistNewItemsAddTaskCancelBtn;
    private Button playlistNewItemsAddTaskDismissBtn;


    // режим разработки
    private Button develModeStartFakeAddNewItemsTaskBtn;
    private Button develModeStartFakeAddNewItemsTaskWithErrorBtn;

    private LiveData<PagedList<VideoItem>> videoItemsLiveData;

    private PlaylistUpdateListener playlistUpdateListener;

    private long playlistId = PlaylistInfo.ID_NONE;
    private PlaylistInfo playlistInfo;

    private long addPlaylistNewItemsTaskId = PlaylistImportTask.ID_NONE;
    private TaskController taskController;

    private PlaylistsImportService playlistsImportService;
    private ServiceConnection playlistImportServiceConnection;

    private final Handler handler = new Handler();

    // потоки для сетевых операций (могут включать обращения к базе данных) - при плохой связи
    // сетевая операция может затупить и незаметно задерживать время выполнения других фоновых
    // операций, которые не связаны с сетью
    private final ExecutorService dbAndNetworkExecutor = Executors.newSingleThreadExecutor();

    private enum State {
        NEW_ITEMS_LIST_EMPTY, NEW_ITEMS_LIST_LOAD_PROGRESS, NEW_ITEMS_LIST_LOAD_ERROR, NEW_ITEMS_LIST_LOADED,
        PLAYLIST_UPDATE_TASK_ENQUEUED, PLAYLIST_UPDATE_TASK_PROGRESS,
        PLAYLIST_UPDATE_TASK_CANCELED, PLAYLIST_UPDATE_TASK_ERROR, PLAYLIST_UPDATE_TASK_OK
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

            if (state != State.PLAYLIST_UPDATE_TASK_ENQUEUED &&
                    state != State.PLAYLIST_UPDATE_TASK_PROGRESS &&
                    state != State.PLAYLIST_UPDATE_TASK_CANCELED &&
                    state != State.PLAYLIST_UPDATE_TASK_ERROR &&
                    state != State.PLAYLIST_UPDATE_TASK_OK) {
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
                    updateStateFromTaskController();
                }
            });
        }

        @Override
        public void onFinish(final TaskController taskController) {
            playlistUpdateListener.onPlaylistUpdated();

            handler.post(new Runnable() {
                @Override
                public void run() {
                    updateStateFromTaskController();
                }
            });
        }

        @Override
        public void onStatusMsgChange(final TaskController taskController, final String status, final Exception e) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    updateStateFromTaskController();
                }
            });
        }

        @Override
        public void onCancel(final TaskController taskController) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    updateStateFromTaskController();
                }
            });
        }

        @Override
        public void onReset(final TaskController taskController) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    updateStateFromTaskController();
                }
            });
        }

        @Override
        public void onStateChange(final TaskController taskController, TaskController.TaskState state) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    updateStateFromTaskController();
                }
            });
        }
    };

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playlistId = super.getActivity().getIntent().getLongExtra(PARAM_PLAYLIST_ID, PlaylistInfo.ID_NONE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_view_playlist_new_items, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        playlistThumbImg = view.findViewById(R.id.playlist_thumb_img);
        playlistNameTxt = view.findViewById(R.id.playlist_name_txt);
        playlistUrlTxt = view.findViewById(R.id.playlist_url_txt);

        // Cписок элементов
        playlistItemsView = view.findViewById(R.id.playlist_items_view);

        emptyView = view.findViewById(R.id.empty_view);
        checkInitialView = view.findViewById(R.id.check_initial_view);
        checkProgress = view.findViewById(R.id.check_progress);
        checkErrorView = view.findViewById(R.id.check_error_view);
        checkErrorTxt = view.findViewById(R.id.check_error_txt);
        checkNewItemsBtn = view.findViewById(R.id.check_new_items_btn);


        newItemsView = view.findViewById(R.id.playlist_new_items_view);
        addNewItemsBtn = view.findViewById(R.id.add_new_items_btn);
        videoList = view.findViewById(R.id.video_list);

        // Операции и прогресс добавления
        playlistNewItemsAddTaskProgressView = view.findViewById(R.id.playlist_new_items_add_task_progress_view);

        playlistNewItemsAddTaskEnqueuedTxt = view.findViewById(R.id.playlist_new_items_add_task_enqueued_txt);
        playlistNewItemsAddTaskStatusTxt = view.findViewById(R.id.playlist_new_items_add_task_status_txt);
        playlistNewItemsAddTaskProgress = view.findViewById(R.id.playlist_new_items_add_task_progress);
        playlistNewItemsAddTaskCanceledTxt = view.findViewById(R.id.playlist_new_items_add_task_canceled_txt);
        playlistNewItemsAddTaskErrorTxt = view.findViewById(R.id.playlist_new_items_add_task_error_txt);

        playlistNewItemsAddTaskRetryBtn = view.findViewById(R.id.playlist_new_items_add_task_retry_btn);
        playlistNewItemsAddTaskCancelBtn = view.findViewById(R.id.playlist_new_items_add_task_cancel_btn);
        playlistNewItemsAddTaskDismissBtn = view.findViewById(R.id.playlist_new_items_add_task_dismiss_btn);

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
                updateVideoListBg(playlistId);
            }
        });

        addNewItemsBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewItemsBg();
            }
        });


        playlistNewItemsAddTaskRetryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlaylistsImportService.retryPlaylistImportTask(ConfigurePlaylistNewItemsFragment.this.getContext(),
                        addPlaylistNewItemsTaskId);
            }
        });

        playlistNewItemsAddTaskCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlaylistsImportService.cancelPlaylistImportTask(ConfigurePlaylistNewItemsFragment.this.getContext(),
                        addPlaylistNewItemsTaskId);
            }
        });

        playlistNewItemsAddTaskDismissBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlaylistsImportService.dismissPlaylistImportTask(ConfigurePlaylistNewItemsFragment.this.getContext(),
                        addPlaylistNewItemsTaskId);
            }
        });

        if (ConfigOptions.DEVEL_MODE_ON) {
            develModeStartFakeAddNewItemsTaskBtn.setVisibility(View.VISIBLE);
            develModeStartFakeAddNewItemsTaskBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    addPlaylistNewItemsTaskId = PlaylistsImportService.develModeStartFakeTask(
                            ConfigurePlaylistNewItemsFragment.this.getContext(),
                            PlaylistImportTask.PlaylistImportTaskType.ADD_NEW_ITEMS_TO_PLAYLIST,
                            playlistId,
                            20000, false);
                }
            });

            develModeStartFakeAddNewItemsTaskWithErrorBtn.setVisibility(View.VISIBLE);
            develModeStartFakeAddNewItemsTaskWithErrorBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    updateControlsVisibility();
                    addPlaylistNewItemsTaskId = PlaylistsImportService.develModeStartFakeTask(
                            ConfigurePlaylistNewItemsFragment.this.getContext(),
                            PlaylistImportTask.PlaylistImportTaskType.ADD_NEW_ITEMS_TO_PLAYLIST,
                            playlistId,
                            20000, true);
                }
            });
        }

        // и здесь же загрузим список видео (если делать это в onResume,
        // то список будет каждый раз сбрасываться при потере фокуса активити)
        updateVideoListBg(playlistId);
    }

    @Override
    public void onResume() {
        super.onResume();

        playlistImportServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName name, final IBinder service) {
                playlistsImportService = ((PlaylistsImportService.PlaylistsImportServiceBinder) service).getService();

                // посмотреть, нет ли запущенных задач обновления, связанных с этим плейлистом
                // если есть, то загрузим информацию о них
                final PlaylistImportTask importTask = playlistsImportService.getImportTaskForPlaylist(playlistId);
                if (importTask != null && importTask.getTaskType() == PlaylistImportTask.PlaylistImportTaskType.ADD_NEW_ITEMS_TO_PLAYLIST) {
                    addPlaylistNewItemsTaskId = importTask.getId();
                    taskController = importTask.getTaskController();
                    taskController.setTaskListener(addNewItemsTaskListener);
                }
                updateStateFromTaskController();

                playlistsImportService.setServiceListener(new PlaylistsImportService.PlaylistsImportServiceListener() {
                    @Override
                    public void onImportPlaylistTaskAdded(final PlaylistImportTask importTask) {
                        if (importTask.getPlaylistInfo().getId() == playlistId &&
                                importTask.getTaskType() == PlaylistImportTask.PlaylistImportTaskType.ADD_NEW_ITEMS_TO_PLAYLIST) {
                            addPlaylistNewItemsTaskId = importTask.getId();
                            taskController = importTask.getTaskController();
                            taskController.setTaskListener(addNewItemsTaskListener);
                            updateStateFromTaskController();
                        }
                    }

                    @Override
                    public void onImportPlaylistTaskRemoved(final PlaylistImportTask importTask) {
                        if (importTask.getPlaylistInfo().getId() == playlistId &&
                                importTask.getTaskType() == PlaylistImportTask.PlaylistImportTaskType.ADD_NEW_ITEMS_TO_PLAYLIST) {
                            taskController.removeTaskListener(addNewItemsTaskListener);
                            taskController = null;
                            addPlaylistNewItemsTaskId = PlaylistImportTask.ID_NONE;
                            updateStateFromTaskController();
                        }
                    }
                });
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
                playlistsImportService.removeServiceListener();
                playlistsImportService = null;
                if (taskController != null) {
                    taskController.removeTaskListener(addNewItemsTaskListener);
                    taskController = null;
                }
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

    public void setPlaylistUpdateListener(PlaylistUpdateListener playlistUpdateListener) {
        this.playlistUpdateListener = playlistUpdateListener;
    }

    private void updateStateFromTaskController() {
        // текущее состояние в зависимости от состояния таск-контроллера
        if (taskController != null) {

            playlistNewItemsAddTaskStatusTxt.setText(taskController.getStatusMsg());
            if (taskController.getException() != null) {
                final Exception e = taskController.getException();
                playlistNewItemsAddTaskErrorTxt.setText(e.getMessage()
                        + (e.getCause() != null ? "\n(" + e.getCause().getMessage() + ")" : ""));
            }

            switch (taskController.getState()) {
                case WAIT:
                    // ожидаение действия пользователя:
                    // если canceled: dismiss или retry
                    // если не сanceled и ошибка, то cancel или retry
                    // если не сanceled и ошибки нет (завершено успешно): dismiss
                    if (taskController.isCanceled()) {
                        // canceled
                        state = State.PLAYLIST_UPDATE_TASK_CANCELED;
                    } else if (taskController.getException() != null) {
                        state = State.PLAYLIST_UPDATE_TASK_ERROR;
                    } else {
                        // не canceled и нет ошибки, значит задача выполнена
                        state = State.PLAYLIST_UPDATE_TASK_OK;
                    }
                    break;
                case ENQUEUED:
                    // в очереди на выполнение
                    state = State.PLAYLIST_UPDATE_TASK_ENQUEUED;
                    break;
                case ACTIVE:
                    // выполняется
                    state = State.PLAYLIST_UPDATE_TASK_PROGRESS;
                    break;
            }
        } else {
            state = State.NEW_ITEMS_LIST_EMPTY;
        }
        updateControlsVisibility();
    }

    private void updateControlsVisibility() {
        switch (state){
            case NEW_ITEMS_LIST_EMPTY:
                playlistItemsView.setVisibility(View.VISIBLE);
                playlistNewItemsAddTaskProgressView.setVisibility(View.GONE);

                emptyView.setVisibility(View.VISIBLE);
                newItemsView.setVisibility(View.GONE);

                checkInitialView.setVisibility(View.VISIBLE);
                checkErrorView.setVisibility(View.GONE);
                checkProgress.setVisibility(View.INVISIBLE);

                checkNewItemsBtn.setEnabled(true);

                break;
            case NEW_ITEMS_LIST_LOAD_PROGRESS:
                playlistItemsView.setVisibility(View.VISIBLE);
                playlistNewItemsAddTaskProgressView.setVisibility(View.GONE);

                emptyView.setVisibility(View.VISIBLE);
                newItemsView.setVisibility(View.GONE);

                checkInitialView.setVisibility(View.INVISIBLE);
                checkErrorView.setVisibility(View.GONE);
                checkProgress.setVisibility(View.VISIBLE);

                checkNewItemsBtn.setEnabled(false);

                break;
            case NEW_ITEMS_LIST_LOAD_ERROR:
                playlistItemsView.setVisibility(View.VISIBLE);
                playlistNewItemsAddTaskProgressView.setVisibility(View.GONE);

                emptyView.setVisibility(View.VISIBLE);
                newItemsView.setVisibility(View.GONE);

                checkInitialView.setVisibility(View.GONE);
                checkErrorView.setVisibility(View.VISIBLE);
                checkProgress.setVisibility(View.GONE);

                checkNewItemsBtn.setEnabled(true);

                break;
            case NEW_ITEMS_LIST_LOADED:
                playlistItemsView.setVisibility(View.VISIBLE);
                playlistNewItemsAddTaskProgressView.setVisibility(View.GONE);

                emptyView.setVisibility(View.GONE);
                newItemsView.setVisibility(View.VISIBLE);

                break;

            case PLAYLIST_UPDATE_TASK_ENQUEUED:
                playlistItemsView.setVisibility(View.GONE);
                playlistNewItemsAddTaskProgressView.setVisibility(View.VISIBLE);

                playlistNewItemsAddTaskEnqueuedTxt.setVisibility(View.VISIBLE);
                playlistNewItemsAddTaskStatusTxt.setVisibility(View.GONE);
                playlistNewItemsAddTaskProgress.setVisibility(View.VISIBLE);
                playlistNewItemsAddTaskCanceledTxt.setVisibility(View.GONE);
                playlistNewItemsAddTaskErrorTxt.setVisibility(View.GONE);

                playlistNewItemsAddTaskRetryBtn.setVisibility(View.GONE);
                playlistNewItemsAddTaskCancelBtn.setVisibility(View.VISIBLE);
                playlistNewItemsAddTaskDismissBtn.setVisibility(View.GONE);

                break;
            case PLAYLIST_UPDATE_TASK_PROGRESS:
                playlistItemsView.setVisibility(View.GONE);
                playlistNewItemsAddTaskProgressView.setVisibility(View.VISIBLE);

                playlistNewItemsAddTaskEnqueuedTxt.setVisibility(View.GONE);
                playlistNewItemsAddTaskStatusTxt.setVisibility(View.VISIBLE);
                playlistNewItemsAddTaskProgress.setVisibility(View.VISIBLE);
                playlistNewItemsAddTaskCanceledTxt.setVisibility(View.GONE);
                playlistNewItemsAddTaskErrorTxt.setVisibility(View.GONE);

                playlistNewItemsAddTaskRetryBtn.setVisibility(View.GONE);
                playlistNewItemsAddTaskCancelBtn.setVisibility(View.VISIBLE);
                playlistNewItemsAddTaskDismissBtn.setVisibility(View.GONE);

                break;
            case PLAYLIST_UPDATE_TASK_CANCELED:
                playlistItemsView.setVisibility(View.GONE);
                playlistNewItemsAddTaskProgressView.setVisibility(View.VISIBLE);

                playlistNewItemsAddTaskEnqueuedTxt.setVisibility(View.GONE);
                playlistNewItemsAddTaskStatusTxt.setVisibility(View.GONE);
                playlistNewItemsAddTaskProgress.setVisibility(View.GONE);
                playlistNewItemsAddTaskCanceledTxt.setVisibility(View.VISIBLE);
                playlistNewItemsAddTaskErrorTxt.setVisibility(View.GONE);

                playlistNewItemsAddTaskRetryBtn.setVisibility(View.VISIBLE);
                playlistNewItemsAddTaskCancelBtn.setVisibility(View.GONE);
                playlistNewItemsAddTaskDismissBtn.setVisibility(View.VISIBLE);

                break;
            case PLAYLIST_UPDATE_TASK_ERROR:
                playlistItemsView.setVisibility(View.GONE);
                playlistNewItemsAddTaskProgressView.setVisibility(View.VISIBLE);

                playlistNewItemsAddTaskEnqueuedTxt.setVisibility(View.GONE);
                playlistNewItemsAddTaskStatusTxt.setVisibility(View.VISIBLE);
                playlistNewItemsAddTaskProgress.setVisibility(View.GONE);
                playlistNewItemsAddTaskCanceledTxt.setVisibility(View.GONE);
                playlistNewItemsAddTaskErrorTxt.setVisibility(View.VISIBLE);

                playlistNewItemsAddTaskRetryBtn.setVisibility(View.VISIBLE);
                playlistNewItemsAddTaskCancelBtn.setVisibility(View.VISIBLE);
                playlistNewItemsAddTaskDismissBtn.setVisibility(View.GONE);

                break;
            case PLAYLIST_UPDATE_TASK_OK:
                playlistItemsView.setVisibility(View.GONE);
                playlistNewItemsAddTaskProgressView.setVisibility(View.VISIBLE);

                playlistNewItemsAddTaskEnqueuedTxt.setVisibility(View.GONE);
                playlistNewItemsAddTaskStatusTxt.setVisibility(View.VISIBLE);
                playlistNewItemsAddTaskProgress.setVisibility(View.GONE);
                playlistNewItemsAddTaskCanceledTxt.setVisibility(View.GONE);
                playlistNewItemsAddTaskErrorTxt.setVisibility(View.GONE);

                playlistNewItemsAddTaskRetryBtn.setVisibility(View.GONE);
                playlistNewItemsAddTaskCancelBtn.setVisibility(View.GONE);
                playlistNewItemsAddTaskDismissBtn.setVisibility(View.VISIBLE);

                break;
        }
    }

    /**
     * Update video list in background
     *
     * @param playlistId
     */
    private void updateVideoListBg(final long playlistId) {
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
                playlistInfo = VideoDatabase.getDbInstance(getContext()).playlistInfoDao().getById(playlistId);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        playlistNameTxt.setText(playlistInfo.getName());
                        playlistUrlTxt.setText(PlaylistUrlUtil.cleanupUrl(playlistInfo.getUrl()));

                        setupVideoListAdapter(playlistId);
                    }
                });

                // иконка плейлиста - может грузиться подольше, без интернета вообще не загрузится
                try {
                    final Bitmap plThumb = ThumbManager.getInstance().loadPlaylistThumb(
                            ConfigurePlaylistNewItemsFragment.this.getContext(), playlistInfo);
                    playlistInfo.setThumbBitmap(plThumb);

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            playlistThumbImg.setImageBitmap(playlistInfo.getThumbBitmap());
                        }
                    });
                } catch (final Exception e) {
                }
            }
        });
    }

    private void setupVideoListAdapter(final long playlistId) {
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
                        VideoItemActions.actionPlayNewInPlaylist(
                                ConfigurePlaylistNewItemsFragment.this.getContext(), videoItem, position);
                    }

                    @Override
                    public boolean onItemLongClick(final View view, final int position, final VideoItem videoItem) {
                        final PopupMenu popup = new PopupMenu(ConfigurePlaylistNewItemsFragment.this.getContext(),
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
                                                VideoItemActions.actionCopyVideoName(ConfigurePlaylistNewItemsFragment.this.getContext(), videoItem);
                                                break;
                                            }
                                            case R.id.action_copy_video_url: {
                                                VideoItemActions.actionCopyVideoUrl(ConfigurePlaylistNewItemsFragment.this.getContext(), videoItem);
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
                new VideoItemOnlyNewOnlineDataSourceFactory(this.getContext(), playlistId, false,
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
        addPlaylistNewItemsTaskId = PlaylistsImportService.addPlaylistNewItems(this.getContext(), playlistId);
    }
}
