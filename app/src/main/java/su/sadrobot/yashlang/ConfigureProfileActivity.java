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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import su.sadrobot.yashlang.model.Profile;
import su.sadrobot.yashlang.model.VideoDatabase;

/**
 *
 */
public class ConfigureProfileActivity extends AppCompatActivity {

    /**
     * Id профиля для редактирования.
     * Если не указан или указан Profile.ID_NONE, будет создан новый профиль.
     */
    public static final String PARAM_PROFILE_ID = "PARAM_PROFILE_ID";

    private TabLayout tabs;
    private ViewPager2 pager;

    private Toolbar toolbar;

    private EditText profileNameTxt;
    private ConfigureProfilePlaylistsFragment configureProfilePlaylistsFrag;
    private ConfigureProfileNfcTagsFragment configureProfileNfcTagsFrag;

    private final Handler handler = new Handler();
    // достаточно одного фонового потока
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private long profileId = Profile.ID_NONE;
    private Profile profile;

    private boolean saveOnFinish = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_configure_profile);

        tabs = findViewById(R.id.tabs);
        pager = findViewById(R.id.pager);
        toolbar = findViewById(R.id.toolbar);

        profileNameTxt = findViewById(R.id.profile_name_txt);

        // https://developer.android.com/training/appbar
        // https://www.vogella.com/tutorials/AndroidActionBar/article.html#custom-views-in-the-action-bar
        setSupportActionBar(toolbar);
        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        profileId = getIntent().getLongExtra(PARAM_PROFILE_ID, Profile.ID_NONE);
        configureProfilePlaylistsFrag = new ConfigureProfilePlaylistsFragment(profileId);
        configureProfileNfcTagsFrag = new ConfigureProfileNfcTagsFragment(profileId);

        pager.setAdapter(new FragmentStateAdapter(getSupportFragmentManager(), getLifecycle()) {
            @Override
            public int getItemCount() {
                return 2;
            }

            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == 0) {
                    return configureProfilePlaylistsFrag;
                } else {
                    return configureProfileNfcTagsFrag;
                }
            }
        });

        new TabLayoutMediator(tabs, pager,
                new TabLayoutMediator.TabConfigurationStrategy() {
                    @Override
                    public void onConfigureTab(@NonNull TabLayout.Tab tab, int position) {
                        if (position == 0) {
                            tab.setText(R.string.tab_item_playlists);
                        } else {
                            tab.setText(R.string.tab_item_nfc_tags);
                        }
                    }
                }).attach();

        loadProfile();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // https://developer.android.com/training/appbar/action-views.html

        toolbar.inflateMenu(R.menu.configure_profile_actions);
        if (profileId == Profile.ID_NONE) {
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
            case R.id.action_cancel:
                saveOnFinish = false;
                ConfigureProfileActivity.this.finish();
                break;
            case R.id.action_delete:
                if (profileId != Profile.ID_NONE) {
                    new AlertDialog.Builder(ConfigureProfileActivity.this)
                            .setTitle(getString(R.string.delete_profile_title).replace("%s", profile.getName()))
                            .setMessage(getString(R.string.delete_profile_message))
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog, int whichButton) {

                                    dbExecutor.execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            VideoDatabase.getDbInstance(ConfigureProfileActivity.this).profileDao().delete(profile);

                                            handler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(
                                                            ConfigureProfileActivity.this,
                                                            getString(R.string.profile_is_deleted).replace("%s", profile.getName()),
                                                            Toast.LENGTH_LONG).show();

                                                    saveOnFinish = false;
                                                    ConfigureProfileActivity.this.finish();
                                                }
                                            });
                                        }
                                    });

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
    protected void onStop() {
        if (saveOnFinish) {
            saveProfile();
        }
        super.onStop();
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        // перенаправляем NFC intent
        // включение/выключение интента делаем внутри configureProfileNfcTagsFrag
        configureProfileNfcTagsFrag.onNewIntent(intent);
    }

    private void saveProfile() {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                profile.setName(profileNameTxt.getText().toString());
                final VideoDatabase videodb = VideoDatabase.getDbInstance(ConfigureProfileActivity.this);
                if (profileId == Profile.ID_NONE) {
                    profileId = videodb.profileDao().insert(profile, configureProfilePlaylistsFrag.getCheckedPlaylists());
                } else {
                    videodb.profileDao().update(profile, configureProfilePlaylistsFrag.getCheckedPlaylists());
                }
                if (configureProfileNfcTagsFrag.isAdded()) {
                    // если вкладку не открывали, список будет пустой,
                    // хотя пользователь его не редактировал
                    videodb.profileDao().setNfcTags(profileId, configureProfileNfcTagsFrag.getNfcTags());
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
        });
    }

    private void loadProfile() {
        // загрузка профиля из базы должна быть в фоне
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (profileId == Profile.ID_NONE) {
                    // профиля нет в базе данных
                    profile = new Profile(Profile.ID_NONE, ConfigureProfileActivity.this.getString(R.string.new_profile_name));
                } else {
                    // профиль есть в базе данных
                    final VideoDatabase videodb = VideoDatabase.getDbInstance(ConfigureProfileActivity.this);
                    profile = videodb.profileDao().getById(profileId);
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        profileNameTxt.setText(profile.getName());
                    }
                });
            }
        });
    }
}
