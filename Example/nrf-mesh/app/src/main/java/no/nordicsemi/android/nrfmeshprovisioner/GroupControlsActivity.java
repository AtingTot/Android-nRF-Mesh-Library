/*
 * Copyright (c) 2018, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.nrfmeshprovisioner;

import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import butterknife.ButterKnife;
import no.nordicsemi.android.meshprovisioner.Group;
import no.nordicsemi.android.meshprovisioner.MeshNetwork;
import no.nordicsemi.android.meshprovisioner.models.ConfigurationServerModel;
import no.nordicsemi.android.meshprovisioner.models.GenericLevelServerModel;
import no.nordicsemi.android.meshprovisioner.models.GenericOnOffServerModel;
import no.nordicsemi.android.meshprovisioner.models.SigModelParser;
import no.nordicsemi.android.meshprovisioner.models.VendorModel;
import no.nordicsemi.android.meshprovisioner.transport.ApplicationKey;
import no.nordicsemi.android.meshprovisioner.transport.Element;
import no.nordicsemi.android.meshprovisioner.transport.GenericLevelSetUnacknowledged;
import no.nordicsemi.android.meshprovisioner.transport.GenericOnOffSetUnacknowledged;
import no.nordicsemi.android.meshprovisioner.transport.MeshMessage;
import no.nordicsemi.android.meshprovisioner.transport.MeshModel;
import no.nordicsemi.android.meshprovisioner.transport.ProvisionedMeshNode;
import no.nordicsemi.android.meshprovisioner.transport.VendorModelMessageAcked;
import no.nordicsemi.android.meshprovisioner.transport.VendorModelMessageStatus;
import no.nordicsemi.android.meshprovisioner.transport.VendorModelMessageUnacked;
import no.nordicsemi.android.meshprovisioner.utils.MeshAddress;
import no.nordicsemi.android.meshprovisioner.utils.MeshParserUtils;
import no.nordicsemi.android.nrfmeshprovisioner.adapter.SubGroupAdapter;
import no.nordicsemi.android.nrfmeshprovisioner.di.Injectable;
import no.nordicsemi.android.nrfmeshprovisioner.utils.Utils;
import no.nordicsemi.android.nrfmeshprovisioner.viewmodels.GroupControlsViewModel;

public class GroupControlsActivity extends AppCompatActivity implements Injectable,
        SubGroupAdapter.OnItemClickListener,
        BottomSheetOnOffDialogFragment.BottomSheetOnOffListener,
        BottomSheetLevelDialogFragment.BottomSheetLevelListener,
        BottomSheetVendorDialogFragment.BottomSheetVendorModelControlsListener,
        BottomSheetDetailsDialogFragment.BottomSheetDetailsListener {

    private static final String ON_OFF_FRAGMENT = "ON_OFF_FRAGMENT";
    private static final String LEVEL_FRAGMENT = "LEVEL_FRAGMENT";
    private static final String VENDOR_FRAGMENT = "VENDOR_FRAGMENT";
    private static final String DETAILS_FRAGMENT = "DETAILS_FRAGMENT";

    @Inject
    ViewModelProvider.Factory mViewModelFactory;

    private GroupControlsViewModel mViewModel;
    private SubGroupAdapter groupAdapter;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config_groups);
        ButterKnife.bind(this);
        mViewModel = ViewModelProviders.of(this, mViewModelFactory).get(GroupControlsViewModel.class);

        final Toolbar toolbar = findViewById(R.id.toolbar_info);
        setSupportActionBar(toolbar);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        final View noModelsConfigured = findViewById(R.id.no_models_subscribed);
        final View noAppKeysBound = findViewById(R.id.no_app_keys);

        final RecyclerView recyclerViewSubGroups = findViewById(R.id.recycler_view_grouped_models);
        recyclerViewSubGroups.setLayoutManager(new LinearLayoutManager(this));
        groupAdapter = new SubGroupAdapter(this,
                mViewModel.getMeshManagerApi().getMeshNetwork(),
                mViewModel.getSelectedGroup(),
                mViewModel.isConnectedToProxy());
        groupAdapter.setOnItemClickListener(this);
        recyclerViewSubGroups.setAdapter(groupAdapter);

        mViewModel.getSelectedGroup().observe(this, group -> {
            if (group != null) {
                getSupportActionBar().setTitle(group.getName());
                getSupportActionBar().setSubtitle(MeshAddress.formatAddress(group.getGroupAddress(), true));
            }
        });

        mViewModel.getMeshNetworkLiveData().observe(this, meshNetworkLiveData -> {
            if (groupAdapter.getModelCount() > 0) {
                noModelsConfigured.setVisibility(View.INVISIBLE);
                if (groupAdapter.getItemCount() > 0) {
                    noAppKeysBound.setVisibility(View.INVISIBLE);
                } else {
                    noAppKeysBound.setVisibility(View.VISIBLE);
                }
            } else {
                noModelsConfigured.setVisibility(View.VISIBLE);
                noAppKeysBound.setVisibility(View.INVISIBLE);
            }
            groupAdapter.updateAdapterData();
        });

        mViewModel.getSelectedModel().observe(this, meshModel -> {
            groupAdapter.updateAdapterData();
            final BottomSheetDetailsDialogFragment fragment = (BottomSheetDetailsDialogFragment) getSupportFragmentManager().findFragmentByTag(DETAILS_FRAGMENT);
            if (fragment != null) {
                final Group group = mViewModel.getSelectedGroup().getValue();
                final MeshNetwork meshNetwork = mViewModel.getMeshManagerApi().getMeshNetwork();
                final ArrayList<Element> elements = new ArrayList<>(meshNetwork.getElements(group));
                fragment.updateAdapter(group, elements);
            }
        });

        mViewModel.getNrfMeshRepository().getMeshMessageLiveData().observe(this, meshMessage -> {
            if (meshMessage instanceof VendorModelMessageStatus) {
                final VendorModelMessageStatus status = (VendorModelMessageStatus) meshMessage;
                final BottomSheetVendorDialogFragment fragment = (BottomSheetVendorDialogFragment) getSupportFragmentManager().findFragmentByTag(VENDOR_FRAGMENT);
                if (fragment != null)
                    fragment.setReceivedMessage(status.getAccessPayload());
            }
        });

        mViewModel.isConnectedToProxy().observe(this, aBoolean -> invalidateOptionsMenu());

    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (mViewModel.getProvisionedNodes().getValue() != null && !mViewModel.getProvisionedNodes().getValue().isEmpty()) {
            final Boolean isConnectedToNetwork = mViewModel.isConnectedToProxy().getValue();
            if (isConnectedToNetwork != null && isConnectedToNetwork) {
                getMenuInflater().inflate(R.menu.menu_group_controls_disconnect, menu);
            } else {
                getMenuInflater().inflate(R.menu.menu_group_controls_connect, menu);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_edit:
                editGroup();
                break;
            case R.id.action_connect:
                final Intent intent = new Intent(this, ScannerActivity.class);
                intent.putExtra(Utils.EXTRA_DATA_PROVISIONING_SERVICE, false);
                startActivityForResult(intent, Utils.CONNECT_TO_NETWORK);
                return true;
            case R.id.action_disconnect:
                mViewModel.disconnect();
                return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onSubGroupItemClick(final int appKeyIndex, final int modelId) {
        if (MeshParserUtils.isVendorModel(modelId)) {
            final BottomSheetVendorDialogFragment onOffFragment = BottomSheetVendorDialogFragment.getInstance(modelId, appKeyIndex);
            onOffFragment.show(getSupportFragmentManager(), VENDOR_FRAGMENT);
        } else {
            switch (modelId) {
                case SigModelParser.GENERIC_ON_OFF_SERVER:
                    final BottomSheetOnOffDialogFragment onOffFragment = BottomSheetOnOffDialogFragment.getInstance(appKeyIndex);
                    onOffFragment.show(getSupportFragmentManager(), ON_OFF_FRAGMENT);
                    break;
                case SigModelParser.GENERIC_LEVEL_SERVER:
                    final BottomSheetLevelDialogFragment levelFragment = BottomSheetLevelDialogFragment.getInstance(appKeyIndex);
                    levelFragment.show(getSupportFragmentManager(), LEVEL_FRAGMENT);
                    break;
            }
        }
    }

    @Override
    public void toggle(final int appKeyIndex, final int modelId, final boolean isChecked) {
        final Group group = mViewModel.getSelectedGroup().getValue();
        if (group == null)
            return;

        final MeshMessage meshMessage;
        final ApplicationKey applicationKey = mViewModel.getMeshManagerApi().getMeshNetwork().getAppKey(appKeyIndex);
        final int tid = mViewModel.getMeshManagerApi().getMeshNetwork().getSelectedProvisioner().getSequenceNumber();
        switch (modelId) {
            case SigModelParser.GENERIC_ON_OFF_SERVER:
                meshMessage = new GenericOnOffSetUnacknowledged(applicationKey.getKey(), isChecked, tid);
                mViewModel.getMeshManagerApi().sendMeshMessage(group.getGroupAddress(), meshMessage);
                break;
            case SigModelParser.GENERIC_LEVEL_SERVER:
                meshMessage = new GenericLevelSetUnacknowledged(applicationKey.getKey(), isChecked ? 32767 : -32768, tid);
                mViewModel.getMeshManagerApi().sendMeshMessage(group.getGroupAddress(), meshMessage);
                break;
        }

    }

    @Override
    public void toggle(final int keyIndex, final boolean state, final int transitionSteps, final int transitionStepResolution, final int delay) {
        final Group group = mViewModel.getSelectedGroup().getValue();
        if (group == null)
            return;

        final ApplicationKey applicationKey = mViewModel.getMeshManagerApi().getMeshNetwork().getAppKey(keyIndex);
        final int tid = mViewModel.getMeshManagerApi().getMeshNetwork().getSelectedProvisioner().getSequenceNumber();
        final MeshMessage meshMessage = new GenericOnOffSetUnacknowledged(applicationKey.getKey(), state, tid, transitionSteps, transitionStepResolution, delay);
        mViewModel.getMeshManagerApi().sendMeshMessage(group.getGroupAddress(), meshMessage);
    }

    @Override
    public void toggleLevel(final int keyIndex, final int level, final int transitionSteps, final int transitionStepResolution, final int delay) {
        final Group group = mViewModel.getSelectedGroup().getValue();
        if (group == null)
            return;

        final ApplicationKey applicationKey = mViewModel.getMeshManagerApi().getMeshNetwork().getAppKey(keyIndex);
        final int tid = mViewModel.getMeshManagerApi().getMeshNetwork().getSelectedProvisioner().getSequenceNumber();
        final MeshMessage meshMessage = new GenericLevelSetUnacknowledged(applicationKey.getKey(), transitionSteps, transitionStepResolution, delay, level, tid);
        mViewModel.getMeshManagerApi().sendMeshMessage(group.getGroupAddress(), meshMessage);
    }

    private void editGroup() {
        final Group group = mViewModel.getSelectedGroup().getValue();
        final MeshNetwork meshNetwork = mViewModel.getMeshManagerApi().getMeshNetwork();
        final ArrayList<Element> elements = new ArrayList<>(meshNetwork.getElements(group));
        final BottomSheetDetailsDialogFragment onOffFragment = BottomSheetDetailsDialogFragment.getInstance(group, elements);
        onOffFragment.show(getSupportFragmentManager(), DETAILS_FRAGMENT);
    }

    @Override
    public void editModelItem(@NonNull final Element element, @NonNull final MeshModel model) {
        final Boolean isConnectedToNetwork = mViewModel.isConnectedToProxy().getValue();
        if (isConnectedToNetwork != null && isConnectedToNetwork) {
            final ProvisionedMeshNode node = mViewModel.getMeshManagerApi().getMeshNetwork().getProvisionedNode(element.getElementAddress());
            if (node != null) {
                mViewModel.setSelectedMeshNode(node);
                mViewModel.setSelectedElement(element);
                mViewModel.setSelectedModel(model);
                startActivity(model);
            }
        } else {
            Toast.makeText(this, R.string.disconnected_network_rationale, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onGroupNameChanged(@NonNull final Group group) {
        mViewModel.getMeshManagerApi().getMeshNetwork().updateGroup(group);
    }

    /**
     * Start activity based on the type of the model
     *
     * <p> This way we can seperate the ui logic for different activities</p>
     *
     * @param model model
     */
    private void startActivity(final MeshModel model) {
        final Intent intent;
        if (model instanceof ConfigurationServerModel) {
            intent = new Intent(this, ConfigurationServerActivity.class);
        } else if (model instanceof GenericOnOffServerModel) {
            intent = new Intent(this, GenericOnOffServerActivity.class);
        } else if (model instanceof GenericLevelServerModel) {
            intent = new Intent(this, GenericLevelServerActivity.class);
        } else if (model instanceof VendorModel) {
            intent = new Intent(this, VendorModelActivity.class);
        } else {
            intent = new Intent(this, ModelConfigurationActivity.class);
        }
        startActivity(intent);
    }

    @Override
    public void sendVendorModelMessage(final int modelId, final int keyIndex, final int opCode, final byte[] parameters, final boolean acknowledged) {
        final Group group = mViewModel.getSelectedGroup().getValue();
        if (group == null)
            return;

        final VendorModel model = getModel(modelId, keyIndex);
        if (model == null)
            return;

        final ApplicationKey appKey = mViewModel.getMeshManagerApi().getMeshNetwork().getAppKey(keyIndex);
        final MeshMessage message;
        if (acknowledged) {
            message = new VendorModelMessageAcked(appKey.getKey(), modelId, model.getCompanyIdentifier(), opCode, parameters);
        } else {
            message = new VendorModelMessageUnacked(appKey.getKey(), modelId, model.getCompanyIdentifier(), opCode, parameters);
        }
        mViewModel.getMeshManagerApi().sendMeshMessage(group.getGroupAddress(), message);
    }

    private VendorModel getModel(final int modelId, final int appKeyIndex) {
        final List<MeshModel> models = groupAdapter.getModels();
        for (MeshModel model : models) {
            if (modelId == model.getModelId()) {
                if (model.getBoundAppKeyIndexes().contains(appKeyIndex)) {
                    return (VendorModel) model;
                }
            }
        }
        return null;

    }
}
