package su.sadrobot.yashlang.view;

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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.controller.StreamHelper;
import su.sadrobot.yashlang.model.StreamCache;

public class StreamInfoArrayAdapter extends BaseAdapter {
    // https://developer.android.com/reference/android/widget/BaseAdapter

    private final Context context;
    private final List<StreamHelper.StreamInfo> streams;

    public StreamInfoArrayAdapter(final Context context, final List<StreamHelper.StreamInfo> streams) {
        this.context = context;
        this.streams = streams;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        final View view = convertView != null ?
                convertView :
                LayoutInflater.from(context).inflate(android.R.layout.simple_spinner_item, parent, false);
        final TextView textTxt = view.findViewById(android.R.id.text1);

        if (position == 0) {
            textTxt.setText(context.getString(R.string.stream_none).toUpperCase());
        } else {
            final StreamHelper.StreamInfo stream = streams.get(position - 1);

            final String streamInfoStr = stream.getResolution() +
                    (stream.getQuality() != null ? " (" + stream.getQuality() + ") " : " ") +
                    stream.getFormatName() +
                    (!stream.isOnline() ? " [" + context.getString(R.string.icon_offline) + " " +
                            context.getString(R.string.offline).toUpperCase() + "]" : "") +
                    (stream.getStreamType() == StreamCache.StreamType.BOTH ?
                            " [" + StreamCache.StreamType.VIDEO.name() + "+" + StreamCache.StreamType.AUDIO + "]" : "");
            textTxt.setText(streamInfoStr);
        }

        return view;
    }

    @Override
    public View getDropDownView(final int position, final View convertView, final ViewGroup parent) {
        final View view = convertView != null ?
                convertView :
                LayoutInflater.from(context).inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
        final TextView textTxt = view.findViewById(android.R.id.text1);

        if (position == 0) {
            textTxt.setText(context.getString(R.string.stream_none).toUpperCase());
        } else {
            final StreamHelper.StreamInfo stream = streams.get(position - 1);

            final String streamInfoStr = stream.getResolution() +
                    (stream.getQuality() != null ? " (" + stream.getQuality() + ") " : " ") +
                    stream.getFormatName() +
                    (!stream.isOnline() ? " [" + context.getString(R.string.icon_offline) + " " +
                            context.getString(R.string.offline).toUpperCase() + "]" : "") +
                    (stream.getStreamType() == StreamCache.StreamType.BOTH ?
                            " [" + StreamCache.StreamType.VIDEO.name() + "+" + StreamCache.StreamType.AUDIO + "]" : "");
            textTxt.setText(streamInfoStr);
        }

        return view;
    }

    @Override
    public int getCount() {
        return streams.size() + 1;
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @Override
    public Object getItem(final int position) {
        return position == 0 ? null : streams.get(position - 1);
    }

    /**
     * Первый элемент в списке всегда NONE, поэтому если поток null или не найден, вернет индекс 0.
     * @param stream
     * @return
     */
    public int indexOf(final StreamHelper.StreamInfo stream) {
        int position = 0;
        if (stream != null) {
            // position = streams.indexOf(stream);
            // здесь не используем, т.к. хотим найти поток по значению, сам объект может быть
            // не тот, который добавлен в список
            for (int i = 0; i < streams.size(); i++) {
                final StreamHelper.StreamInfo stream2 = streams.get(i);
                if (stream.isOnline() == stream2.isOnline() &&
                        stream.getStreamType() == stream2.getStreamType() &&
                        stream.getFormatName().equals(stream2.getFormatName()) &&
                        stream.getResolution().equals(stream2.getResolution())) {
                    position = i + 1;
                    break;
                }
            }
        }
        return position;
    }
}
