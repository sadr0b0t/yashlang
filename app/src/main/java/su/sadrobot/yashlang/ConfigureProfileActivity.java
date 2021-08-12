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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.Profile;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.OnListItemSwitchListener;
import su.sadrobot.yashlang.view.PlaylistInfoArrayAdapter;

/**
 *
 */
public class ConfigureProfileActivity extends AppCompatActivity {

    /**
     * Id профиля для редактирования.
     * Если не указан или указан Profile.ID_NONE, будет создан новый профиль.
     */
    public static final String PARAM_PROFILE_ID = "PARAM_PROFILE_ID";

    private Toolbar toolbar;

    private EditText profileNameTxt;

    // Экран с пустым списком
    private View emptyView;

    //
    private RecyclerView playlistList;


    private Handler handler = new Handler();

    private long profileId = Profile.ID_NONE;
    private Profile profile;
    private Set<Long> checkedPlaylists = new HashSet<>();

    private boolean saveOnFinish = true;

    private RecyclerView.AdapterDataObserver emptyListObserver = new RecyclerView.AdapterDataObserver() {
        // https://stackoverflow.com/questions/47417645/empty-view-on-a-recyclerview
        // https://stackoverflow.com/questions/27414173/equivalent-of-listview-setemptyview-in-recyclerview
        // https://gist.github.com/sheharyarn/5602930ad84fa64c30a29ab18eb69c6e
        private void checkIfEmpty() {
            final boolean listIsEmpty = playlistList.getAdapter() == null || playlistList.getAdapter().getItemCount() == 0;
            emptyView.setVisibility(listIsEmpty ? View.VISIBLE : View.GONE);
            playlistList.setVisibility(listIsEmpty ? View.GONE : View.VISIBLE);
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_configure_profile);

        toolbar = findViewById(R.id.toolbar);

        profileNameTxt = findViewById(R.id.profile_name_txt);
        emptyView = findViewById(R.id.empty_view);
        playlistList = findViewById(R.id.playlist_list);

        // https://developer.android.com/training/appbar
        // https://www.vogella.com/tutorials/AndroidActionBar/article.html#custom-views-in-the-action-bar
        setSupportActionBar(toolbar);
        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        playlistList.setLayoutManager(linearLayoutManager);

        profileId = getIntent().getLongExtra(PARAM_PROFILE_ID, Profile.ID_NONE);
        loadProfile();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // https://developer.android.com/training/appbar/action-views.html

        toolbar.inflateMenu(R.menu.configure_profile_actions);
        if(profileId == Profile.ID_NONE) {
            toolbar.getMenu().removeItem(R.id.action_delete);
        }

        toolbar.setOnMenuItemClickListener(
                new Toolbar.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        return onOptionsItemSelected(item);
                    }
                });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_select_all:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final List<Long> allPlaylists = VideoDatabase.getDbInstance(
                                ConfigureProfileActivity.this).playlistInfoDao().getAllIds();

                        checkedPlaylists.clear();
                        checkedPlaylists.addAll(allPlaylists);

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                playlistList.getAdapter().notifyDataSetChanged();
                            }
                        });
                    }
                }).start();

                break;
            case R.id.action_select_none:
                checkedPlaylists.clear();
                playlistList.getAdapter().notifyDataSetChanged();
                break;
            case R.id.action_cancel:
                saveOnFinish = false;
                ConfigureProfileActivity.this.finish();
                break;
            case R.id.action_delete:
                if(profileId != Profile.ID_NONE) {
                    new AlertDialog.Builder(ConfigureProfileActivity.this)
                            .setTitle(getString(R.string.delete_profile_title).replace("%s", profile.getName()))
                            .setMessage(getString(R.string.delete_profile_message))
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog, int whichButton) {

                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            VideoDatabase.getDbInstance(ConfigureProfileActivity.this).profileDao().delete(profile);

                                            handler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    handler.post(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            Toast.makeText(
                                                                    ConfigureProfileActivity.this,
                                                                    getString(R.string.profile_is_deleted).replace("%s", profile.getName()),
                                                                    Toast.LENGTH_LONG).show();
                                                        }
                                                    });

                                                    saveOnFinish = false;
                                                    ConfigureProfileActivity.this.finish();
                                                }
                                            });
                                        }
                                    }).start();

                                }
                            })
                            .setNegativeButton(android.R.string.no, null).show();
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();

        if(saveOnFinish) {
            saveProfile();
        }
    }

    private void updateControlsVisibility() {
        // если список пустой, показываем специальный экранчик
        final boolean listIsEmpty = playlistList.getAdapter() == null || playlistList.getAdapter().getItemCount() == 0;
        emptyView.setVisibility(listIsEmpty ? View.VISIBLE : View.GONE);
        playlistList.setVisibility(listIsEmpty ? View.GONE : View.VISIBLE);
    }

    private void saveProfile() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                profile.setName(profileNameTxt.getText().toString());
                if(profileId == Profile.ID_NONE) {
                    profileId = VideoDatabase.getDbInstance(ConfigureProfileActivity.this).profileDao().insert(profile, checkedPlaylists);
                } else {
                    VideoDatabase.getDbInstance(ConfigureProfileActivity.this).profileDao().update(profile, checkedPlaylists);
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(
                                ConfigureProfileActivity.this,
                                getString(R.string.profile_is_saved).replace("%s", profile.getName()),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void loadProfile() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if(profileId == Profile.ID_NONE) {
                    // профиля нет в базе данных
                    profile = new Profile(Profile.ID_NONE, ConfigureProfileActivity.this.getString(R.string.new_profile_name));
                    checkedPlaylists.clear();
                } else {
                    // профиль есть в базе данных
                    final VideoDatabase videodb = VideoDatabase.getDbInstance(ConfigureProfileActivity.this);
                    profile = videodb.profileDao().getById(profileId);
                    checkedPlaylists.clear();
                    checkedPlaylists.addAll(videodb.profileDao().getProfilePlaylistsIds(profileId));
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        profileNameTxt.setText(profile.getName());
                    }
                });

                setupPlaylistListAdapter();
            }
        }).start();
    }

    private void setupPlaylistListAdapter() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<PlaylistInfo> items = VideoDatabase.getDbInstance(ConfigureProfileActivity.this).playlistInfoDao().getAll();

                final PlaylistInfoArrayAdapter adapter = new PlaylistInfoArrayAdapter(ConfigureProfileActivity.this, items,
                        new OnListItemClickListener<PlaylistInfo>() {
                            @Override
                            public void onItemClick(final View view, final int position, final PlaylistInfo item) {
                            }

                            @Override
                            public boolean onItemLongClick(final View view, final int position, final PlaylistInfo plInfo) {

                                // параметр Gravity.CENTER не работает (и появился еще только в API 19+),
                                // работает только вариант Gravity.RIGHT
                                //final PopupMenu popup = new PopupMenu(ConfigurePlaylistsActivity.this, view, Gravity.CENTER);
                                final PopupMenu popup = new PopupMenu(ConfigureProfileActivity.this,
                                        view.findViewById(R.id.playlist_name_txt));
                                popup.getMenuInflater().inflate(R.menu.playlist_item_actions, popup.getMenu());
                                popup.setOnMenuItemClickListener(
                                        new PopupMenu.OnMenuItemClickListener() {
                                            @Override
                                            public boolean onMenuItemClick(final MenuItem item) {
                                                switch (item.getItemId()) {
                                                    case R.id.action_copy_playlist_name: {
                                                        final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                        final ClipData clip = ClipData.newPlainText(plInfo.getName(), plInfo.getName());
                                                        clipboard.setPrimaryClip(clip);

                                                        Toast.makeText(ConfigureProfileActivity.this,
                                                                getString(R.string.copied) + ": " + plInfo.getName(),
                                                                Toast.LENGTH_LONG).show();
                                                        break;
                                                    }
                                                    case R.id.action_copy_playlist_url: {
                                                        final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                        final ClipData clip = ClipData.newPlainText(plInfo.getUrl(), plInfo.getUrl());
                                                        clipboard.setPrimaryClip(clip);

                                                        Toast.makeText(ConfigureProfileActivity.this,
                                                                getString(R.string.copied) + ": " + plInfo.getUrl(),
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
                        new OnListItemSwitchListener<PlaylistInfo>() {
                            @Override
                            public void onItemCheckedChanged(final CompoundButton buttonView, final int position, final PlaylistInfo item, final boolean isChecked) {
                                if (isChecked) {
                                    checkedPlaylists.add(item.getId());
                                } else {
                                    checkedPlaylists.remove(item.getId());
                                }
                            }

                            @Override
                            public boolean isItemChecked(final PlaylistInfo item) {
                                return checkedPlaylists.contains(item.getId());
                            }
                        });

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        playlistList.setAdapter(adapter);

                        updateControlsVisibility();
                    }
                });

            }
        }).start();
    }
}
