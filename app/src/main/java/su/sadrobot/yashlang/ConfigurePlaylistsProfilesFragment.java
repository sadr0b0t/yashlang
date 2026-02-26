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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
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
import androidx.paging.DataSource;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private final Handler handler = new Handler();
    // достаточно одного фонового потока
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    // Если true, показывать только функции редактирования списка профилей и самих профилей
    // (добавить/удалить/редактировать) и не показывать функции применения профиля для установки
    // текущих активных плейлистов
    private boolean editOnly = false;

    // слушаем изменения в таблице профилей
    // https://developer.android.com/reference/kotlin/androidx/paging/DataSource
    // https://developer.android.com/reference/kotlin/androidx/paging/DataSource#addInvalidatedCallback(androidx.paging.DataSource.InvalidatedCallback)
    private DataSource.Factory<Integer, Profile> profilesDsFactory;
    private DataSource profilesDs;

    // это событие произойдет ровно один раз - когда таблица профилей изменится
    // (текущий объект DataSource станет "invalid")
    // после этого нужно создавать новый DataSource и назначать такой же колбэк,
    // чтобы можно было поймать следующее изменение
    final DataSource.InvalidatedCallback profilesInvalidatedCallback = new DataSource.InvalidatedCallback() {
        @Override
        public void onInvalidated() {
            // эта переменная не должна быть локальной, ее важно иметь как член класса,
            // иначе она может здесь умереть и колбэки перестанут приходить
            profilesDs = profilesDsFactory.create();
            profilesDs.addInvalidatedCallback(profilesInvalidatedCallback);
            onProfilesDataChanged();
        }
    };

    @Override
    public void onInflate(@NonNull Context context, @NonNull AttributeSet attrs, @Nullable Bundle savedInstanceState) {
        super.onInflate(context, attrs, savedInstanceState);
        // https://stackoverflow.com/questions/31483596/android-fragment-how-to-pass-values-in-xml
        // https://developer.android.com/reference/android/support/v4/app/Fragment.html#onInflate(android.app.Activity,%20android.util.AttributeSet,%20android.os.Bundle)
        // https://web.archive.org/web/20260122133951/https://codingtechroom.com/question/how-to-access-custom-attributes-in-android
        // https://stackoverflow.com/questions/2127177/how-do-i-use-obtainstyledattributesint-with-internal-themes-of-android
        // https://github.com/aosp-mirror/platform_development/blob/master/samples/ApiDemos/src/com/example/android/apis/view/LabelView.java
        // https://github.com/aosp-mirror/platform_development/blob/master/samples/ApiDemos/res/values/attrs.xml#L24
        // по ссылкам выше примерно написано, как опредилить атрибуты и считать их в файле java, но не написано,
        // как их задавать в файле xml
        // этот файл нашел сам на удачу, гуляя по проекту по ссылке выше:
        // https://github.com/aosp-mirror/platform_development/blob/master/samples/ApiDemos/res/layout/custom_layout.xml
        // здесь пример указания кастомного атрибута через неймспейс "app:custom_attr"
        // но чтобы это заработало, в верхнем элементе лэйаута нужно добавить:
        // xmlns:app="http://schemas.android.com/apk/res-auto"
        // это значение предложила добавить среда разработки и это помогло (без этого неймспейс app не доступен)
        // определить допустимые атрибуты - см в values/attrs.xml
        // задать значение для вью в файле:
        // app:edit_only="true"
        final TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.ConfigurePlaylistsProfilesFragment, 0, 0);
        try {
            editOnly = a.getBoolean(R.styleable.ConfigurePlaylistsProfilesFragment_edit_only, false);
        } finally {
            a.recycle();
        }
    }

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
                final Intent intent = new Intent(
                        ConfigurePlaylistsProfilesFragment.this.getContext(),
                        ConfigureProfileActivity.class);
                // новый профиль
                intent.putExtra(ConfigureProfileActivity.PARAM_PROFILE_ID, Profile.ID_NONE);
                startActivity(intent);
            }
        });

        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final VideoDatabase videodb = VideoDatabase.getDbInstance(ConfigurePlaylistsProfilesFragment.this.getContext());
                profilesDsFactory = videodb.profileDao().getAllDs();
                profilesDs = profilesDsFactory.create();
                profilesDs.addInvalidatedCallback(profilesInvalidatedCallback);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        setupProfileListArrayAdapter();
    }

    private void onProfilesDataChanged() {
        setupProfileListArrayAdapter();
    }

    void setupProfileListArrayAdapter() {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) {
                    // без этой проверки время от времени (скорее часто, чем редко)
                    // будем ловить null pointer на getString
                    // https://stackoverflow.com/questions/13956528/save-data-in-activitys-ondestroy-method
                    return;
                }

                final List<Profile> items = new ArrayList<>();
                final List<Integer> listSeparators = new ArrayList<>();

                if (!editOnly) {
                    // три ненастраиваемых профиля с жестким поведением не из базы данных
                    items.add(new Profile(Profile.ID_ENABLE_ALL, getString(R.string.enable_all).toUpperCase()));
                    items.add(new Profile(Profile.ID_DISABLE_ALL, getString(R.string.disable_all).toUpperCase()));
                    items.add(new Profile(Profile.ID_DISABLE_YT, getString(R.string.disble_all_yt).toUpperCase()));

                    listSeparators.add(3);
                }
                // профили из базы данных
                final VideoDatabase videodb = VideoDatabase.getDbInstance(getContext());
                items.addAll(videodb.profileDao().getAll());
                // статусы есть/нет привязанные метки
                for (final Profile p : items) {
                    p.setHasBoundNfcTags(videodb.profileDao().getProfileNfcTags(p.getId()).size() > 0);
                }

                final ProfileArrayAdapter adapter = new ProfileArrayAdapter(items, listSeparators,
                        new OnListItemClickListener<Profile>() {
                            @Override
                            public void onItemClick(final View view, final int position, final Profile profile) {
                                final PopupMenu popup = new PopupMenu(ConfigurePlaylistsProfilesFragment.this.getContext(),
                                        view);
                                popup.getMenuInflater().inflate(R.menu.profile_actions, popup.getMenu());
                                if (editOnly) {
                                    popup.getMenu().removeItem(R.id.action_apply);
                                    popup.getMenu().removeItem(R.id.action_add_to_enabled);
                                } else if (profile.getId() == Profile.ID_ENABLE_ALL ||
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
                                                            dbExecutor.execute(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    VideoDatabase.getDbInstance(getContext()).
                                                                            playlistInfoDao().setEnabled4All(true);

                                                                    handler.post(new Runnable() {
                                                                        @Override
                                                                        public void run() {
                                                                            Toast.makeText(
                                                                                    ConfigurePlaylistsProfilesFragment.this.getContext(),
                                                                                    getString(R.string.enabled_all),
                                                                                    Toast.LENGTH_LONG).show();
                                                                        }
                                                                    });
                                                                }
                                                            });
                                                        } else if (profile.getId() == Profile.ID_DISABLE_ALL) {
                                                            dbExecutor.execute(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    VideoDatabase.getDbInstance(getContext()).
                                                                            playlistInfoDao().setEnabled4All(false);

                                                                    handler.post(new Runnable() {
                                                                        @Override
                                                                        public void run() {
                                                                            Toast.makeText(
                                                                                    ConfigurePlaylistsProfilesFragment.this.getContext(),
                                                                                    getString(R.string.disabled_all),
                                                                                    Toast.LENGTH_LONG).show();
                                                                        }
                                                                    });
                                                                }
                                                            });
                                                        } else if (profile.getId() == Profile.ID_DISABLE_YT) {
                                                            dbExecutor.execute(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    VideoDatabase.getDbInstance(getContext()).
                                                                            playlistInfoDao().setEnabled4Yt(false);

                                                                    handler.post(new Runnable() {
                                                                        @Override
                                                                        public void run() {
                                                                            Toast.makeText(
                                                                                    ConfigurePlaylistsProfilesFragment.this.getContext(),
                                                                                    getString(R.string.disabled_all_yt),
                                                                                    Toast.LENGTH_LONG).show();
                                                                        }
                                                                    });
                                                                }
                                                            });
                                                        } else {
                                                            dbExecutor.execute(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    final VideoDatabase videodb = VideoDatabase.getDbInstance(getContext());
                                                                    final List<Long> plIds = videodb.profileDao().getProfilePlaylistsIds(profile.getId());
                                                                    videodb.playlistInfoDao().enableOnlyPlaylists(plIds);

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
                                                            });
                                                        }
                                                        break;
                                                    }
                                                    case R.id.action_add_to_enabled: {
                                                        dbExecutor.execute(new Runnable() {
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
                                                        });
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
                                                                        dbExecutor.execute(new Runnable() {
                                                                            @Override
                                                                            public void run() {
                                                                                VideoDatabase.getDbInstance(getContext()).profileDao().delete(profile);
                                                                                // Это здесь больше не нужно - см выше onProfilesDataChanged()
                                                                                //setupProfileListArrayAdapter();

                                                                                handler.post(new Runnable() {
                                                                                    @Override
                                                                                    public void run() {
                                                                                        Toast.makeText(
                                                                                                ConfigurePlaylistsProfilesFragment.this.getContext(),
                                                                                                getString(R.string.profile_is_deleted).replace("%s", profile.getName()),
                                                                                                Toast.LENGTH_LONG).show();
                                                                                    }
                                                                                });
                                                                            }
                                                                        });
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
                            public boolean onItemLongClick(final View view, final int position, final Profile profile) {
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
        });
    }
}
