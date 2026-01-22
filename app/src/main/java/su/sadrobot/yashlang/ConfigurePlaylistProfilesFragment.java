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

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import su.sadrobot.yashlang.controller.ThumbManager;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.Profile;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.util.PlaylistUrlUtil;
import su.sadrobot.yashlang.view.ListItemSwitchController;
import su.sadrobot.yashlang.view.ProfileArrayAdapter;

/**
 *
 */
public class ConfigurePlaylistProfilesFragment extends Fragment {
    // https://developer.android.com/guide/components/fragments
    // https://developer.android.com/guide/navigation/navigation-swipe-view

    public static final String PARAM_PLAYLIST_ID = "PARAM_PLAYLIST_ID";

    private ImageView playlistThumbImg;
    private TextView playlistNameTxt;
    private TextView playlistUrlTxt;
    private TextView playlistSizeTxt;

    private View emptyView;

    private Button configureProfilesBtn;
    private RecyclerView profileList;

    private final Handler handler = new Handler();
    // достаточно одного фонового потока
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    // потоки для сетевых операций (могут включать обращения к базе данных) - при плохой
    // сетевая операция может затупить и не заметно задерживать время выполнения других фоновых
    // операций, которые не связаны с сетью
    private final ExecutorService dbAndNetworkExecutor = Executors.newSingleThreadExecutor();

    private long playlistId = PlaylistInfo.ID_NONE;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playlistId = super.getActivity().getIntent().getLongExtra(PARAM_PLAYLIST_ID, PlaylistInfo.ID_NONE);
    }

    @Override
    public void onResume() {
        super.onResume();

        updatePlaylistInfoBg();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_view_playlist_profiles, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        playlistThumbImg = view.findViewById(R.id.playlist_thumb_img);
        playlistNameTxt = view.findViewById(R.id.playlist_name_txt);
        playlistUrlTxt = view.findViewById(R.id.playlist_url_txt);
        playlistSizeTxt = view.findViewById(R.id.playlist_size_txt);

        emptyView = view.findViewById(R.id.empty_view);
        configureProfilesBtn = view.findViewById(R.id.edit_profiles_btn);
        profileList = view.findViewById(R.id.profile_list);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        profileList.setLayoutManager(linearLayoutManager);
        profileList.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                // чтобы картинки и текст не сбивались в кучку при быстрой промотке
                // (к списку профилей, может, не относится, но пусть как везде будет)
                // см: https://github.com/sadr0b0t/yashlang/issues/129
                return true;
            }
        });

        configureProfilesBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                dbExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final Intent intent = new Intent(
                                ConfigurePlaylistProfilesFragment.this.getContext(),
                                ConfigureProfilesActivity.class);
                        startActivity(intent);
                    }
                });
            }
        });
    }

    private void updateControlsVisibility() {
        final boolean listIsEmpty = profileList.getAdapter() == null ||
                profileList.getAdapter().getItemCount() == 0;

        if (listIsEmpty) {
            emptyView.setVisibility(View.VISIBLE);
            profileList.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            profileList.setVisibility(View.VISIBLE);
        }
    }

    public void updatePlaylistInfoBg() {
        this.updatePlaylistInfotBg(playlistId);
    }

    /**
     * Update playlist info in background
     *
     * @param plId
     */
    private void updatePlaylistInfotBg(final long plId) {
        dbAndNetworkExecutor.execute(new Runnable() {
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
                    final Bitmap plThumb = ThumbManager.getInstance().loadPlaylistThumb(
                            ConfigurePlaylistProfilesFragment.this.getContext(), plInfo);
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
        });

        setupProfileListAdapter(plId);
    }

    private void setupProfileListAdapter(final long plId) {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                // профили из базы данных
                final List<Profile> items = VideoDatabase.getDbInstance(getContext()).profileDao().getAll();
                // профили для текущего плейлиста
                final List<Long> profileIdsForPlaylist =
                        VideoDatabase.getDbInstance(getContext()).profileDao().getProfileIdsForPlaylist(plId);

                final ProfileArrayAdapter adapter = new ProfileArrayAdapter(items, new ListItemSwitchController<Profile>() {
                    @Override
                    public void onItemCheckedChanged(final CompoundButton buttonView, final int position, final Profile item, final  boolean isChecked) {
                        if(isChecked) {
                            // сделаем это заранее здесь, а не после фактического изменения базы в фоновом потоке,
                            // т.к. если это делать после обращения к базе данных внутри фонового потока,
                            // адаптер может запросить статус флага из isItemChecked раньше, чем мы сдесь
                            // сменим состояние профиля и это будет выглядеть, как глюк с ненажавшейся переключалкой
                            profileIdsForPlaylist.add(item.getId());
                            dbExecutor.execute(new Runnable() {
                                @Override
                                public void run() {
                                    VideoDatabase.getDbInstance(getContext()).profileDao().addPlaylistToProfile(item.getId(), plId);

                                }
                            });
                        } else {
                            profileIdsForPlaylist.remove(item.getId());
                            dbExecutor.execute(new Runnable() {
                                @Override
                                public void run() {
                                    VideoDatabase.getDbInstance(getContext()).profileDao().removePlaylistFromProfile(item.getId(), plId);
                                }
                            });
                        }
                    }

                    @Override
                    public boolean isItemChecked(final Profile item) {
                        return profileIdsForPlaylist.contains(item.getId());
                    }

                    @Override
                    public boolean showItemCheckbox(Profile item) {
                        return true;
                    }
                });

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        profileList.setAdapter(adapter);
                        updateControlsVisibility();
                    }
                });
            }
        });
    }
}
