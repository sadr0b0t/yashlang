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

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import su.sadrobot.yashlang.model.Profile;
import su.sadrobot.yashlang.model.ProfileNfcTags;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.util.NfcUtil;
import su.sadrobot.yashlang.view.NfcTagsArrayAdapter;
import su.sadrobot.yashlang.view.OnListItemClickListener;

/**
 *
 */
public class ConfigureProfileNfcTagsFragment extends Fragment {

    //
    private TextView nfcSatusTxt;
    private Button gotoNfcSystemSettingsBtn;
    private TextView addNfcTagTxt;
    private RecyclerView nfcTagList;

    // Экран с пустым списком
    private View emptyView;

    private final Handler handler = new Handler();
    // достаточно одного фонового потока
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private long profileId;
    private List<ProfileNfcTags> nfcTags = new ArrayList<>();

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private IntentFilter intentFiltersArray[];

    public ConfigureProfileNfcTagsFragment(final long profileId) {
        this.profileId = profileId;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_configure_profile_nfc_tags, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        nfcSatusTxt = view.findViewById(R.id.nfc_status_txt);
        gotoNfcSystemSettingsBtn = view.findViewById(R.id.goto_nfc_system_settings_btn);
        addNfcTagTxt = view.findViewById(R.id.add_nfc_tag_txt);
        nfcTagList = view.findViewById(R.id.nfc_tag_list);

        emptyView = view.findViewById(R.id.empty_view);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(ConfigureProfileNfcTagsFragment.this.getContext());
        nfcTagList.setLayoutManager(linearLayoutManager);
        nfcTagList.addItemDecoration(new DividerItemDecoration(this.getContext(), DividerItemDecoration.VERTICAL));
        nfcTagList.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                // чтобы картинки и текст не сбивались в кучку при быстрой промотке
                // см: https://github.com/sadr0b0t/yashlang/issues/129
                return true;
            }
        });

        gotoNfcSystemSettingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // https://github.com/marc136/tonuino-nfc-tools/blob/main/app/src/main/java/de/mw136/tonuino/ui/MainActivity.kt
                startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
            }
        });

        // NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this.getContext());
        if (nfcAdapter != null) {
            // nfcAdapter здесь не нужен, но без nfcAdapter это делать незачем
            final Intent intent = new Intent(this.getContext(), this.getActivity().getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            pendingIntent = PendingIntent.getActivity(this.getContext(), 0, intent, PendingIntent.FLAG_MUTABLE);
            intentFiltersArray = NfcUtil.createIntentFilterArray();
        }

        loadProfile();
    }

    @Override
    public void onResume() {
        super.onResume();

        // NFC
        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(this.getActivity(), pendingIntent, intentFiltersArray, NfcUtil.NFC_TECH_LISTS_ARRAY);
        }
        updateNfcStatus();
    }

    @Override
    public void onPause() {
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this.getActivity());
        }
        super.onPause();
    }

    protected void onNewIntent(final Intent intent) {
        // Обрабатывает интент, который должна переслать сюда родительская активити,
        // ожидаем интент для NFC
        if (intent.getAction() == NfcAdapter.ACTION_NDEF_DISCOVERED || intent.getAction() == NfcAdapter.ACTION_TECH_DISCOVERED) {
            final Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            final String tagIdStr = NfcUtil.nfcTagIdToString(tag.getId());

            boolean containsTag = false;
            for (final ProfileNfcTags _tag: nfcTags) {
                if (_tag.getNfcTagId().equals(tagIdStr)) {
                    containsTag = true;
                    break;
                }
            }
            if (!containsTag) {
                nfcTags.add(new ProfileNfcTags(profileId, tagIdStr, ""));
                setupNfcTagListArrayAdapter();
            }
        }
    }

    public List<ProfileNfcTags> getNfcTags() {
        return nfcTags;
    }

    private void updateNfcStatus() {
        if (nfcAdapter == null) {
            nfcSatusTxt.setText(R.string.nfc_status_not_supported);
            gotoNfcSystemSettingsBtn.setVisibility(View.GONE);
            addNfcTagTxt.setVisibility(View.GONE);
        } else if (!nfcAdapter.isEnabled()) {
            nfcSatusTxt.setText(R.string.nfc_status_disabled);
            gotoNfcSystemSettingsBtn.setVisibility(View.VISIBLE);
            addNfcTagTxt.setVisibility(View.GONE);
        } else {
            nfcSatusTxt.setText(R.string.nfc_status_enabled);
            gotoNfcSystemSettingsBtn.setVisibility(View.GONE);
            addNfcTagTxt.setVisibility(View.VISIBLE);
        }
    }

    private void updateControlsVisibility() {
        final boolean listIsEmpty = nfcTagList.getAdapter() == null ||
                nfcTagList.getAdapter().getItemCount() == 0;

        if (listIsEmpty) {
            emptyView.setVisibility(View.VISIBLE);
            nfcTagList.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            nfcTagList.setVisibility(View.VISIBLE);
        }
    }

    private void loadProfile() {
        // загрузка профиля из базы должна быть в фоне
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (profileId == Profile.ID_NONE) {
                    // профиля нет в базе данных
                    nfcTags.clear();
                } else {
                    // профиль есть в базе данных
                    final VideoDatabase videodb = VideoDatabase.getDbInstance(ConfigureProfileNfcTagsFragment.this.getContext());
                    nfcTags.clear();
                    nfcTags.addAll(videodb.profileDao().getProfileNfcTags(profileId));
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        setupNfcTagListArrayAdapter();
                    }
                });
            }
        });
    }

    private void setupNfcTagListArrayAdapter() {
        final List<ProfileNfcTags> items = new ArrayList<>();

        // профили из базы данных
        items.addAll(nfcTags);

        final NfcTagsArrayAdapter adapter = new NfcTagsArrayAdapter(items,
                new OnListItemClickListener<ProfileNfcTags>() {
                    @Override
                    public void onItemClick(final View view, final int position, final ProfileNfcTags nfcTag) {
                        final PopupMenu popup = new PopupMenu(ConfigureProfileNfcTagsFragment.this.getContext(),
                                view);
                        popup.getMenuInflater().inflate(R.menu.profile_nfc_tags_actions, popup.getMenu());

                        popup.setOnMenuItemClickListener(
                                new PopupMenu.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(final MenuItem item) {
                                        switch (item.getItemId()) {
                                            case R.id.action_edit_label: {
                                                final EditText nfcLabelEdit = new EditText(ConfigureProfileNfcTagsFragment.this.getContext());
                                                nfcLabelEdit.setHint(R.string.nfc_tag_label);
                                                nfcLabelEdit.setText(nfcTag.getNfcTagLabel());

                                                new AlertDialog.Builder(ConfigureProfileNfcTagsFragment.this.getContext())
                                                        .setTitle(getString(R.string.edit_nfc_tag_label_title).replace("%s", nfcTag.getNfcTagId()))
                                                        .setView(nfcLabelEdit)
                                                        .setIcon(android.R.drawable.ic_dialog_alert)
                                                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                                            public void onClick(DialogInterface dialog, int whichButton) {
                                                                nfcTag.setNfcTagLabel(nfcLabelEdit.getText().toString());
                                                                setupNfcTagListArrayAdapter();
                                                            }
                                                        })
                                                        .setNegativeButton(android.R.string.no, null).show();
                                                break;
                                            }
                                            case R.id.action_unbind: {
                                                new AlertDialog.Builder(ConfigureProfileNfcTagsFragment.this.getContext())
                                                        .setTitle(getString(R.string.unbind_nfc_tag_title).replace("%s", nfcTag.getNfcTagId()))
                                                        .setMessage(nfcTag.getNfcTagLabel())
                                                        .setIcon(android.R.drawable.ic_dialog_alert)
                                                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                                            public void onClick(DialogInterface dialog, int whichButton) {
                                                                nfcTags.remove(nfcTag);
                                                                setupNfcTagListArrayAdapter();
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
                    public boolean onItemLongClick(final View view, final int position, final ProfileNfcTags nfcTag) {
                        return false;
                    }
                });

        nfcTagList.setAdapter(adapter);
        updateControlsVisibility();
    }
}
