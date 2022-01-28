package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * ConfigurePlaylistFragment.java is part of YaShlang.
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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
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

import su.sadrobot.yashlang.controller.VideoThumbManager;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.util.PlaylistUrlUtil;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.OnListItemSwitchListener;
import su.sadrobot.yashlang.view.VideoItemPagedListAdapter;

/**
 *
 */
public class ConfigurePlaylistFragment extends Fragment {
    // https://developer.android.com/guide/components/fragments
    // https://developer.android.com/guide/navigation/navigation-swipe-view

    public static final String PARAM_PLAYLIST_ID = "PARAM_PLAYLIST_ID";

    private ImageView playlistThumbImg;
    private TextView playlistNameTxt;
    private TextView playlistUrlTxt;
    private TextView playlistSizeTxt;

    private View emptyView;

    private EditText filterPlaylistInput;
    private RecyclerView videoList;

    private Handler handler = new Handler();

    private LiveData<PagedList<VideoItem>> videoItemsLiveData;

    private long playlistId = PlaylistInfo.ID_NONE;

    private RecyclerView.AdapterDataObserver emptyListObserver = new RecyclerView.AdapterDataObserver() {
        // https://stackoverflow.com/questions/47417645/empty-view-on-a-recyclerview
        // https://stackoverflow.com/questions/27414173/equivalent-of-listview-setemptyview-in-recyclerview
        // https://gist.github.com/sheharyarn/5602930ad84fa64c30a29ab18eb69c6e
        private void checkIfEmpty() {
            updateControlsVisibility();
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

        playlistId = super.getActivity().getIntent().getLongExtra(PARAM_PLAYLIST_ID, PlaylistInfo.ID_NONE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_view_playlist, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        playlistThumbImg = view.findViewById(R.id.playlist_thumb_img);
        playlistNameTxt = view.findViewById(R.id.playlist_name_txt);
        playlistUrlTxt = view.findViewById(R.id.playlist_url_txt);
        playlistSizeTxt = view.findViewById(R.id.playlist_size_txt);

        filterPlaylistInput = view.findViewById(R.id.filter_playlist_input);
        emptyView = view.findViewById(R.id.empty_view);
        videoList = view.findViewById(R.id.video_list);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        videoList.setLayoutManager(linearLayoutManager);

        filterPlaylistInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event) {
                setupVideoListAdapter(playlistId, v.getText().toString().trim());

                return false;
            }
        });

        filterPlaylistInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                setupVideoListAdapter(playlistId, s.toString().trim());
            }
        });

        // и здесь же загрузим список видео (если делать это в onResume,
        // то список будет каждый раз сбрасываться при потере фокуса активити)
        updateVideoListBg();
    }

    private void updateControlsVisibility() {
        // считаем, что плейлист пустой только если в поле фильтра ничего не введено
        final boolean listIsEmpty = filterPlaylistInput.getText().length() == 0 &&
                (videoList.getAdapter() == null || videoList.getAdapter().getItemCount() == 0);

        if(listIsEmpty) {
            emptyView.setVisibility(View.VISIBLE);
            filterPlaylistInput.setVisibility(View.GONE);
            videoList.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            filterPlaylistInput.setVisibility(View.VISIBLE);
            videoList.setVisibility(View.VISIBLE);
        }
    }

    public void updateVideoListBg() {
        this.updateVideoListBg(playlistId);
    }

    /**
     * Update video list in background
     *
     * @param plId
     */
    private void updateVideoListBg(final long plId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // информация из базы данных - загрузится быстро и без интернета
                final VideoDatabase videodb = VideoDatabase.getDbInstance(getContext());
                final PlaylistInfo plInfo = videodb.playlistInfoDao().getById(plId);
                final int plVideosCount = videodb.videoItemDao().countAllVideos(plId);

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        playlistNameTxt.setText(plInfo.getName());
                        playlistUrlTxt.setText(PlaylistUrlUtil.cleanupUrl(plInfo.getUrl()));
                        playlistSizeTxt.setText(" (" + plVideosCount + ")");
                    }
                });

                // иконка плейлиста - может грузиться подольше, без интернета вообще не загрузится
                try {
                    final Bitmap plThumb = VideoThumbManager.getInstance().loadPlaylistThumb(
                            ConfigurePlaylistFragment.this.getContext(), plInfo.getThumbUrl());
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


        setupVideoListAdapter(plId, filterPlaylistInput.getText().toString().trim());
    }

    private void setupVideoListAdapter(final long plId, final String sstr) {
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
                        final Intent intent = new Intent(ConfigurePlaylistFragment.this.getContext(), WatchVideoActivity.class);
                        intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ID, videoItem.getId());
                        intent.putExtra(WatchVideoActivity.PARAM_RECOMMENDATIONS_MODE, WatchVideoActivity.RecommendationsMode.PLAYLIST_ID);
                        intent.putExtra(WatchVideoActivity.PARAM_PLAYLIST_ID, playlistId);
                        intent.putExtra(WatchVideoActivity.PARAM_SHOW_ALL, true);
                        startActivity(intent);
                    }

                    @Override
                    public boolean onItemLongClick(final View view, final int position, final VideoItem videoItem) {
                        final PopupMenu popup = new PopupMenu(ConfigurePlaylistFragment.this.getContext(),
                                view.findViewById(R.id.video_name_txt));
                        popup.getMenuInflater().inflate(R.menu.video_item_actions, popup.getMenu());
                        popup.getMenu().removeItem(R.id.action_play_in_playlist);
                        popup.getMenu().removeItem(R.id.action_play_in_playlist_shuffle);
                        popup.getMenu().removeItem(R.id.action_blacklist);
                        popup.setOnMenuItemClickListener(
                                new PopupMenu.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(final MenuItem item) {
                                        switch (item.getItemId()) {
                                            case R.id.action_copy_video_name: {
                                                final ClipboardManager clipboard = (ClipboardManager) ConfigurePlaylistFragment.this.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                                final ClipData clip = ClipData.newPlainText(videoItem.getName(), videoItem.getName());
                                                clipboard.setPrimaryClip(clip);

                                                Toast.makeText(ConfigurePlaylistFragment.this.getContext(),
                                                        getString(R.string.copied) + ": " + videoItem.getName(),
                                                        Toast.LENGTH_LONG).show();
                                                break;
                                            }
                                            case R.id.action_copy_video_url: {
                                                final String vidUrl = videoItem.getItemUrl();
                                                final ClipboardManager clipboard = (ClipboardManager) ConfigurePlaylistFragment.this.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                                final ClipData clip = ClipData.newPlainText(vidUrl, vidUrl);
                                                clipboard.setPrimaryClip(clip);

                                                Toast.makeText(ConfigurePlaylistFragment.this.getContext(),
                                                        getString(R.string.copied) + ": " + vidUrl,
                                                        Toast.LENGTH_LONG).show();
                                                break;
                                            }
                                            case R.id.action_copy_playlist_name:
                                                if (videoItem != null && videoItem.getPlaylistId() != PlaylistInfo.ID_NONE) {
                                                    new Thread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            final PlaylistInfo plInfo = VideoDatabase.getDbInstance(
                                                                    getContext()).playlistInfoDao().getById(videoItem.getPlaylistId());
                                                            if(plInfo != null) {
                                                                handler.post(new Runnable() {
                                                                    @Override
                                                                    public void run() {
                                                                        final ClipboardManager clipboard = (ClipboardManager) ConfigurePlaylistFragment.this.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                                                        final ClipData clip = ClipData.newPlainText(plInfo.getName(), plInfo.getName());
                                                                        clipboard.setPrimaryClip(clip);

                                                                        Toast.makeText(ConfigurePlaylistFragment.this.getContext(),
                                                                                getString(R.string.copied) + ": " + plInfo.getName(),
                                                                                Toast.LENGTH_LONG).show();
                                                                    }
                                                                });
                                                            }
                                                        }
                                                    }).start();
                                                } else if(videoItem != null && videoItem.getPlaylistId() == PlaylistInfo.ID_NONE) {
                                                    Toast.makeText(ConfigurePlaylistFragment.this.getContext(), getString(R.string.err_playlist_not_defined),
                                                            Toast.LENGTH_LONG).show();
                                                }
                                                break;
                                            case R.id.action_copy_playlist_url:
                                                if (videoItem != null && videoItem.getPlaylistId() != PlaylistInfo.ID_NONE) {
                                                    new Thread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            final PlaylistInfo plInfo = VideoDatabase.getDbInstance(
                                                                    getContext()).playlistInfoDao().getById(videoItem.getPlaylistId());
                                                            if(plInfo != null) {
                                                                handler.post(new Runnable() {
                                                                    @Override
                                                                    public void run() {
                                                                        final ClipboardManager clipboard = (ClipboardManager) ConfigurePlaylistFragment.this.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                                                        final ClipData clip = ClipData.newPlainText(plInfo.getUrl(), plInfo.getUrl());
                                                                        clipboard.setPrimaryClip(clip);

                                                                        Toast.makeText(ConfigurePlaylistFragment.this.getContext(),
                                                                                getString(R.string.copied) + ": " + plInfo.getUrl(),
                                                                                Toast.LENGTH_LONG).show();
                                                                    }
                                                                });
                                                            }
                                                        }
                                                    }).start();
                                                } else if(videoItem != null && videoItem.getPlaylistId() == PlaylistInfo.ID_NONE) {
                                                    Toast.makeText(ConfigurePlaylistFragment.this.getContext(), getString(R.string.err_playlist_not_defined),
                                                            Toast.LENGTH_LONG).show();
                                                }
                                                break;
                                        }
                                        return true;
                                    }
                                }
                        );
                        popup.show();
                        return true;
                    }
                },
                new OnListItemSwitchListener<VideoItem>() {
                    @Override
                    public void onItemCheckedChanged(final CompoundButton buttonView, final int position, final VideoItem item, final boolean isChecked) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                VideoDatabase.getDbInstance(getContext()).
                                        videoItemDao().setBlacklisted(item.getId(), !isChecked);
                            }
                        }).start();
                    }

                    @Override
                    public boolean isItemChecked(final VideoItem item) {
                        return !item.isBlacklisted();
                    }

                    @Override
                    public boolean showItemCheckbox(final VideoItem item) {
                        return true;
                    }
                });
        // если список пустой, показываем специальный экранчик с сообщением
        adapter.registerAdapterDataObserver(emptyListObserver);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(20).build();

        final DataSource.Factory factory;
        if (sstr != null && !sstr.isEmpty()) {
            factory = VideoDatabase.getDbInstance(getContext()).videoItemDao().getByPlaylistAllDs(plId, sstr);
        } else {
            factory = VideoDatabase.getDbInstance(getContext()).videoItemDao().getByPlaylistAllDs(plId);
        }

        videoItemsLiveData = new LivePagedListBuilder(factory, config).build();

        videoItemsLiveData.observe(this, new Observer<PagedList<VideoItem>>() {
            @Override
            public void onChanged(@Nullable PagedList<VideoItem> videos) {
                adapter.submitList(videos);
            }
        });

        videoList.setAdapter(adapter);
    }
}
