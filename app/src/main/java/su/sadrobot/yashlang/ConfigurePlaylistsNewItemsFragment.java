package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * ConfigurePlaylistNewItemsFragment.java is part of YaShlang.
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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

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

import java.util.List;

import su.sadrobot.yashlang.controller.ContentLoader;
import su.sadrobot.yashlang.controller.TaskController;
import su.sadrobot.yashlang.controller.VideoThumbManager;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.util.PlaylistUrlUtil;
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
    private View newItemsAddProgressView;
    private ImageView newItemsAddPlThumbImg;
    private TextView newItemsAddPlNameTxt;
    private TextView newItemsAddPlUrlTxt;
    private TextView newItemsAddStatusTxt;
    private ProgressBar newItemsAddProgress;

    private View newItemsAddErrorView;
    private TextView newItemsAddErrorTxt;
    private Button newItemsAddRetryBtn;
    private Button newItemsAddSkipBtn;
    private Button newItemsAddCancelBtn;

    private View newItemsAddDoneView;
    private TextView newItemsAddDoneStatusTxt;
    private Button newItemsAddDoneBtn;

    private final Handler handler = new Handler();


    private LiveData<PagedList<VideoItem>> videoItemsLiveData;

    private enum State {
        NEW_ITEMS_LIST_EMPTY, NEW_ITEMS_LIST_LOAD_PROGRESS, NEW_ITEMS_LIST_LOAD_ERROR, NEW_ITEMS_LIST_LOADED,
        PLAYLIST_UPDATE_PROGRESS, PLAYLIST_UPDATE_ERROR, PLAYLIST_UPDATE_OK,
        PLAYLISTS_UPDATE_ALL_OK
    }

    private State state = State.NEW_ITEMS_LIST_EMPTY;
    private boolean checkError = false;

    private TaskController taskController;
    private int plToUpdateStartIndex = 0;
    private int addedThisTime = 0;


    private final RecyclerView.AdapterDataObserver emptyListObserver = new RecyclerView.AdapterDataObserver() {
        // https://stackoverflow.com/questions/47417645/empty-view-on-a-recyclerview
        // https://stackoverflow.com/questions/27414173/equivalent-of-listview-setemptyview-in-recyclerview
        // https://gist.github.com/sheharyarn/5602930ad84fa64c30a29ab18eb69c6e
        private void checkIfEmpty() {
            // пришли какие-то данные (или сообщение, что их нет) - в любом случае прячем прогресс
            // плюс, сюда же попадаем в случае ошибки

            if(state != State.PLAYLIST_UPDATE_PROGRESS && state != State.PLAYLIST_UPDATE_ERROR &&
                    state != State.PLAYLIST_UPDATE_OK && state != State.PLAYLISTS_UPDATE_ALL_OK) {
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
        newItemsAddProgressView = view.findViewById(R.id.playlist_new_items_add_progress_view);

        newItemsAddPlThumbImg = view.findViewById(R.id.playlist_new_items_add_pl_thumb_img);
        newItemsAddPlNameTxt = view.findViewById(R.id.playlist_new_items_add_pl_name_txt);
        newItemsAddPlUrlTxt = view.findViewById(R.id.playlist_new_items_add_pl_url_txt);
        newItemsAddStatusTxt = view.findViewById(R.id.playlist_new_items_add_status_txt);
        newItemsAddProgress = view.findViewById(R.id.playlist_new_items_add_progress);

        newItemsAddErrorView = view.findViewById(R.id.playlist_new_items_add_error_view);
        newItemsAddErrorTxt = view.findViewById(R.id.playlist_new_items_add_error_txt);
        newItemsAddRetryBtn = view.findViewById(R.id.playlist_new_items_add_retry_btn);
        newItemsAddSkipBtn = view.findViewById(R.id.playlist_new_items_add_skip_btn);
        newItemsAddCancelBtn = view.findViewById(R.id.playlist_new_items_add_cancel_btn);

        // Всё добавлено
        newItemsAddDoneView = view.findViewById(R.id.playlist_new_items_add_done_view);
        newItemsAddDoneStatusTxt = view.findViewById(R.id.playlist_new_items_add_done_status_txt);
        newItemsAddDoneBtn = view.findViewById(R.id.playlist_new_items_add_done_btn);

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

        newItemsAddRetryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewItemsBg();
            }
        });

        newItemsAddSkipBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // пропустить неудачный плейлист
                plToUpdateStartIndex++;
                addNewItemsBg();
            }
        });

        newItemsAddCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                plToUpdateStartIndex = 0;
                addedThisTime = 0;
                state = State.NEW_ITEMS_LIST_EMPTY;
                updateControlsVisibility();
                //updateVideoListBg();
            }
        });

        newItemsAddDoneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                state = State.NEW_ITEMS_LIST_EMPTY;
                updateControlsVisibility();
                //updateVideoListBg();
            }
        });

        // здесь можно было бы сразу грузить список новых видео, но, возможно,
        // будет не очень правильно грузить сеть проверкой всех плейлистов каждый
        // раз, когда мы заходим в настройки - есть и кнопка
        //updateVideoListBg();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(taskController != null) {
            taskController.cancel();
        }
    }

    private void updateControlsVisibility() {
        switch (state){
            case NEW_ITEMS_LIST_EMPTY:
                playlistItemsView.setVisibility(View.VISIBLE);
                newItemsAddProgressView.setVisibility(View.GONE);
                newItemsAddDoneView.setVisibility(View.GONE);

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
                newItemsAddProgressView.setVisibility(View.GONE);
                newItemsAddDoneView.setVisibility(View.GONE);

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
                newItemsAddProgressView.setVisibility(View.GONE);
                newItemsAddDoneView.setVisibility(View.GONE);

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
                newItemsAddProgressView.setVisibility(View.GONE);
                newItemsAddDoneView.setVisibility(View.GONE);

                emptyView.setVisibility(View.GONE);
                newItemsView.setVisibility(View.VISIBLE);

                break;
            case PLAYLIST_UPDATE_PROGRESS:
                playlistItemsView.setVisibility(View.GONE);
                newItemsAddProgressView.setVisibility(View.VISIBLE);
                newItemsAddDoneView.setVisibility(View.GONE);

                newItemsAddProgress.setVisibility(View.VISIBLE);
                newItemsAddErrorView.setVisibility(View.GONE);

                break;
            case PLAYLIST_UPDATE_ERROR:
                playlistItemsView.setVisibility(View.GONE);
                newItemsAddProgressView.setVisibility(View.VISIBLE);
                newItemsAddDoneView.setVisibility(View.GONE);

                newItemsAddProgress.setVisibility(View.GONE);
                newItemsAddErrorView.setVisibility(View.VISIBLE);

                break;
            case PLAYLIST_UPDATE_OK:
                playlistItemsView.setVisibility(View.GONE);
                newItemsAddProgressView.setVisibility(View.VISIBLE);
                newItemsAddDoneView.setVisibility(View.GONE);

                newItemsAddProgress.setVisibility(View.GONE);
                newItemsAddErrorView.setVisibility(View.GONE);

                break;
            case PLAYLISTS_UPDATE_ALL_OK:
                playlistItemsView.setVisibility(View.GONE);
                newItemsAddProgressView.setVisibility(View.GONE);
                newItemsAddDoneView.setVisibility(View.VISIBLE);

                break;
        }
    }

    /**
     * Update video list in background
     */
    private void updateVideoListBg() {
        // прогресс будет видно до тех пор, пока в адаптер не придут какие-то данные или не
        // произойдет ошибка
        checkError = false;
        checkErrorTxt.setText("");
        state = State.NEW_ITEMS_LIST_LOAD_PROGRESS;
        updateControlsVisibility();

        new Thread(new Runnable() {
            @Override
            public void run() {
                // информация из базы данных - загрузится быстро и без интернета
                final List<Long> plIds;
                if(checkAllRadio.isChecked()) {
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
        }).start();
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
                        intent.putExtra(WatchVideoActivity.PARAM_RECOMMENDATIONS_MODE, WatchVideoActivity.RecommendationsMode.ALL_NEW);
                        startActivity(intent);
                    }

                    @Override
                    public boolean onItemLongClick(final View view, final int position, final VideoItem videoItem) {
                        final PopupMenu popup = new PopupMenu(ConfigurePlaylistsNewItemsFragment.this.getContext(),
                                view.findViewById(R.id.video_name_txt));
                        popup.getMenuInflater().inflate(R.menu.video_item_actions, popup.getMenu());
                        popup.getMenu().removeItem(R.id.action_play_in_playlist);
                        popup.getMenu().removeItem(R.id.action_play_in_playlist_shuffle);
                        popup.getMenu().removeItem(R.id.action_blacklist);
                        popup.getMenu().removeItem(R.id.action_copy_playlist_name);
                        popup.getMenu().removeItem(R.id.action_copy_playlist_url);
                        popup.setOnMenuItemClickListener(
                                new PopupMenu.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(final MenuItem item) {
                                        switch (item.getItemId()) {
                                            case R.id.action_copy_video_name: {
                                                final ClipboardManager clipboard = (ClipboardManager) ConfigurePlaylistsNewItemsFragment.this.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                                final ClipData clip = ClipData.newPlainText(videoItem.getName(), videoItem.getName());
                                                clipboard.setPrimaryClip(clip);

                                                Toast.makeText(ConfigurePlaylistsNewItemsFragment.this.getContext(),
                                                        getString(R.string.copied) + ": " + videoItem.getName(),
                                                        Toast.LENGTH_LONG).show();
                                                break;
                                            }
                                            case R.id.action_copy_video_url: {
                                                final String vidUrl = videoItem.getItemUrl();
                                                final ClipboardManager clipboard = (ClipboardManager) ConfigurePlaylistsNewItemsFragment.this.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                                final ClipData clip = ClipData.newPlainText(vidUrl, vidUrl);
                                                clipboard.setPrimaryClip(clip);

                                                Toast.makeText(ConfigurePlaylistsNewItemsFragment.this.getContext(),
                                                        getString(R.string.copied) + ": " + vidUrl,
                                                        Toast.LENGTH_LONG).show();
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
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(20).build();

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
        // канал или плейлист
        taskController = new TaskController();
        taskController.setTaskListener(new TaskController.TaskAdapter() {
            @Override
            public void onStart() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        newItemsAddStatusTxt.setText(taskController.getStatusMsg());
                        state = State.PLAYLIST_UPDATE_PROGRESS;
                        updateControlsVisibility();
                    }
                });
            }

            @Override
            public void onFinish() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        newItemsAddStatusTxt.setText(taskController.getStatusMsg());
                        if(taskController.getException() == null) {
                            state = State.PLAYLIST_UPDATE_OK;
                        } else {
                            state = State.PLAYLIST_UPDATE_ERROR;
                        }
                        updateControlsVisibility();
                    }
                });
            }

            @Override
            public void onStatusMsgChange(final String status, final Exception e) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        newItemsAddStatusTxt.setText(status);
                        if (e != null) {
                            newItemsAddErrorTxt.setText(e.getMessage()
                                    + (e.getCause() != null ? "\n(" + e.getCause().getMessage() + ")" : ""));
                            state = State.PLAYLIST_UPDATE_ERROR;
                            updateControlsVisibility();
                        }
                    }
                });
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<PlaylistInfo> allPlaylists;
                if(checkAllRadio.isChecked()) {
                    allPlaylists = VideoDatabase.getDbInstance(getContext()).playlistInfoDao().getAll();
                } else { // checkOnlyEnabledRadio.isChecked()
                    allPlaylists = VideoDatabase.getDbInstance(getContext()).playlistInfoDao().getEnabled();
                }

                boolean allOk = true;

                // начинаем с индекса plToUpdateStartIndex (например, если продолжаем после ошибки)
                for (; plToUpdateStartIndex < allPlaylists.size(); plToUpdateStartIndex++) {
                    if(taskController.isCanceled()) {
                        allOk = false;
                        break;
                    }

                    final PlaylistInfo plInfo = allPlaylists.get(plToUpdateStartIndex);
                    // подгрузим иконку плейлиста (хотя скорее всего она уже в кеше)
                    try {
                        // иконка канала
                        if (plInfo.getThumbBitmap() == null) {
                            final Bitmap _plThumb = VideoThumbManager.getInstance().loadPlaylistThumb(
                                    ConfigurePlaylistsNewItemsFragment.this.getContext(), plInfo.getThumbUrl());
                            plInfo.setThumbBitmap(_plThumb);
                        }
                    } catch (final Exception e) {
                        // если не загрузилась - плохой признак скорее, но здесь ничего страшного,
                        // игнорируем
                    }

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            newItemsAddPlThumbImg.setImageBitmap(plInfo.getThumbBitmap());
                            newItemsAddPlNameTxt.setText(plInfo.getName());
                            newItemsAddPlUrlTxt.setText(PlaylistUrlUtil.cleanupUrl(plInfo.getUrl()));
                        }
                    });

                    // добавляем новые элементы
                    int addedCount = ContentLoader.getInstance().addPlaylistNewItems(
                        ConfigurePlaylistsNewItemsFragment.this.getContext(),
                            plInfo.getId(), plInfo.getUrl(), taskController);

                    if (addedCount == -1) {
                        // Обновление плейлиста завершилось с ошибкой - завершаему эту попытку,
                        // экран не закрываем (в колбэк таск-контроллера еще раньше должно прийти
                        // событие с ошибкой, он покажет экран ошибки с сообщением и предложениями
                        // попробовать еще, пропустить или завершить добавление)
                        allOk = false;
                        break;
                    } else {
                        addedThisTime += addedCount;
                    }

                    try {
                        // сделаем небольшую паузу между двумя плейлистами, чтобы успеть разглядеть
                        // сообщение о том, что плейлист добавлен, например.
                        // (пользователь не будет часто добавлять плейлисты в этом диалоге, поэтому
                        // здесь это ок)
                        Thread.sleep(ConfigOptions.UPDATE_PLAYLISTS_DELAY_MS);
                    } catch (final InterruptedException e) {
                    }
                }

                if (allOk) {
                    // все добавили
                    plToUpdateStartIndex = 0;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if(taskController.getException() == null) {
                                //updateVideoListBg();
                                state = State.PLAYLISTS_UPDATE_ALL_OK;

                                newItemsAddDoneStatusTxt.setText(
                                        getString(R.string.added_n_items).replace("%s", String.valueOf(addedThisTime)));

                                // обнулим для след раза в эту же сессию
                                addedThisTime = 0;

                                updateControlsVisibility();
                            }
                        }
                    });
                }
            }
        }).start();
    }
}
