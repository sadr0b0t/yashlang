package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * ViewPlaylistNewItemsFragment.java is part of YaShlang.
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import su.sadrobot.yashlang.controller.ContentLoader;
import su.sadrobot.yashlang.controller.VideoThumbManager;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.util.PlaylistUrlUtil;
import su.sadrobot.yashlang.view.DataSourceListener;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.VideoItemOnlyNewOnlineDataSourceFactory;
import su.sadrobot.yashlang.view.VideoItemPagedListAdapter;

/**
 *
 */
public class ViewPlaylistNewItemsFragment extends Fragment {
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
    private Button checkNewItemsBtn;
    private TextView checkErrorTxt;
    private ProgressBar checkProgress;

    private View newItemsView;
    private Button addNewItemsBtn;
    private RecyclerView videoList;

    // Добавление элементов
    private View newItemsAddProgressView;
    private TextView newItemsAddStatusTxt;
    private ProgressBar newItemsAddProgress;

    private View newItemsAddErrorView;
    private TextView newItemsAddErrorTxt;
    private Button newItemsAddRetryBtn;
    private Button newItemsAddCancelBtn;

    private View newItemsAddDoneView;
    private Button newItemsAddDoneBtn;

    private Handler handler = new Handler();

    private LiveData<PagedList<VideoItem>> videoItemsLiveData;
    private VideoDatabase videodb;

    private PlaylistUpdateListener playlistUpdateListener;

    private long playlistId = -1;
    private PlaylistInfo plInfo;

    private RecyclerView.AdapterDataObserver emptyListObserver = new RecyclerView.AdapterDataObserver() {
        // https://stackoverflow.com/questions/47417645/empty-view-on-a-recyclerview
        // https://stackoverflow.com/questions/27414173/equivalent-of-listview-setemptyview-in-recyclerview
        // https://gist.github.com/sheharyarn/5602930ad84fa64c30a29ab18eb69c6e
        private void checkIfEmpty() {
            // пришли какие-то данные (или сообщение, что их нет) - в любом случае прячем прогресс
            checkProgress.setVisibility(View.INVISIBLE);
            checkNewItemsBtn.setEnabled(true);

            //
            final boolean listIsEmpty = videoList.getAdapter() == null || videoList.getAdapter().getItemCount() == 0;
            emptyView.setVisibility(listIsEmpty ? View.VISIBLE : View.GONE);
            newItemsView.setVisibility(listIsEmpty ? View.GONE : View.VISIBLE);
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


    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        playlistId = super.getActivity().getIntent().getLongExtra(PARAM_PLAYLIST_ID, -1);

        // подключимся к базе один раз при создании активити,
        // закрывать подключение в onDestroy
        videodb = VideoDatabase.getDb(getContext());
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
        checkNewItemsBtn = view.findViewById(R.id.check_new_items_btn);
        checkErrorTxt = view.findViewById(R.id.check_error_txt);
        checkProgress = view.findViewById(R.id.check_progress);

        newItemsView = view.findViewById(R.id.playlist_new_items_view);
        addNewItemsBtn = view.findViewById(R.id.add_new_items_btn);
        videoList = view.findViewById(R.id.video_list);

        // Операции и прогресс добавления
        newItemsAddProgressView = view.findViewById(R.id.playlist_new_items_add_progress_view);
        newItemsAddStatusTxt = view.findViewById(R.id.playlist_new_items_add_status_txt);
        newItemsAddProgress = view.findViewById(R.id.playlist_new_items_add_progress);

        newItemsAddErrorView = view.findViewById(R.id.playlist_new_items_add_error_view);
        newItemsAddErrorTxt = view.findViewById(R.id.playlist_new_items_add_error_txt);
        newItemsAddRetryBtn = view.findViewById(R.id.playlist_new_items_add_retry_btn);
        newItemsAddCancelBtn = view.findViewById(R.id.playlist_new_items_add_cancel_btn);

        newItemsAddDoneView = view.findViewById(R.id.playlist_new_items_add_done_view);
        newItemsAddDoneBtn = view.findViewById(R.id.playlist_new_items_add_done_btn);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        videoList.setLayoutManager(linearLayoutManager);


        checkNewItemsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateVideoListBg(playlistId);
            }
        });

        addNewItemsBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewItems();
            }
        });

        newItemsAddRetryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewItems();
            }
        });

        newItemsAddCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playlistItemsView.setVisibility(View.VISIBLE);
                newItemsAddProgressView.setVisibility(View.GONE);
            }
        });

        newItemsAddDoneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playlistItemsView.setVisibility(View.VISIBLE);
                newItemsAddProgressView.setVisibility(View.GONE);
            }
        });

        // и здесь же загрузим список видео (если делать это в onResume,
        // то список будет каждый раз сбрасываться при потере фокуса активити)
        updateVideoListBg(playlistId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (videodb != null) {
            videodb.close();
        }
    }

    public void setPlaylistUpdateListener(PlaylistUpdateListener playlistUpdateListener) {
        this.playlistUpdateListener = playlistUpdateListener;
    }

    /**
     * Update video list in background
     *
     * @param plId
     */
    private void updateVideoListBg(final long plId) {
        // прогресс будет видно до тех пор, пока в адаптер не придут какие-то данные
        checkProgress.setVisibility(View.VISIBLE);
        checkErrorTxt.setVisibility(View.GONE);
        checkNewItemsBtn.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                // информация из базы данных - загрузится быстро и без интернета
                plInfo = videodb.playlistInfoDao().getById(plId);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        playlistNameTxt.setText(plInfo.getName());
                        playlistUrlTxt.setText(PlaylistUrlUtil.cleanupUrl(plInfo.getUrl()));

                        setupVideoListAdapter(plId, plInfo.getUrl());
                    }
                });

                // иконка плейлиста - может грузиться подольше, без интернета вообще не загрузится
                try {
                    final Bitmap plThumb = VideoThumbManager.getInstance().loadPlaylistThumb(
                            ViewPlaylistNewItemsFragment.this.getContext(), plInfo.getThumbUrl());
                    plInfo.setThumbBitmap(plThumb);

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            playlistThumbImg.setImageBitmap(plInfo.getThumbBitmap());
                        }
                    });
                } catch (final Exception e) {
                }
            }
        }).start();
    }

    private void setupVideoListAdapter(final long plId, final String plUrl) {
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
                        final Intent intent = new Intent(ViewPlaylistNewItemsFragment.this.getContext(), WatchVideoActivity.class);
                        intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ITEM_URL, videoItem.getItemUrl());
                        intent.putExtra(WatchVideoActivity.PARAM_PLAYLIST_ID, playlistId);
                        startActivity(intent);
                    }

                    @Override
                    public boolean onItemLongClick(final View view, final int position, final VideoItem videoItem) {
                        final PopupMenu popup = new PopupMenu(ViewPlaylistNewItemsFragment.this.getContext(),
                                view.findViewById(R.id.video_name_txt));
                        popup.getMenuInflater().inflate(R.menu.video_actions, popup.getMenu());
                        popup.getMenu().removeItem(R.id.action_blacklist);
                        popup.getMenu().removeItem(R.id.action_copy_playlist_name);
                        popup.getMenu().removeItem(R.id.action_copy_playlist_url);
                        popup.setOnMenuItemClickListener(
                                new PopupMenu.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(final MenuItem item) {
                                        switch (item.getItemId()) {
                                            case R.id.action_copy_video_name: {
                                                final ClipboardManager clipboard = (ClipboardManager) ViewPlaylistNewItemsFragment.this.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                                final ClipData clip = ClipData.newPlainText(videoItem.getName(), videoItem.getName());
                                                clipboard.setPrimaryClip(clip);

                                                Toast.makeText(ViewPlaylistNewItemsFragment.this.getContext(),
                                                        getString(R.string.copied) + ": " + videoItem.getName(),
                                                        Toast.LENGTH_LONG).show();
                                                break;
                                            }
                                            case R.id.action_copy_video_url: {
                                                final String vidUrl = videoItem.getItemUrl();
                                                final ClipboardManager clipboard = (ClipboardManager) ViewPlaylistNewItemsFragment.this.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                                final ClipData clip = ClipData.newPlainText(vidUrl, vidUrl);
                                                clipboard.setPrimaryClip(clip);

                                                Toast.makeText(ViewPlaylistNewItemsFragment.this.getContext(),
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
                new VideoItemOnlyNewOnlineDataSourceFactory(this.getContext(), plUrl, plId, false,
                        new DataSourceListener() {
                            @Override
                            public void onLoadInitialError(final Exception e) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        checkErrorTxt.setText(e.getMessage());
                                        checkErrorTxt.setVisibility(View.VISIBLE);
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

        videoItemsLiveData.observe(this, new Observer<PagedList<VideoItem>>() {
            @Override
            public void onChanged(@Nullable PagedList<VideoItem> videos) {
                adapter.submitList(videos);
            }
        });

        videoList.setAdapter(adapter);
    }

    private void addNewItems() {
        final String playlistUrl = plInfo.getUrl();
        // канал или плейлист
        final ContentLoader.TaskController taskController = new ContentLoader.TaskController();
        taskController.setTaskListener(new ContentLoader.TaskListener() {
            @Override
            public void onStart() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        playlistItemsView.setVisibility(View.GONE);
                        newItemsAddProgressView.setVisibility(View.VISIBLE);

                        newItemsAddProgress.setVisibility(View.VISIBLE);
                        newItemsAddErrorView.setVisibility(View.GONE);
                        newItemsAddDoneView.setVisibility(View.GONE);

                        newItemsAddStatusTxt.setText(taskController.getStatusMsg());
                    }
                });
            }

            @Override
            public void onFinish() {
                playlistUpdateListener.onPlaylistUpdated();

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if(taskController.getException() == null) {
                            updateVideoListBg(playlistId);

                            newItemsAddProgress.setVisibility(View.GONE);
                            newItemsAddErrorView.setVisibility(View.GONE);
                            newItemsAddDoneView.setVisibility(View.VISIBLE);

                            newItemsAddStatusTxt.setText(taskController.getStatusMsg());
                        }
                    }
                });
            }

            @Override
            public void onStatusChange(final String status, final Exception e) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        newItemsAddStatusTxt.setText(status);
                        if (e != null) {
                            newItemsAddProgress.setVisibility(View.GONE);
                            newItemsAddErrorView.setVisibility(View.VISIBLE);
                            newItemsAddDoneView.setVisibility(View.GONE);

                            newItemsAddErrorTxt.setText(e.getMessage());
                        }
                    }
                });
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                ContentLoader.getInstance().addYtPlaylistNewItems(
                        ViewPlaylistNewItemsFragment.this.getContext(),
                        playlistId, playlistUrl, taskController);
            }
        }).start();
    }
}
