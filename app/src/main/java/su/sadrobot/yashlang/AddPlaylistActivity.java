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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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

import su.sadrobot.yashlang.controller.ContentLoader;
import su.sadrobot.yashlang.controller.PlaylistImportTask;
import su.sadrobot.yashlang.controller.PlaylistInfoActions;
import su.sadrobot.yashlang.controller.TaskController;
import su.sadrobot.yashlang.controller.ThumbManager;
import su.sadrobot.yashlang.controller.VideoItemActions;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.player.RecommendationsProviderFactory;
import su.sadrobot.yashlang.service.PlaylistsImportService;
import su.sadrobot.yashlang.util.PlaylistUrlUtil;
import su.sadrobot.yashlang.view.DataSourceListener;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.PlaylistImportTaskArrayAdapter;
import su.sadrobot.yashlang.view.VideoItemOnlineDataSourceFactory;
import su.sadrobot.yashlang.view.VideoItemPagedListAdapter;

/**
 *
 */
public class AddPlaylistActivity extends AppCompatActivity {

    //
    // Панель плейлиста - ввод адреса плюс дочерние панели:
    //   информация о плейлисте список видео, статусы загрузки и т.п.
    private View playlistView;
    private EditText playlistUrlInput;
    private ImageButton searchPlaylistBtn;

    // Начальный экран с пустым списком
    private View playlistEmptyInitialView;
    private Button playlistSearch2Btn;

    // Прогресс загрузки плейлиста
    private View playlistLoadProgressView;

    // Ошибка загрузки плейлиста
    private View playlistLoadErrorView;
    private Button playlistSearch3Btn;
    private Button playlistReloadBtn;
    private TextView playlistLoadErrorTxt;

    // Загруженный плейлист
    private View playlistLoadedView;
    private ImageView playlistThumbImg;
    private TextView playlistNameTxt;
    private Button addPlaylistBtn;
    private View playlistLoadedEmptyView;
    private RecyclerView videoList;

    //
    // Панель - прогресс добавления плейлиста
    private View playlistAddTaskProgressView;
    private ImageView playlistAddTaskPlThumbImg;
    private TextView playlistAddTaskPlNameTxt;
    private TextView playlistAddTaskPlUrlTxt;

    private TextView playlistAddTaskEnqueuedTxt;
    private TextView playlistAddTaskStatusTxt;

    private ProgressBar playlistAddTaskProgress;
    private TextView playlistAddTaskCanceledTxt;
    private TextView playlistAddTaskErrorTxt;

    // Действия при добавлении плейлиста (внутри панели прогресса добавления плейлиста)
    private Button playlistAddTaskCancelBtn;
    private Button playlistAddTaskRetryBtn;
    private Button playlistAddTaskDismissBtn;
    private Button playlistAddTaskAddAnotherBtn;

    // режим разработки
    private Button develModeStartFakeAddTaskBtn;
    private Button develModeStartFakeAddTaskWithErrorBtn;

    private LiveData<PagedList<VideoItem>> videoItemsLiveData;

    private final Handler handler = new Handler();

    // потоки для сетевых операций (могут включать обращения к базе данных) - при плохой связи
    // сетевая операция может затупить и незаметно задерживать время выполнения других фоновых
    // операций, которые не связаны с сетью
    private final ExecutorService dbAndNetworkExecutor = Executors.newSingleThreadExecutor();

    private enum State {
        ITEMS_LIST_EMPTY, ITEMS_LIST_LOAD_PROGRESS, ITEMS_LIST_LOAD_ERROR, ITEMS_LIST_LOADED_EMPTY, ITEMS_LIST_LOADED,
        PLAYLIST_ADD_TASK_ENQUEUED, PLAYLIST_ADD_TASK_PROGRESS,
        PLAYLIST_ADD_TASK_CANCELED, PLAYLIST_ADD_TASK_ERROR, PLAYLIST_ADD_TASK_OK
    }

    private State state = State.ITEMS_LIST_EMPTY;
    private boolean playlistLoadError = false;

    private PlaylistsImportService playlistsImportService;
    private ServiceConnection playlistImportServiceConnection;

    private PlaylistInfo loadedPlaylist = null;

    private long addPlaylistTaskId = PlaylistImportTask.ID_NONE;
    private PlaylistImportTask importTask;

    private final RecyclerView.AdapterDataObserver emptyListObserver = new RecyclerView.AdapterDataObserver() {
        // https://stackoverflow.com/questions/47417645/empty-view-on-a-recyclerview
        // https://stackoverflow.com/questions/27414173/equivalent-of-listview-setemptyview-in-recyclerview
        // https://gist.github.com/sheharyarn/5602930ad84fa64c30a29ab18eb69c6e
        private void checkIfEmpty() {
            // в любом случае прячем прогресс, если он был включен

            if (state != State.PLAYLIST_ADD_TASK_ENQUEUED &&
                    state != State.PLAYLIST_ADD_TASK_PROGRESS &&
                    state != State.PLAYLIST_ADD_TASK_CANCELED &&
                    state != State.PLAYLIST_ADD_TASK_ERROR &&
                    state != State.PLAYLIST_ADD_TASK_OK) {
                // Будем менять состояние здесь только в том случае, если у нас сейчас активна панель
                // со списком новых видео. Если мы в процессе добавления новых видео в базу, то
                // состояние здесь менять не будем, чтобы оно не скрыло панели статуса добавления.
                // Такое может произойти, например, если мы крутанули список новых видео вниз
                // и нажали кнопку "добавить новые видео": элементы списка начали подгружаться в фоне
                // и генерировать события, которые приведут программу сюда уже после того, как
                // пользователь начнет наблюдать панели со статусом добавления новых элементов.

                final boolean listIsEmpty = videoList.getAdapter() == null || videoList.getAdapter().getItemCount() == 0;

                if (playlistLoadError) {
                    state = State.ITEMS_LIST_LOAD_ERROR;
                } else if (listIsEmpty) {
                    state = State.ITEMS_LIST_LOADED_EMPTY;
                } else {
                    state = State.ITEMS_LIST_LOADED;
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

    private final TaskController.TaskListener addPlaylistTaskListener = new TaskController.TaskAdapter() {
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_add_playlist);

        playlistView = findViewById(R.id.playlist_view);
        playlistUrlInput = findViewById(R.id.playlist_url_input);
        searchPlaylistBtn = findViewById(R.id.playlist_search_btn);

        playlistEmptyInitialView = findViewById(R.id.playlist_empty_initial_view);
        playlistSearch2Btn = findViewById(R.id.playlist_search2_btn);

        playlistLoadProgressView = findViewById(R.id.playlist_load_progress_view);

        playlistLoadErrorView = findViewById(R.id.playlist_load_error_view);
        playlistSearch3Btn = findViewById(R.id.playlist_search3_btn);
        playlistReloadBtn = findViewById(R.id.playlist_reload_btn);
        playlistLoadErrorTxt = findViewById(R.id.playlist_load_error_txt);

        playlistLoadedView = findViewById(R.id.playlist_loaded_view);
        playlistThumbImg = findViewById(R.id.playlist_thumb_img);
        playlistNameTxt = findViewById(R.id.playlist_name_txt);
        addPlaylistBtn = findViewById(R.id.playlist_add_btn);
        playlistLoadedEmptyView = findViewById(R.id.playlist_loaded_empty_view);
        videoList = findViewById(R.id.video_list);

        playlistAddTaskProgressView = findViewById(R.id.playlist_add_task_progress_view);
        playlistAddTaskPlThumbImg = findViewById(R.id.playlist_add_task_pl_thumb_img);
        playlistAddTaskPlNameTxt = findViewById(R.id.playlist_add_task_pl_name_txt);
        playlistAddTaskPlUrlTxt = findViewById(R.id.playlist_add_task_pl_url_txt);

        playlistAddTaskEnqueuedTxt = findViewById(R.id.playlist_add_task_enqueued_txt);
        playlistAddTaskStatusTxt = findViewById(R.id.playlist_add_task_status_txt);
        playlistAddTaskProgress = findViewById(R.id.playlist_add_task_progress);
        playlistAddTaskCanceledTxt = findViewById(R.id.playlist_add_task_canceled_txt);
        playlistAddTaskErrorTxt = findViewById(R.id.playlist_add_task_error_txt);

        playlistAddTaskCancelBtn = findViewById(R.id.playlist_add_task_cancel_btn);
        playlistAddTaskRetryBtn = findViewById(R.id.playlist_add_task_retry_btn);
        playlistAddTaskDismissBtn = findViewById(R.id.playlist_add_task_dismiss_btn);
        playlistAddTaskAddAnotherBtn = findViewById(R.id.playlist_add_task_add_another_btn);

        develModeStartFakeAddTaskBtn = findViewById(R.id.devel_mode_fake_add_btn);
        develModeStartFakeAddTaskWithErrorBtn = findViewById(R.id.devel_mode_fake_add_with_error_btn);

        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        videoList.setLayoutManager(linearLayoutManager);
        videoList.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                // чтобы картинки и текст не сбивались в кучку при быстрой промотке
                // см: https://github.com/sadr0b0t/yashlang/issues/129
                return true;
            }
        });

        playlistUrlInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event) {
                final String playlistUrl = playlistUrlInput.getText().toString();
                if (playlistUrl.length() > 0) {
                    updateVideoListBg(playlistUrl);
                }
                return false;
            }
        });
        playlistUrlInput.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count > 10) {
                    // пробовать загрузить плейлист если в поле ввода более 10 символов:
                    // - будем считать, что это вставка адреса из контекстного меню
                    // - даже если это не вставка, загрузка в процессе набора не повредит
                    // https://stackoverflow.com/questions/14980227/android-intercept-paste-copy-cut-on-edittext/
                    final String playlistUrl = playlistUrlInput.getText().toString();
                    updateVideoListBg(playlistUrl);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            public void afterTextChanged(Editable s) {

            }
        });

        searchPlaylistBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(AddPlaylistActivity.this, SearchOnlinePlaylistActivity.class),
                        SearchOnlinePlaylistActivity.REQUEST_SEARCH_PLAYLIST);
            }
        });

        playlistSearch2Btn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(AddPlaylistActivity.this, SearchOnlinePlaylistActivity.class),
                        SearchOnlinePlaylistActivity.REQUEST_SEARCH_PLAYLIST);
            }
        });

        playlistSearch3Btn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(AddPlaylistActivity.this, SearchOnlinePlaylistActivity.class),
                        SearchOnlinePlaylistActivity.REQUEST_SEARCH_PLAYLIST);
            }
        });

        // перезагрузка в случае ошибки загрузки плейлиста
        playlistReloadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playlistLoadErrorTxt.setText("");

                final String playlistUrl = playlistUrlInput.getText().toString();
                updateVideoListBg(playlistUrl);
            }
        });


        addPlaylistBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                addLoadedPlaylistBg();
            }
        });

        playlistAddTaskCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlaylistsImportService.cancelPlaylistImportTask(AddPlaylistActivity.this, addPlaylistTaskId);
            }
        });

        playlistAddTaskRetryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlaylistsImportService.retryPlaylistImportTask(AddPlaylistActivity.this, addPlaylistTaskId);
            }
        });

        playlistAddTaskDismissBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // удалить задание из сервиса
                // это доолжно привести к тому, что экран будет закрыт в обработчике события
                // удаления задания с текущим идентификатором
                PlaylistsImportService.dismissPlaylistImportTask(AddPlaylistActivity.this, addPlaylistTaskId);
            }
        });

        playlistAddTaskAddAnotherBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // привести экран к начальному положению
                // полностью отвязать экран от текущей задачи, если она была активна
                if (importTask != null) {
                    importTask.getTaskController().removeTaskListener(addPlaylistTaskListener);
                    importTask = null;
                    addPlaylistTaskId = PlaylistImportTask.ID_NONE;
                }
                // этот метод обнулит элементы управления и покажет начальный экран в том случае,
                // если нет никакой информации о привязанной задаче
                updateStateFromTaskController();
            }
        });

        if (ConfigOptions.DEVEL_MODE_ON) {
            playlistUrlInput.setText("https://video.blender.org/c/blender_channel/videos");

            develModeStartFakeAddTaskBtn.setVisibility(View.VISIBLE);
            develModeStartFakeAddTaskBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // показать панель статуса добавления плейлиста
                    state = State.PLAYLIST_ADD_TASK_ENQUEUED;
                    updateControlsVisibility();
                    addPlaylistTaskId = PlaylistsImportService.develModeStartFakeTask(
                            AddPlaylistActivity.this,
                            PlaylistImportTask.PlaylistImportTaskType.ADD_NEW_PLAYLIST,
                            PlaylistInfo.ID_NONE,
                            20000, false);
                }
            });

            develModeStartFakeAddTaskWithErrorBtn.setVisibility(View.VISIBLE);
            develModeStartFakeAddTaskWithErrorBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // показать панель статуса добавления плейлиста
                    state = State.PLAYLIST_ADD_TASK_ENQUEUED;
                    updateControlsVisibility();
                    addPlaylistTaskId = PlaylistsImportService.develModeStartFakeTask(
                            AddPlaylistActivity.this,
                            PlaylistImportTask.PlaylistImportTaskType.ADD_NEW_PLAYLIST,
                            PlaylistInfo.ID_NONE,
                            20000, true);
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        playlistImportServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName name, final IBinder service) {
                playlistsImportService = ((PlaylistsImportService.PlaylistsImportServiceBinder) service).getService();

                if (addPlaylistTaskId != PlaylistImportTask.ID_NONE) {
                    // этот экран уже инициировал задачу - если она все еще активна, показываем статус,
                    // если завершилась и скрыта, закрываем экран сразу
                    importTask = playlistsImportService.getImportTask(addPlaylistTaskId);
                    if (importTask != null) {
                        importTask.getTaskController().setTaskListener(addPlaylistTaskListener);
                        updateStateFromTaskController();
                    } else {
                        // есть индентификатор задачи addPlaylistTaskId, но в сервисе такой задачи нет,
                        // значит, мы уту задачу инициировали на этом экране, она так или иначе звершила
                        // выполнение и пользователь её скрыл - закрываем текущий экран в таком случае
                        AddPlaylistActivity.this.finish();
                    }

                } else {
                    // экран инициирует новую задачу, ждем, когда она будет добавлена сервисом
                    playlistsImportService.setServiceListener(new PlaylistsImportService.PlaylistsImportServiceListener() {
                        @Override
                        public void onImportPlaylistTaskAdded(final PlaylistImportTask importTask) {
                            // убедимся, что это событие относится к команде, отправленной именно из этой активити
                            if (importTask.getId() == addPlaylistTaskId) {
                                AddPlaylistActivity.this.importTask = importTask;
                                importTask.getTaskController().setTaskListener(addPlaylistTaskListener);
                                updateStateFromTaskController();
                            }
                        }

                        @Override
                        public void onImportPlaylistTaskRemoved(final PlaylistImportTask importTask) {
                            if (importTask.getId() == addPlaylistTaskId) {
                                // пользователь удали из сервиса задачу, ассоциированную с текущим экраном

                                // на всякий случай отвяжем экран от внешних ресурсов
                                importTask.getTaskController().removeTaskListener(addPlaylistTaskListener);
                                // это можно было бы сделать, если бы мы дальше не закрыли экран
                                //AddPlaylistActivity.this.importTask = null;
                                //addPlaylistTaskId = PlaylistImportTask.ID_NONE;
                                //updateStateFromTaskController();

                                // закрываем текущий экран и открываем активити с добавленным плейлистом,
                                // т.к. скрытие задачи здесь говорит о том, что пользователь запустил задачу,
                                // видел ход ее выполнения и в финале нажал "скрыть"
                                PlaylistInfoActions.actionConfigurePlaylist(
                                        AddPlaylistActivity.this, importTask.getPlaylistInfo().getId());
                                AddPlaylistActivity.this.finish();
                            }
                        }
                    });
                }
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
                playlistsImportService.removeServiceListener();
                playlistsImportService = null;
                if (importTask != null) {
                    importTask.getTaskController().removeTaskListener(addPlaylistTaskListener);
                    importTask = null;
                }
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
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SearchOnlinePlaylistActivity.REQUEST_SEARCH_PLAYLIST && data != null) {
            final String playlistUrl = data.getStringExtra(SearchOnlinePlaylistActivity.RESULT_PLAYLIST_URL);
            playlistUrlInput.setText(playlistUrl);
            updateVideoListBg(playlistUrl);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void updateStateFromTaskController() {
        // текущее состояние в зависимости от состояния таск-контроллера
        if (importTask != null) {
            // информация о плейлисте
            if (importTask.getPlaylistInfo().getThumbBitmap() != null) {
                playlistAddTaskPlThumbImg.setImageBitmap(importTask.getPlaylistInfo().getThumbBitmap());
            } else {
                playlistAddTaskPlThumbImg.setImageResource(R.drawable.ic_yashlang_thumb);
                final PlaylistImportTask _importTask = importTask;
                dbAndNetworkExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final Bitmap thumb =
                                ThumbManager.getInstance().loadPlaylistThumb(AddPlaylistActivity.this, _importTask.getPlaylistInfo());
                        _importTask.getPlaylistInfo().setThumbBitmap(thumb);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (_importTask == importTask) {
                                    // пока загружали иконку плейлиста текущая задача не поменялась
                                    playlistAddTaskPlThumbImg.setImageBitmap(importTask.getPlaylistInfo().getThumbBitmap());
                                }
                            }
                        });
                    }
                });
            }
            playlistAddTaskPlNameTxt.setText(importTask.getPlaylistInfo().getName());
            playlistAddTaskPlUrlTxt.setText(PlaylistUrlUtil.cleanupUrl(importTask.getPlaylistInfo().getUrl()));

            // информация о ходе выполнения задачи
            playlistAddTaskStatusTxt.setText(importTask.getTaskController().getStatusMsg());
            if (importTask.getTaskController().getException() != null) {
                final Exception e = importTask.getTaskController().getException();
                playlistAddTaskErrorTxt.setText(e.getMessage()
                        + (e.getCause() != null ? "\n(" + e.getCause().getMessage() + ")" : ""));
            }

            switch (importTask.getTaskController().getState()) {
                case WAIT:
                    // ожидаение действия пользователя:
                    // если canceled: dismiss или retry
                    // если не сanceled и ошибка, то cancel или retry
                    // если не сanceled и ошибки нет (завершено успешно): dismiss
                    if (importTask.getTaskController().isCanceled()) {
                        // canceled
                        state = State.PLAYLIST_ADD_TASK_CANCELED;
                    } else if (importTask.getTaskController().getException() != null) {
                        state = State.PLAYLIST_ADD_TASK_ERROR;
                    } else {
                        // не canceled и нет ошибки, значит задача выполнена
                        state = State.PLAYLIST_ADD_TASK_OK;
                    }
                    break;
                case ENQUEUED:
                    // в очереди на выполнение
                    state = State.PLAYLIST_ADD_TASK_ENQUEUED;
                    break;
                case ACTIVE:
                    // выполняется
                    state = State.PLAYLIST_ADD_TASK_PROGRESS;
                    break;
            }
        } else {
            // сбросить все значения
            playlistUrlInput.setText("");
            playlistAddTaskPlNameTxt.setText("");
            playlistAddTaskPlUrlTxt.setText("");
            playlistAddTaskStatusTxt.setText("");
            playlistAddTaskErrorTxt.setText("");

            state = State.ITEMS_LIST_EMPTY;
        }
        updateControlsVisibility();
    }

    private void updateControlsVisibility() {
        switch (state){
            case ITEMS_LIST_EMPTY:
                playlistView.setVisibility(View.VISIBLE);
                playlistAddTaskProgressView.setVisibility(View.GONE);

                playlistEmptyInitialView.setVisibility(View.VISIBLE);
                playlistLoadProgressView.setVisibility(View.GONE);
                playlistLoadErrorView.setVisibility(View.GONE);
                playlistLoadedView.setVisibility(View.GONE);
                break;
            case ITEMS_LIST_LOAD_PROGRESS:
                playlistView.setVisibility(View.VISIBLE);
                playlistAddTaskProgressView.setVisibility(View.GONE);

                playlistEmptyInitialView.setVisibility(View.GONE);
                playlistLoadProgressView.setVisibility(View.VISIBLE);
                playlistLoadErrorView.setVisibility(View.GONE);
                playlistLoadedView.setVisibility(View.GONE);

                break;
            case ITEMS_LIST_LOAD_ERROR:
                playlistView.setVisibility(View.VISIBLE);
                playlistAddTaskProgressView.setVisibility(View.GONE);

                playlistEmptyInitialView.setVisibility(View.GONE);
                playlistLoadProgressView.setVisibility(View.GONE);
                playlistLoadErrorView.setVisibility(View.VISIBLE);
                playlistLoadedView.setVisibility(View.GONE);

                break;
            case ITEMS_LIST_LOADED_EMPTY:
                playlistView.setVisibility(View.VISIBLE);
                playlistAddTaskProgressView.setVisibility(View.GONE);

                playlistEmptyInitialView.setVisibility(View.GONE);
                playlistLoadProgressView.setVisibility(View.GONE);
                playlistLoadErrorView.setVisibility(View.GONE);
                playlistLoadedView.setVisibility(View.VISIBLE);

                playlistLoadedEmptyView.setVisibility(View.VISIBLE);
                videoList.setVisibility(View.GONE);

                break;
            case ITEMS_LIST_LOADED:
                playlistView.setVisibility(View.VISIBLE);
                playlistAddTaskProgressView.setVisibility(View.GONE);

                playlistEmptyInitialView.setVisibility(View.GONE);
                playlistLoadProgressView.setVisibility(View.GONE);
                playlistLoadErrorView.setVisibility(View.GONE);
                playlistLoadedView.setVisibility(View.VISIBLE);

                playlistLoadedEmptyView.setVisibility(View.GONE);
                videoList.setVisibility(View.VISIBLE);

                break;
            case PLAYLIST_ADD_TASK_ENQUEUED:
                playlistView.setVisibility(View.GONE);
                playlistAddTaskProgressView.setVisibility(View.VISIBLE);

                playlistAddTaskEnqueuedTxt.setVisibility(View.VISIBLE);
                playlistAddTaskStatusTxt.setVisibility(View.GONE);
                playlistAddTaskProgress.setVisibility(View.VISIBLE);
                playlistAddTaskCanceledTxt.setVisibility(View.GONE);
                playlistAddTaskErrorTxt.setVisibility(View.GONE);

                playlistAddTaskCancelBtn.setVisibility(View.VISIBLE);
                playlistAddTaskRetryBtn.setVisibility(View.GONE);
                playlistAddTaskDismissBtn.setVisibility(View.GONE);
                playlistAddTaskAddAnotherBtn.setVisibility(View.VISIBLE);

                break;
            case PLAYLIST_ADD_TASK_PROGRESS:
                playlistView.setVisibility(View.GONE);
                playlistAddTaskProgressView.setVisibility(View.VISIBLE);

                playlistAddTaskEnqueuedTxt.setVisibility(View.GONE);
                playlistAddTaskStatusTxt.setVisibility(View.VISIBLE);
                playlistAddTaskProgress.setVisibility(View.VISIBLE);
                playlistAddTaskCanceledTxt.setVisibility(View.GONE);
                playlistAddTaskErrorTxt.setVisibility(View.GONE);

                playlistAddTaskCancelBtn.setVisibility(View.VISIBLE);
                playlistAddTaskRetryBtn.setVisibility(View.GONE);
                playlistAddTaskDismissBtn.setVisibility(View.GONE);
                playlistAddTaskAddAnotherBtn.setVisibility(View.VISIBLE);

                break;
            case PLAYLIST_ADD_TASK_CANCELED:
                playlistView.setVisibility(View.GONE);
                playlistAddTaskProgressView.setVisibility(View.VISIBLE);

                playlistAddTaskEnqueuedTxt.setVisibility(View.GONE);
                playlistAddTaskStatusTxt.setVisibility(View.GONE);
                playlistAddTaskProgress.setVisibility(View.GONE);
                playlistAddTaskCanceledTxt.setVisibility(View.VISIBLE);
                playlistAddTaskErrorTxt.setVisibility(View.GONE);

                playlistAddTaskCancelBtn.setVisibility(View.GONE);
                playlistAddTaskRetryBtn.setVisibility(View.VISIBLE);
                playlistAddTaskDismissBtn.setVisibility(View.VISIBLE);
                playlistAddTaskAddAnotherBtn.setVisibility(View.VISIBLE);

                break;
            case PLAYLIST_ADD_TASK_ERROR:
                playlistView.setVisibility(View.GONE);
                playlistAddTaskProgressView.setVisibility(View.VISIBLE);

                playlistAddTaskEnqueuedTxt.setVisibility(View.GONE);
                playlistAddTaskStatusTxt.setVisibility(View.VISIBLE);
                playlistAddTaskProgress.setVisibility(View.GONE);
                playlistAddTaskCanceledTxt.setVisibility(View.GONE);
                playlistAddTaskErrorTxt.setVisibility(View.VISIBLE);

                playlistAddTaskCancelBtn.setVisibility(View.VISIBLE);
                playlistAddTaskRetryBtn.setVisibility(View.VISIBLE);
                playlistAddTaskDismissBtn.setVisibility(View.GONE);
                playlistAddTaskAddAnotherBtn.setVisibility(View.GONE);

                break;
            case PLAYLIST_ADD_TASK_OK:
                playlistView.setVisibility(View.GONE);
                playlistAddTaskProgressView.setVisibility(View.VISIBLE);

                playlistAddTaskEnqueuedTxt.setVisibility(View.GONE);
                playlistAddTaskStatusTxt.setVisibility(View.VISIBLE);
                playlistAddTaskProgress.setVisibility(View.GONE);
                playlistAddTaskCanceledTxt.setVisibility(View.GONE);
                playlistAddTaskErrorTxt.setVisibility(View.GONE);

                playlistAddTaskCancelBtn.setVisibility(View.GONE);
                playlistAddTaskRetryBtn.setVisibility(View.GONE);
                playlistAddTaskDismissBtn.setVisibility(View.VISIBLE);
                playlistAddTaskAddAnotherBtn.setVisibility(View.VISIBLE);

                break;
        }
    }

    private void updateVideoListBg(final String playlistUrl) {
        // значения по умолчанию
        playlistNameTxt.setText("");
        playlistThumbImg.setImageResource(R.drawable.ic_yashlang_thumb);
        playlistLoadErrorTxt.setText("");

        //
        loadedPlaylist = null;
        playlistLoadError = false;

        state = State.ITEMS_LIST_LOAD_PROGRESS;
        updateControlsVisibility();

        dbAndNetworkExecutor.execute(new Runnable() {
            @Override
            public void run() {
                // информация о канале
                PlaylistInfo _plInfo = null;
                Bitmap _plThumb = null;
                String _errMsg = "";
                try {
                    _plInfo = ContentLoader.getInstance().getPlaylistInfo(playlistUrl);

                    // иконка канала
                    _plThumb = ThumbManager.getInstance().loadPlaylistThumb(
                            AddPlaylistActivity.this, _plInfo);
                } catch (final Exception e) {
                    playlistLoadError = true;
                    _errMsg = e.getMessage()
                            + (e.getCause() != null ? "\n(" + e.getCause().getMessage() + ")" : "");
                }

                if (!playlistLoadError) {
                    // здесь loadedPlaylist будет не null даже в том случае, если произойдет какая-то
                    // ошибка при загрузке списка видео с 1й страницы, хотя информация о загруженном
                    // плейлисте (и кнопка добавления плейлиста) появится только после того,
                    // как будет загружена и первая страница списка
                    loadedPlaylist = _plInfo;
                    loadedPlaylist.setThumbBitmap(_plThumb);
                }

                final String errMsg = _errMsg;
                // сохраним значения здесь, чтобы значения не повредились к тому моменту,
                // когда к ним обратимся в потоке handler
                final boolean _playlistLoadError = playlistLoadError;
                final PlaylistInfo _loadedPlaylist = loadedPlaylist;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!_playlistLoadError) {
                            // информация о канале (не будет видна до завершения загрузки первой
                            // страницы списка)
                            // На null не проверяем, т.к. достаточно проверки флага playlistLoadError
                            playlistNameTxt.setText(_loadedPlaylist.getName());
                            playlistThumbImg.setImageBitmap(_loadedPlaylist.getThumbBitmap());

                            // загрузить список видео
                            setupVideoListAdapter(playlistUrl);
                        } else {
                            // ошибочка вышла
                            playlistLoadErrorTxt.setText(errMsg);
                            state = State.ITEMS_LIST_LOAD_ERROR;
                            updateControlsVisibility();
                        }
                    }
                });
            }
        });
    }

    private void setupVideoListAdapter(final String playlistUrl) {
        if (videoItemsLiveData != null) {
            videoItemsLiveData.removeObservers(this);
        }
        if (videoList.getAdapter() != null) {
            videoList.getAdapter().unregisterAdapterDataObserver(emptyListObserver);
        }

        final VideoItemPagedListAdapter adapter = new VideoItemPagedListAdapter(this,
                new OnListItemClickListener<VideoItem>() {
                    @Override
                    public void onItemClick(final View view, final int position, final VideoItem videoItem) {
                        final Intent intent = new Intent(AddPlaylistActivity.this, WatchVideoActivity.class);
                        intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ITEM_URL, videoItem.getItemUrl());
                        intent.putExtra(WatchVideoActivity.PARAM_SCROLL_TO_IN_RECOMMENDATIONS, position);
                        intent.putExtra(WatchVideoActivity.PARAM_RECOMMENDATIONS_MODE, RecommendationsProviderFactory.RecommendationsMode.PLAYLIST_URL);
                        intent.putExtra(RecommendationsProviderFactory.PARAM_PLAYLIST_URL, playlistUrl);
                        startActivity(intent);
                    }

                    @Override
                    public boolean onItemLongClick(final View view, final int position, final VideoItem videoItem) {
                        final PopupMenu popup = new PopupMenu(AddPlaylistActivity.this,
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
                                                VideoItemActions.actionCopyVideoName(AddPlaylistActivity.this, videoItem);
                                                break;
                                            }
                                            case R.id.action_copy_video_url: {
                                                VideoItemActions.actionCopyVideoUrl(AddPlaylistActivity.this, videoItem);
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
                }, null);
        // если список пустой, показываем специальный экранчик с кнопкой
        adapter.registerAdapterDataObserver(emptyListObserver);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(ConfigOptions.PAGED_LIST_PAGE_SIZE).build();
        // Pass in dependency
        final DataSource.Factory factory =
                new VideoItemOnlineDataSourceFactory(playlistUrl, new DataSourceListener() {
                    @Override
                    public void onLoadInitialError(final Exception e) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                playlistLoadError = true;
                                playlistLoadErrorTxt.setText(e.getMessage()
                                        + (e.getCause() != null ? "\n(" + e.getCause().getMessage() + ")" : ""));
                                // видимость элементов управления обновится в emptyListObserver
                            }
                        });
                    }

                    @Override
                    public void onLoadBeforeError(final Exception e) {

                    }

                    @Override
                    public void onLoadAfterError(final Exception e) {
                        // TODO: здесь можно показывать снизу отдельную панельку с ошибкой подгрузки
                        // в список новых элементов при промотке вниз уже после того,
                        // как список загрузился
                    }
                });

        videoItemsLiveData = new LivePagedListBuilder(factory, config).build();
        videoItemsLiveData.observe(this, new Observer<PagedList<VideoItem>>() {
            @Override
            public void onChanged(@Nullable PagedList<VideoItem> videos) {
                adapter.submitList(videos);
            }
        });

        videoList.setAdapter(adapter);
    }

    private void addLoadedPlaylistBg() {
        // разрешено добавлять только после того, как о плейлисте загружена предварительная
        // информация, т.е. объект loadedPlaylist будет не null
        //final String playlistUrl = playlistUrlInput.getText().toString();
        if (loadedPlaylist.getThumbBitmap() != null) {
            playlistAddTaskPlThumbImg.setImageBitmap(loadedPlaylist.getThumbBitmap());
        } else {
            playlistAddTaskPlThumbImg.setImageResource(R.drawable.ic_yashlang_thumb);
        }
        playlistAddTaskPlNameTxt.setText(loadedPlaylist.getName());
        playlistAddTaskPlUrlTxt.setText(PlaylistUrlUtil.cleanupUrl(loadedPlaylist.getUrl()));

        // обнулить состояние виджетов
        playlistAddTaskStatusTxt.setText("");
        playlistAddTaskErrorTxt.setText("");

        // показать панель статуса добавления плейлиста
        state = State.PLAYLIST_ADD_TASK_PROGRESS;
        updateControlsVisibility();

        addPlaylistTaskId = PlaylistsImportService.addPlaylist(this, loadedPlaylist.getUrl(), loadedPlaylist);
    }
}
