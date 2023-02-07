package su.sadrobot.yashlang;

/*
 * Copyright (C) Anton Moiseev 2021 <github.com/sadr0b0t>
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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;


/**
 *
 */
public class ConfigureVideoQualityFragment extends Fragment {

    private RadioButton vidStreamSelectMaxResRadio;
    private RadioButton vidStreamSelectMinResRadio;
    private RadioButton vidStreamSelectCustomRadio;
    private RadioButton vidStreamSelectLastChosenRadio;


    private TextView vidStreamSelectCustomPreferTxt;
    private Spinner vidStreamCustomResSpinner;
    private RadioButton vidStreamSelectCustomPreferHigherResRadio;
    private RadioButton vidStreamSelectCustomPreferLowerResRadio;

    private TextView vidStreamSelectLastPreferTxt;
    private TextView vidStreamLastResTxt;
    private RadioButton vidStreamSelectLastPreferHigherResRadio;
    private RadioButton vidStreamSelectLastPreferLowerResRadio;

    private Switch vidStreamSelectOfflineSwitch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_configure_video_quality, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        vidStreamSelectMaxResRadio = view.findViewById(R.id.vid_stream_select_max_res_radio);
        vidStreamSelectMinResRadio = view.findViewById(R.id.vid_stream_select_min_res_radio);
        vidStreamSelectCustomRadio = view.findViewById(R.id.vid_stream_select_custom_radio);
        vidStreamSelectLastChosenRadio = view.findViewById(R.id.vid_stream_select_last_chosen_radio);

        vidStreamSelectCustomPreferTxt = view.findViewById(R.id.vid_stream_select_custom_prefer_txt);
        vidStreamCustomResSpinner = view.findViewById(R.id.vid_stream_custom_res_spinner);
        vidStreamSelectCustomPreferHigherResRadio = view.findViewById(R.id.vid_stream_select_custom_prefer_higher_res_radio);
        vidStreamSelectCustomPreferLowerResRadio = view.findViewById(R.id.vid_stream_select_custom_prefer_lower_res_radio);

        vidStreamSelectLastPreferTxt = view.findViewById(R.id.vid_stream_select_last_prefer_txt);
        vidStreamLastResTxt = view.findViewById(R.id.vid_stream_last_res_txt);
        vidStreamSelectLastPreferHigherResRadio = view.findViewById(R.id.vid_stream_select_last_prefer_higher_res_radio);
        vidStreamSelectLastPreferLowerResRadio = view.findViewById(R.id.vid_stream_select_last_prefer_lower_res_radio);

        vidStreamSelectOfflineSwitch = view.findViewById(R.id.vid_stream_select_offline_switch);

        vidStreamSelectMaxResRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    ConfigOptions.setVideoStreamSelectStrategy(
                            ConfigureVideoQualityFragment.this.getContext(),
                            ConfigOptions.VideoStreamSelectStrategy.MAX_RES);
                }
                updateControlsStates();
            }
        });

        vidStreamSelectMinResRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    ConfigOptions.setVideoStreamSelectStrategy(
                            ConfigureVideoQualityFragment.this.getContext(),
                            ConfigOptions.VideoStreamSelectStrategy.MIN_RES);
                }
                updateControlsStates();
            }
        });

        vidStreamSelectCustomRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    ConfigOptions.setVideoStreamSelectStrategy(
                            ConfigureVideoQualityFragment.this.getContext(),
                            ConfigOptions.VideoStreamSelectStrategy.CUSTOM_RES);
                }
                updateControlsStates();
            }
        });

        vidStreamSelectLastChosenRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    ConfigOptions.setVideoStreamSelectStrategy(
                            ConfigureVideoQualityFragment.this.getContext(),
                            ConfigOptions.VideoStreamSelectStrategy.LAST_CHOSEN);
                }
                updateControlsStates();
            }
        });

        vidStreamSelectCustomPreferHigherResRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    ConfigOptions.setVideoStreamSelectCustomPreferRes(
                            ConfigureVideoQualityFragment.this.getContext(),
                            ConfigOptions.VideoStreamSelectPreferRes.HIGHER_RES);
                }
            }
        });

        vidStreamSelectCustomPreferLowerResRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    ConfigOptions.setVideoStreamSelectCustomPreferRes(
                            ConfigureVideoQualityFragment.this.getContext(),
                            ConfigOptions.VideoStreamSelectPreferRes.LOWER_RES);
                }
            }
        });

        vidStreamSelectLastPreferHigherResRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    ConfigOptions.setVideoStreamSelectLastPreferRes(
                            ConfigureVideoQualityFragment.this.getContext(),
                            ConfigOptions.VideoStreamSelectPreferRes.HIGHER_RES);
                }
            }
        });

        vidStreamSelectLastPreferLowerResRadio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    ConfigOptions.setVideoStreamSelectLastPreferRes(
                            ConfigureVideoQualityFragment.this.getContext(),
                            ConfigOptions.VideoStreamSelectPreferRes.LOWER_RES);
                }
            }
        });

        vidStreamSelectOfflineSwitch.setChecked(ConfigOptions.getVideoStreamSelectOffline(
                ConfigureVideoQualityFragment.this.getContext()));
        vidStreamSelectOfflineSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ConfigOptions.setVideoStreamSelectOffline(
                        ConfigureVideoQualityFragment.this.getContext(),
                        isChecked);
            }
        });

        final ArrayAdapter<String> videoResAdapter = new ArrayAdapter<>(ConfigureVideoQualityFragment.this.getContext(),
                android.R.layout.simple_spinner_item, ConfigOptions.VIDEO_RESOLUTIONS);
        videoResAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vidStreamCustomResSpinner.setAdapter(videoResAdapter);

        // найти позицию выбранного эллемента в массиве
        final String savedCustomResolution = ConfigOptions.getVideoStreamCustomRes(getContext());
        int selResPos = -1;
        for (int i = 0; i < ConfigOptions.VIDEO_RESOLUTIONS.length; i++) {
            if (ConfigOptions.VIDEO_RESOLUTIONS[i].equals(savedCustomResolution)) {
                selResPos = i;
                break;
            }
        }

        // перестраховка, но ладно
        if (selResPos == -1) {
            for (int i = 0; i < ConfigOptions.VIDEO_RESOLUTIONS.length; i++) {
                if (ConfigOptions.VIDEO_RESOLUTIONS[i].equals(ConfigOptions.DEFAULT_VIDEO_RESOLUTION)) {
                    selResPos = i;
                    break;
                }
            }
            // совсем уже перестраховка
            if (selResPos == -1) {
                selResPos = 0;
            }
        }

        vidStreamCustomResSpinner.setSelection(selResPos);
        vidStreamCustomResSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ConfigOptions.setVideoStreamCustomRes(ConfigureVideoQualityFragment.this.getContext(), videoResAdapter.getItem(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        switch (ConfigOptions.getVideoStreamSelectStrategy(this.getContext())) {
            case MAX_RES:
                vidStreamSelectMaxResRadio.setChecked(true);
                break;
            case MIN_RES:
                vidStreamSelectMinResRadio.setChecked(true);
                break;
            case CUSTOM_RES:
                vidStreamSelectCustomRadio.setChecked(true);
                break;
            case LAST_CHOSEN:
                vidStreamSelectLastChosenRadio.setChecked(true);
                break;
        }

        switch (ConfigOptions.getVideoStreamSelectCustomPreferRes(this.getContext())) {
            case HIGHER_RES:
                vidStreamSelectCustomPreferHigherResRadio.setChecked(true);
                break;
            case LOWER_RES:
                vidStreamSelectCustomPreferLowerResRadio.setChecked(true);
                break;
        }

        switch (ConfigOptions.getVideoStreamSelectLastPreferRes(this.getContext())) {
            case HIGHER_RES:
                vidStreamSelectLastPreferHigherResRadio.setChecked(true);
                break;
            case LOWER_RES:
                vidStreamSelectLastPreferLowerResRadio.setChecked(true);
                break;
        }

        vidStreamLastResTxt.setText(ConfigOptions.getVideoStreamLastSelectedRes(this.getContext()));

        updateControlsStates();
    }

    private void updateControlsStates() {
        if (vidStreamSelectMaxResRadio.isChecked()) {
            vidStreamSelectCustomPreferTxt.setEnabled(false);
            vidStreamCustomResSpinner.setEnabled(false);
            vidStreamSelectCustomPreferHigherResRadio.setEnabled(false);
            vidStreamSelectCustomPreferLowerResRadio.setEnabled(false);

            vidStreamSelectLastPreferTxt.setEnabled(false);
            vidStreamLastResTxt.setEnabled(false);
            vidStreamSelectLastPreferHigherResRadio.setEnabled(false);
            vidStreamSelectLastPreferLowerResRadio.setEnabled(false);
        } else if (vidStreamSelectMinResRadio.isChecked()) {
            vidStreamSelectCustomPreferTxt.setEnabled(false);
            vidStreamCustomResSpinner.setEnabled(false);
            vidStreamSelectCustomPreferHigherResRadio.setEnabled(false);
            vidStreamSelectCustomPreferLowerResRadio.setEnabled(false);

            vidStreamSelectLastPreferTxt.setEnabled(false);
            vidStreamLastResTxt.setEnabled(false);
            vidStreamSelectLastPreferHigherResRadio.setEnabled(false);
            vidStreamSelectLastPreferLowerResRadio.setEnabled(false);
        } else if (vidStreamSelectCustomRadio.isChecked()) {
            vidStreamSelectCustomPreferTxt.setEnabled(true);
            vidStreamCustomResSpinner.setEnabled(true);
            vidStreamSelectCustomPreferHigherResRadio.setEnabled(true);
            vidStreamSelectCustomPreferLowerResRadio.setEnabled(true);

            vidStreamSelectLastPreferTxt.setEnabled(false);
            vidStreamLastResTxt.setEnabled(false);
            vidStreamSelectLastPreferHigherResRadio.setEnabled(false);
            vidStreamSelectLastPreferLowerResRadio.setEnabled(false);
        } else {//if(vidStreamSelectLastChosenRadio.isChecked()) {
            vidStreamSelectCustomPreferTxt.setEnabled(false);
            vidStreamCustomResSpinner.setEnabled(false);
            vidStreamSelectCustomPreferHigherResRadio.setEnabled(false);
            vidStreamSelectCustomPreferLowerResRadio.setEnabled(false);

            vidStreamSelectLastPreferTxt.setEnabled(true);
            vidStreamLastResTxt.setEnabled(true);
            vidStreamSelectLastPreferHigherResRadio.setEnabled(true);
            vidStreamSelectLastPreferLowerResRadio.setEnabled(true);
        }
    }
}
