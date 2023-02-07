package su.sadrobot.yashlang;

/*
 * Copyright (C) Anton Moiseev 2022 <github.com/sadr0b0t>
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
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;
import java.util.List;

import su.sadrobot.yashlang.controller.StreamCacheFsManager;
import su.sadrobot.yashlang.controller.StreamCacheManager;
import su.sadrobot.yashlang.service.StreamCacheDownloadService;
import su.sadrobot.yashlang.util.StringFormatUtil;


public class StreamCacheActivity extends AppCompatActivity {
    // https://developer.android.com/guide/components/fragments
    // https://developer.android.com/guide/navigation/navigation-swipe-view-2

    private TabLayout tabs;
    private ViewPager2 pager;

    private Toolbar toolbar;

    private StreamCacheDownloadFragment streamCacheDownloadFrag;
    private StreamCacheFragment streamCacheFrag;
    private StreamCacheFsStatusFragment streamCacheFsStatusFrag;

    private final Handler handler = new Handler();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_stream_cache);

        tabs = findViewById(R.id.tabs);
        pager = findViewById(R.id.pager);
        toolbar = findViewById(R.id.toolbar);

        // https://developer.android.com/training/appbar
        // https://www.vogella.com/tutorials/AndroidActionBar/article.html#custom-views-in-the-action-bar
        setSupportActionBar(toolbar);
        // кнопка "Назад" на акшенбаре
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        streamCacheDownloadFrag = new StreamCacheDownloadFragment();
        streamCacheFrag = new StreamCacheFragment();
        streamCacheFsStatusFrag = new StreamCacheFsStatusFragment();

        pager.setAdapter(new FragmentStateAdapter(getSupportFragmentManager(), getLifecycle()) {
            @Override
            public int getItemCount() {
                return 3;
            }

            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == 0) {
                    return streamCacheDownloadFrag;
                } else if (position == 1) {
                    return streamCacheFrag;
                } else {
                    return streamCacheFsStatusFrag;
                }
            }
        });

        new TabLayoutMediator(tabs, pager,
                new TabLayoutMediator.TabConfigurationStrategy() {
                    @Override
                    public void onConfigureTab(@NonNull TabLayout.Tab tab, int position) {
                        if (position == 0) {
                            tab.setText(R.string.tab_item_stream_cache_download);
                        } else if (position == 1) {
                            tab.setText(R.string.tab_item_stream_cache);
                        } else {
                            tab.setText(R.string.tab_item_size_on_disk);
                        }
                    }
                }).attach();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // https://developer.android.com/training/appbar/action-views.html

        toolbar.inflateMenu(R.menu.stream_cache_actions);

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
            case R.id.action_delete_not_finished:
                actionDeleteNotFinished();
                break;
            case R.id.action_manage_cache_dir:
                actionManageCacheDir();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void actionDeleteNotFinished() {
        new AlertDialog.Builder(StreamCacheActivity.this)
                .setTitle(R.string.delete_not_finished_title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        StreamCacheDownloadService.pauseDownloads(StreamCacheActivity.this);
                        // todo: здесь будет правильно дождаться, когда потоки будут точно остановлены
                        // чтобы не удалить файл, например, в момент записи
                        StreamCacheManager.getInstance().deleteNotFinished(StreamCacheActivity.this);
                    }
                })
                .setNegativeButton(android.R.string.no, null).show();
    }

    private void actionManageCacheDir() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<File> unmanagedFiles = StreamCacheFsManager.getUnmanagedFiles(StreamCacheActivity.this);
                if (unmanagedFiles.isEmpty()) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(StreamCacheActivity.this,
                                    getString(R.string.no_unmanaged_cache_files),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    final StringBuilder unmanagedFilesListStr = new StringBuilder();
                    unmanagedFilesListStr.append(StreamCacheFsManager.getStreamCacheDir(StreamCacheActivity.this));
                    final long totalSize = StreamCacheFsManager.getUnmanagedFilesFsSize(StreamCacheActivity.this);
                    for (final File file : unmanagedFiles) {
                        unmanagedFilesListStr.append(
                                "\n" + file.getName() +" (" +
                                StringFormatUtil.formatFileSize(StreamCacheActivity.this, file.length()) +
                                ")");
                    }
                    final String title = getString(R.string.delete_unmanaged_files_title).
                            replace("%c", String.valueOf(unmanagedFiles.size())).
                            replace("%s", StringFormatUtil.formatFileSize(StreamCacheActivity.this, totalSize));
                    final String msgSuccess = getString(R.string.deleted_unmanaged_files).
                            replace("%c", String.valueOf(unmanagedFiles.size())).
                            replace("%s", StringFormatUtil.formatFileSize(StreamCacheActivity.this, totalSize));

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(StreamCacheActivity.this)
                                    .setTitle(title)
                                    .setMessage(unmanagedFilesListStr)
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    for (final File file : unmanagedFiles) {
                                                        if(file.exists()) {
                                                            file.delete();
                                                        }
                                                    }

                                                    handler.post(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            Toast.makeText(StreamCacheActivity.this,
                                                                    msgSuccess, Toast.LENGTH_LONG).show();
                                                        }
                                                    });
                                                }
                                            }).start();
                                        }
                                    })
                                    .setNegativeButton(android.R.string.no, null).show();
                        }
                    });
                }
            }
        }).start();
    }
}
