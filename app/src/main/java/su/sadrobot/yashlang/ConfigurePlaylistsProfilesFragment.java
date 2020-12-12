package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * ConfigurePlaylistActivity.java is part of YaShlang.
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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import su.sadrobot.yashlang.model.Profile;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.ProfileArrayAdapter;

/**
 *
 */
public class ConfigurePlaylistsProfilesFragment extends Fragment {

    private Button newProfileBtn;
    private RecyclerView profileList;

    private Handler handler = new Handler();

    private LiveData<PagedList<Profile>> videoItemsLiveData;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_configure_playlists_profiles, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        newProfileBtn = view.findViewById(R.id.new_profile_btn);

        profileList = view.findViewById(R.id.profile_list);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        profileList.setLayoutManager(linearLayoutManager);
        profileList.addItemDecoration(new DividerItemDecoration(this.getContext(), DividerItemDecoration.VERTICAL));

        newProfileBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final Intent intent = new Intent(
                                ConfigurePlaylistsProfilesFragment.this.getContext(),
                                ConfigureProfileActivity.class);
                        // новый профиль
                        intent.putExtra(ConfigureProfileActivity.PARAM_PROFILE_ID, Profile.ID_NONE);
                        startActivity(intent);
                    }
                }).start();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        setupProfileListArrayAdapter();
    }


    void setupProfileListArrayAdapter() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Profile> items = new ArrayList<>();

                // три ненастраиваемых профиля с жестким поведением не из базы данных
                items.add(new Profile(Profile.ID_ENABLE_ALL, getString(R.string.enable_all).toUpperCase()));
                items.add(new Profile(Profile.ID_DISABLE_ALL, getString(R.string.disable_all).toUpperCase()));
                items.add(new Profile(Profile.ID_DISABLE_YT, getString(R.string.disble_all_yt).toUpperCase()));

                // профили из базы данных
                items.addAll(VideoDatabase.getDbInstance(getContext()).profileDao().getAll());

                final List<Integer> listSeparators = new ArrayList<>();
                listSeparators.add(3);

                final ProfileArrayAdapter adapter = new ProfileArrayAdapter(items, listSeparators,
                        new OnListItemClickListener<Profile>() {
                            @Override
                            public void onItemClick(final View view, final int position, final Profile profile) {
                                final PopupMenu popup = new PopupMenu(ConfigurePlaylistsProfilesFragment.this.getContext(),
                                        view);
                                popup.getMenuInflater().inflate(R.menu.profile_actions, popup.getMenu());
                                if (profile.getId() == Profile.ID_ENABLE_ALL ||
                                        profile.getId() == Profile.ID_DISABLE_ALL ||
                                        profile.getId() == Profile.ID_DISABLE_YT) {
                                    popup.getMenu().removeItem(R.id.action_add_to_enabled);
                                    popup.getMenu().removeItem(R.id.action_edit);
                                    popup.getMenu().removeItem(R.id.action_delete);
                                }
                                popup.setOnMenuItemClickListener(
                                        new PopupMenu.OnMenuItemClickListener() {
                                            @Override
                                            public boolean onMenuItemClick(final MenuItem item) {
                                                switch (item.getItemId()) {
                                                    case R.id.action_apply: {
                                                        if (profile.getId() == Profile.ID_ENABLE_ALL) {
                                                            new Thread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    VideoDatabase.getDbInstance(getContext()).
                                                                            playlistInfoDao().setEnabled4All(true);
                                                                }
                                                            }).start();

                                                            handler.post(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    Toast.makeText(
                                                                            ConfigurePlaylistsProfilesFragment.this.getContext(),
                                                                            getString(R.string.enabled_all),
                                                                            Toast.LENGTH_LONG).show();
                                                                }
                                                            });
                                                        } else if (profile.getId() == Profile.ID_DISABLE_ALL) {
                                                            new Thread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    VideoDatabase.getDbInstance(getContext()).
                                                                            playlistInfoDao().setEnabled4All(false);
                                                                }
                                                            }).start();

                                                            handler.post(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    Toast.makeText(
                                                                            ConfigurePlaylistsProfilesFragment.this.getContext(),
                                                                            getString(R.string.disabled_all),
                                                                            Toast.LENGTH_LONG).show();
                                                                }
                                                            });
                                                        } else if (profile.getId() == Profile.ID_DISABLE_YT) {
                                                            new Thread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    VideoDatabase.getDbInstance(getContext()).
                                                                            playlistInfoDao().setEnabled4Yt(false);
                                                                }
                                                            }).start();

                                                            handler.post(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    Toast.makeText(
                                                                            ConfigurePlaylistsProfilesFragment.this.getContext(),
                                                                            getString(R.string.disabled_all_yt),
                                                                            Toast.LENGTH_LONG).show();
                                                                }
                                                            });
                                                        } else {
                                                            new Thread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    final VideoDatabase videodb = VideoDatabase.getDbInstance(getContext());
                                                                    final List<Long> plIds = videodb.profileDao().getProfilePlaylistsIds(profile.getId());
                                                                    videodb.playlistInfoDao().enableOnlyPlaylists(plIds);
                                                                }
                                                            }).start();

                                                            handler.post(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    Toast.makeText(
                                                                            ConfigurePlaylistsProfilesFragment.this.getContext(),
                                                                            getString(R.string.applied_profile).replace("%s", profile.getName()),
                                                                            Toast.LENGTH_LONG).show();
                                                                }
                                                            });
                                                        }
                                                        break;
                                                    }
                                                    case R.id.action_add_to_enabled: {
                                                        new Thread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                final VideoDatabase videodb = VideoDatabase.getDbInstance(getContext());
                                                                final List<Long> plIds = videodb.profileDao().getProfilePlaylistsIds(profile.getId());
                                                                videodb.playlistInfoDao().enableAlsoPlaylists(plIds);

                                                                handler.post(new Runnable() {
                                                                    @Override
                                                                    public void run() {
                                                                        Toast.makeText(
                                                                                ConfigurePlaylistsProfilesFragment.this.getContext(),
                                                                                getString(R.string.enabled_from_profile).replace("%s", profile.getName()),
                                                                                Toast.LENGTH_LONG).show();
                                                                    }
                                                                });
                                                            }
                                                        }).start();
                                                        break;
                                                    }
                                                    case R.id.action_edit: {
                                                        final Intent intent = new Intent(
                                                                ConfigurePlaylistsProfilesFragment.this.getContext(),
                                                                ConfigureProfileActivity.class);
                                                        intent.putExtra(ConfigureProfileActivity.PARAM_PROFILE_ID, profile.getId());
                                                        startActivity(intent);
                                                        break;
                                                    }
                                                    case R.id.action_delete: {
                                                        new AlertDialog.Builder(ConfigurePlaylistsProfilesFragment.this.getContext())
                                                                .setTitle(getString(R.string.delete_profile_title).replace("%s", profile.getName()))
                                                                .setMessage(getString(R.string.delete_profile_message))
                                                                .setIcon(android.R.drawable.ic_dialog_alert)
                                                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                                                    public void onClick(DialogInterface dialog, int whichButton) {
                                                                        new Thread(new Runnable() {
                                                                            @Override
                                                                            public void run() {
                                                                                VideoDatabase.getDbInstance(getContext()).profileDao().delete(profile);

                                                                                handler.post(new Runnable() {
                                                                                    @Override
                                                                                    public void run() {
                                                                                        Toast.makeText(
                                                                                                ConfigurePlaylistsProfilesFragment.this.getContext(),
                                                                                                getString(R.string.profile_is_deleted).replace("%s", profile.getName()),
                                                                                                Toast.LENGTH_LONG).show();
                                                                                    }
                                                                                });
                                                                                setupProfileListArrayAdapter();
                                                                            }
                                                                        }).start();

                                                                    }
                                                                })
                                                                .setNegativeButton(android.R.string.no, null).show();
                                                        break;
                                                    }
                                                }
                                                return true;
                                            }
                                        }
                                );
                                popup.show();
                            }

                            @Override
                            public boolean onItemLongClick(final View view, final int position, final Profile videoItem) {
                                return false;
                            }
                        });

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        profileList.setAdapter(adapter);
                    }
                });
            }
        }).start();
    }
}
