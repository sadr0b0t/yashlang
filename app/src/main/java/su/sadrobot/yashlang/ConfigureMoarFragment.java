package su.sadrobot.yashlang;

/*
 * Copyright (C) Anton Moiseev 2023 <github.com/sadr0b0t>
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
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import su.sadrobot.yashlang.service.PlayerService;


public class ConfigureMoarFragment extends Fragment {

    private Switch offlineModeSwitch;

    private Switch backgroundPlaybackOnSwitch;
    private Switch pauseOnHideSwitch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_configure_moar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        offlineModeSwitch = view.findViewById(R.id.offline_mode_switch);
        backgroundPlaybackOnSwitch = view.findViewById(R.id.background_playback_on_switch);
        pauseOnHideSwitch = view.findViewById(R.id.pause_on_hide_switch);

        offlineModeSwitch.setChecked(ConfigOptions.getOfflineModeOn(
                ConfigureMoarFragment.this.getContext()));
        offlineModeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ConfigOptions.setOfflineModeOn(ConfigureMoarFragment.this.getContext(), isChecked);
            }
        });

        backgroundPlaybackOnSwitch.setChecked(ConfigOptions.getBackgroundPlaybackOn(
                ConfigureMoarFragment.this.getContext()));
        backgroundPlaybackOnSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ConfigOptions.setBackgroundPlaybackOn(ConfigureMoarFragment.this.getContext(), isChecked);
                updateControlsStates();

                if (!isChecked) {
                    PlayerService.cmdStop(ConfigureMoarFragment.this.getContext());
                }
            }
        });

        pauseOnHideSwitch.setChecked(ConfigOptions.getPauseOnHide(
                ConfigureMoarFragment.this.getContext()));
        pauseOnHideSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ConfigOptions.setPauseOnHide(ConfigureMoarFragment.this.getContext(), isChecked);
            }
        });

        updateControlsStates();
    }

    private void updateControlsStates() {
        if (backgroundPlaybackOnSwitch.isChecked()) {
            pauseOnHideSwitch.setEnabled(true);
        } else {
            pauseOnHideSwitch.setEnabled(false);
        }
    }
}
