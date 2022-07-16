package su.sadrobot.yashlang.controller;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2022.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * ContentLoader.java is part of YaShlang.
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
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.service.StreamCacheDownloadService;
import su.sadrobot.yashlang.view.StreamInfoArrayAdapter;

public class VideoItemActionFactory {
    public interface StreamDialogListener {
        void onClose();
        void onStreamsSelected(final StreamHelper.StreamInfo videoStream, final StreamHelper.StreamInfo audioStream);
    }

    public static void actionDownloadStreams(final Context context, final Handler handler, final VideoItem videoItem,
                                             final StreamDialogListener callback) {
        // https://developer.android.com/reference/android/view/LayoutInflater
        // https://stackoverflow.com/questions/51729036/what-is-layoutinflater-and-how-do-i-use-it-properly
        final LayoutInflater inflater = LayoutInflater.from(context);
        final View view = inflater.inflate(R.layout.view_video_item_streams, null, false);
        final ProgressBar fetchStreamsProgress = view.findViewById(R.id.fetch_streams_progress);
        final View errorView = view.findViewById(R.id.error_view);
        final View videoItemStreamsView = view.findViewById(R.id.video_items_streams_view);

        final TextView fetchErrorTxt = view.findViewById(R.id.fetch_error_txt);
        final TextView fetchErrorDetailsTxt = view.findViewById(R.id.fetch_error_details_txt);
        final Spinner videoStreamSpinner = view.findViewById(R.id.video_streams_spinner);
        final Spinner audioStreamSpinner = view.findViewById(R.id.audio_streams_spinner);

        final AlertDialog dlg1 = new AlertDialog.Builder(context)
                .setTitle(R.string.download_video_item_streams)
                .setView(view)
                .setPositiveButton(R.string.download, null)
                .setNegativeButton(android.R.string.cancel, null).create();
        if (callback != null) {
            dlg1.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    callback.onClose();
                }
            });
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        fetchStreamsProgress.setVisibility(View.VISIBLE);
                        errorView.setVisibility(View.GONE);
                        videoItemStreamsView.setVisibility(View.GONE);
                        dlg1.show();
                        dlg1.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);
                    }
                });

                // загрузить списки потоков видео и аудио
                final StreamHelper.StreamSources streamSources = StreamHelper.fetchOnlineStreams(videoItem);
                StreamHelper.sortVideoStreamsDefault(streamSources.getVideoStreams());
                StreamHelper.sortAudioStreamsDefault(streamSources.getAudioStreams());
                if (streamSources.getVideoStreams().isEmpty() &&
                        streamSources.getAudioStreams().isEmpty()) {
                    if (!streamSources.problems.isEmpty()) {
                        // нет ни видео, ни аудио потоков, при этом список проблем не пустой
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                fetchStreamsProgress.setVisibility(View.GONE);
                                errorView.setVisibility(View.VISIBLE);
                                videoItemStreamsView.setVisibility(View.GONE);
                                dlg1.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);

                                fetchErrorTxt.setText(context.getString(R.string.error_loading_streams_for_video));
                                fetchErrorDetailsTxt.setText(streamSources.problems.get(0).getMessage());
                            }
                        });
                    } else {
                        // нет ни видео, ни аудио потоков, но и явных проблем в списке проблем нет
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                fetchStreamsProgress.setVisibility(View.GONE);
                                errorView.setVisibility(View.GONE);
                                videoItemStreamsView.setVisibility(View.GONE);

                                fetchErrorTxt.setText(context.getString(R.string.no_streams_for_video_item));
                            }
                        });
                    }
                } else {
                    // нашли потоки видео или аудио или и то и другое
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            fetchStreamsProgress.setVisibility(View.GONE);
                            errorView.setVisibility(View.GONE);
                            videoItemStreamsView.setVisibility(View.VISIBLE);

                            final StreamInfoArrayAdapter videoStreamsAdapter = new StreamInfoArrayAdapter(
                                    context, streamSources.getVideoStreams());
                            videoStreamSpinner.setAdapter(videoStreamsAdapter);
                            if (videoItem.getPlaybackStreams() != null) {
                                videoStreamSpinner.setSelection(videoStreamsAdapter.indexOf(
                                        videoItem.getPlaybackStreams().getVideoStream()));
                            }

                            final StreamInfoArrayAdapter audioStreamsAdapter = new StreamInfoArrayAdapter(
                                    context, streamSources.getAudioStreams());
                            audioStreamSpinner.setAdapter(audioStreamsAdapter);
                            if (videoItem.getPlaybackStreams() != null) {
                                audioStreamSpinner.setSelection(audioStreamsAdapter.indexOf(
                                        videoItem.getPlaybackStreams().getAudioStream()));
                            }

                            dlg1.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
                            dlg1.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(android.R.string.yes),
                                    new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (videoStreamSpinner.getSelectedItem() != null ||
                                                    audioStreamSpinner.getSelectedItem() != null ) {
                                                StreamCacheManager.getInstance().queueForDownload(
                                                        context,
                                                        videoItem,
                                                        (StreamHelper.StreamInfo) videoStreamSpinner.getSelectedItem(),
                                                        (StreamHelper.StreamInfo) audioStreamSpinner.getSelectedItem(),
                                                        new StreamCacheManager.StreamCacheManagerListener() {
                                                            @Override
                                                            public void onStreamCacheAddedToQueue(final List<Long> insertedIds) {
                                                                StreamCacheDownloadService.startDownload(context, insertedIds);

                                                                handler.post(new Runnable() {
                                                                    @Override
                                                                    public void run() {
                                                                        if (callback != null) {
                                                                            callback.onStreamsSelected(
                                                                                    (StreamHelper.StreamInfo) videoStreamSpinner.getSelectedItem(),
                                                                                    (StreamHelper.StreamInfo) audioStreamSpinner.getSelectedItem());
                                                                        }

                                                                        Toast.makeText(context,
                                                                                R.string.streams_added_to_download_queue,
                                                                                Toast.LENGTH_LONG).show();
                                                                    }
                                                                });
                                                            }
                                                        });
                                            }
                                        }
                                    }).start();
                                }
                            });
                        }
                    });
                }
            }
        }).start();
    }

    public static void actionSelectStreams(final Context context, final Handler handler, final VideoItem videoItem,
                                           final StreamDialogListener callback) {
        // https://developer.android.com/reference/android/view/LayoutInflater
        // https://stackoverflow.com/questions/51729036/what-is-layoutinflater-and-how-do-i-use-it-properly
        final LayoutInflater inflater = LayoutInflater.from(context);
        final View view = inflater.inflate(R.layout.view_video_item_streams, null, false);
        final ProgressBar fetchStreamsProgress = view.findViewById(R.id.fetch_streams_progress);
        final View errorView = view.findViewById(R.id.error_view);
        final View videoItemStreamsView = view.findViewById(R.id.video_items_streams_view);

        final TextView fetchErrorTxt = view.findViewById(R.id.fetch_error_txt);
        final TextView fetchErrorDetailsTxt = view.findViewById(R.id.fetch_error_details_txt);
        final Spinner videoStreamSpinner = view.findViewById(R.id.video_streams_spinner);
        final Spinner audioStreamSpinner = view.findViewById(R.id.audio_streams_spinner);

        final AlertDialog dlg1 = new AlertDialog.Builder(context)
                .setTitle(R.string.select_video_item_streams)
                .setView(view)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null).create();
        if (callback != null) {
            dlg1.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    callback.onClose();
                }
            });
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        fetchStreamsProgress.setVisibility(View.VISIBLE);
                        errorView.setVisibility(View.GONE);
                        videoItemStreamsView.setVisibility(View.GONE);
                        dlg1.show();
                        dlg1.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);
                    }
                });

                final StreamHelper.StreamSources streamSources;
                if (videoItem.getStreamSources() != null) {
                    streamSources = videoItem.getStreamSources();
                } else {
                    // загрузить списки потоков видео и аудио
                    streamSources = StreamHelper.fetchStreams(context, videoItem);
                    StreamHelper.sortVideoStreamsDefault(streamSources.getVideoStreams());
                    StreamHelper.sortAudioStreamsDefault(streamSources.getAudioStreams());
                }

                if (streamSources.getVideoStreams().isEmpty() &&
                        streamSources.getAudioStreams().isEmpty()) {
                    if (!streamSources.problems.isEmpty()) {
                        // нет ни видео, ни аудио потоков, при этом список проблем не пустой
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                fetchStreamsProgress.setVisibility(View.GONE);
                                errorView.setVisibility(View.VISIBLE);
                                videoItemStreamsView.setVisibility(View.GONE);
                                dlg1.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);

                                fetchErrorTxt.setText(context.getString(R.string.error_loading_streams_for_video));
                                fetchErrorDetailsTxt.setText(streamSources.problems.get(0).getMessage());
                            }
                        });
                    } else {
                        // нет ни видео, ни аудио потоков, но и явных проблем в списке проблем нет
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                fetchStreamsProgress.setVisibility(View.GONE);
                                errorView.setVisibility(View.GONE);
                                videoItemStreamsView.setVisibility(View.GONE);

                                fetchErrorTxt.setText(context.getString(R.string.no_streams_for_video_item));
                            }
                        });
                    }
                } else {
                    // нашли потоки видео или аудио или и то и другое
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            fetchStreamsProgress.setVisibility(View.GONE);
                            errorView.setVisibility(View.GONE);
                            videoItemStreamsView.setVisibility(View.VISIBLE);

                            final StreamInfoArrayAdapter videoStreamsAdapter = new StreamInfoArrayAdapter(
                                    context, streamSources.getVideoStreams());
                            videoStreamSpinner.setAdapter(videoStreamsAdapter);
                            if (videoItem.getPlaybackStreams() != null) {
                                videoStreamSpinner.setSelection(videoStreamsAdapter.indexOf(
                                        videoItem.getPlaybackStreams().getVideoStream()));
                            }

                            final StreamInfoArrayAdapter audioStreamsAdapter = new StreamInfoArrayAdapter(
                                    context, streamSources.getAudioStreams());
                            audioStreamSpinner.setAdapter(audioStreamsAdapter);
                            if (videoItem.getPlaybackStreams() != null) {
                                audioStreamSpinner.setSelection(audioStreamsAdapter.indexOf(
                                        videoItem.getPlaybackStreams().getAudioStream()));
                            }

                            dlg1.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
                            dlg1.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(android.R.string.yes),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            if (callback != null) {
                                                callback.onStreamsSelected(
                                                        (StreamHelper.StreamInfo) videoStreamSpinner.getSelectedItem(),
                                                        (StreamHelper.StreamInfo) audioStreamSpinner.getSelectedItem());
                                            }
                                        }
                                    });
                        }
                    });
                }
            }
        }).start();
    }
}
