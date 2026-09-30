package com.twoplay.pipedal;

import android.animation.ObjectAnimator;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.twoplay.pipedal.model.WebProbe;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import dagger.hilt.android.AndroidEntryPoint;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

@AndroidEntryPoint
public class ScannerFragment extends Fragment implements IpAddressDialogFragment.IpAddressDialogFragmentResult {

    private ConstraintLayout searchingView;
    private RecyclerView recyclerView;
    private MaterialToolbar appBar;
    private DeviceAdapter adapter;
    private ScannerViewModel viewModel;
    private ConstraintLayout errorView;
    private TextView errorTextView;
    private TextView captionView;
    private TextView searchingTextView;
    private MaterialButton wifiSettingsButton;
    MaterialButton scanButton;
    MaterialButton cancelButton;
    private MaterialButton ipAddressButton;


    private void showCancel(boolean show)
    {
        if (scanButton != null) {
            scanButton.setVisibility(show ? View.GONE: View.VISIBLE);
        }
        if (cancelButton != null)
        {
            cancelButton.setVisibility(show? View.VISIBLE: View.GONE);
        }
    }
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        setHasOptionsMenu(true);
        viewModel = new ViewModelProvider(this).get(ScannerViewModel.class);

        PackageManager packageManager = getActivity().getPackageManager();
        Intent wifiSettingsIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
        List<ResolveInfo> activities = packageManager.queryIntentActivities(wifiSettingsIntent, 0);
        boolean hasWifiSettingsHandler = !activities.isEmpty();

        View v = inflater.inflate(R.layout.scanner_fragment, container, false);
        this.captionView = (TextView) v.findViewById(R.id.caption);
        this.searchingView = (ConstraintLayout) v.findViewById(R.id.searching_panel);
        this.searchingTextView = (TextView) v.findViewById(R.id.searching_text);
        this.errorView = (ConstraintLayout) v.findViewById(R.id.error_panel);
        this.recyclerView = (RecyclerView) v.findViewById(R.id.recycler_view);
        this.appBar = (MaterialToolbar) v.findViewById(R.id.app_bar);
        this.errorTextView = (TextView) v.findViewById(R.id.error_text);
        wifiSettingsButton = (MaterialButton)v.findViewById(R.id.wifi_button);
        ipAddressButton = (MaterialButton)v.findViewById(R.id.ip_address_button);

        scanButton = v.findViewById(R.id.scan_again_button);
        cancelButton = v.findViewById(R.id.cancel_button);

        if (hasWifiSettingsHandler) {
            wifiSettingsButton.setOnClickListener((View vv) -> {
                Intent launchIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                getActivity().startActivity(launchIntent);

            });
        } else {
            wifiSettingsButton.setVisibility(View.GONE);
        }
        ipAddressButton.setOnClickListener((View vv) -> {
            IpAddressDialogFragment.execute(this,Preferences.getConnectionIpAddress(getActivity()));
        });

        scanButton.setOnClickListener((View vv) -> viewModel.restartScan());
        cancelButton.setOnClickListener((View vv) -> viewModel.stopScan());
        showCancel(false);
        MaterialButton helpButton = v.findViewById(R.id.help_button);
        helpButton.setOnClickListener((View v3) -> {
            HelpDialogFragment.execute(this);
        });

        appBar.inflateMenu(R.menu.scanner_menu);
        appBar.setOnMenuItemClickListener((MenuItem item) -> {
//            if (item.getItemId() == R.id.menu_refresh)
//            {
//                refreshDevices();
//                return true;
//            }
            return false;
        });
        appBar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp);
        appBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectAndFinish();
            }
        });


        return v;
    }

    private void disconnectAndFinish() {
        if (getActivity() != null) {
            viewModel.disconnect(() -> {
                if (getActivity() != null) {
                    getActivity().finish();
                }
            });
        }
    }

    private void refreshDevices() {
        viewModel.restartScan();
    }

    class DeviceViewHolder extends RecyclerView.ViewHolder {

        private final TextView textView;
        private final ImageView wifiIcon;

        ScannerViewModel.ScannerDeviceUiState scannerDevice;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);

            itemView.setOnClickListener((View v) -> {
                ScannerFragment.this.onConnectionClicked(scannerDevice.getId());
            });
            wifiIcon = (ImageView) itemView.findViewById(R.id.wifi_icon);
            this.textView = (TextView) itemView.findViewById(R.id.primary_text);
        }

        void bindTo(ScannerViewModel.ScannerDeviceUiState device) {
            this.scannerDevice = device;
            textView.setText(device.getDisplayName());
            int ridIcon = R.drawable.ic_wifi_normal_black_24dp;
            wifiIcon.setImageResource(ridIcon);
        }
    }

    private void onConnectionClicked(long deviceId) {
        if (viewModel.onConnectionClicked(deviceId)
                == ScannerViewModel.ConnectionClickAction.PROMPT_TO_CANCEL) {
            promptForCancelInvitation(deviceId);
        }
    }

    void promptForCancelInvitation(final long deviceId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage("Do you want to cancel the connection attempt?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        cancelInvitation(deviceId);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setCancelable(true);

        AlertDialog dialog = builder.create();
        dialog.show();
    }
    void cancelInvitation(final long deviceId)
    {
    }

    public static final DiffUtil.ItemCallback<ScannerViewModel.ScannerDeviceUiState> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ScannerViewModel.ScannerDeviceUiState>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull ScannerViewModel.ScannerDeviceUiState oldDevice,
                        @NonNull ScannerViewModel.ScannerDeviceUiState newDevice) {
                    String oldInstanceId = oldDevice.getInstanceId();
                    String newInstanceId = newDevice.getInstanceId();
                    if (oldInstanceId != null && !oldInstanceId.isEmpty()
                            && newInstanceId != null && !newInstanceId.isEmpty()) {
                        return oldInstanceId.equals(newInstanceId);
                    }
                    return oldDevice.getId() == newDevice.getId();
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull ScannerViewModel.ScannerDeviceUiState oldDevice,
                        @NonNull ScannerViewModel.ScannerDeviceUiState newDevice) {
                    return oldDevice.equals(newDevice);
                }
            };

    class DeviceAdapter extends ListAdapter<ScannerViewModel.ScannerDeviceUiState, DeviceViewHolder> {
        public DeviceAdapter() {
            super(DIFF_CALLBACK);
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_item, parent, false);
            return new DeviceViewHolder(view);
        }


        @Override
        public long getItemId(int position) {
            return getItem(position).getId();
        }

        @Override
        public void onBindViewHolder(DeviceViewHolder holder, int position) {
            holder.bindTo(getItem(position));
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        if (!Preferences.getDontshowHelpAgain(getContext()))
        {
            Preferences.setDontShowHelpAgain(getContext(),true);
            HelpDialogFragment.execute(this);
        }
    }

    private void renderUiState(@NonNull ScannerViewModel.ScannerUiState state) {
        captionView.setText(state.getCaption() == ScannerViewModel.ScannerUiState.Caption.RECONNECTING
                ? R.string.reconnecting
                : R.string.select_a_device_to_connect_to);

        boolean isSearching = state.getContent() == ScannerViewModel.ScannerUiState.Content.SEARCHING;
        boolean showingDevices = state.getContent() == ScannerViewModel.ScannerUiState.Content.DEVICE_LIST;
        boolean showingError = state.getContent() == ScannerViewModel.ScannerUiState.Content.ERROR;

        showSearchingView(isSearching);
        recyclerView.setVisibility(showingDevices ? View.VISIBLE : View.GONE);
        errorView.setVisibility(showingError ? View.VISIBLE : View.GONE);
        showCancel(state.getShowCancelButton());

        if (isSearching) {
            searchingTextView.setText(
                    state.getSearchingMessage() == ScannerViewModel.ScannerUiState.SearchingMessage.SEARCHING_FOR_DEVICE
                            ? R.string.searching_for_device
                            : R.string.searching);
        }

        if (state.getErrorMessage() == ScannerViewModel.ScannerUiState.ErrorMessage.SCAN_ERROR) {
            errorTextView.setText(state.getScanError());
        } else if (state.getErrorMessage() == ScannerViewModel.ScannerUiState.ErrorMessage.NO_DEVICES_FOUND) {
            errorTextView.setText(R.string.no_devices_found);
        }

        adapter.submitList(state.getDevices());
    }

    ObjectAnimator fadeInAnimator = null;

    private void fadeInAnimate(View targetView) {
        // Set initial alpha to 0 (completely transparent)
        targetView.setAlpha(0f);

        if (fadeInAnimator != null)
        {
            fadeInAnimator.cancel();
            fadeInAnimator = null;
        }

        // Create the ObjectAnimator
        ObjectAnimator animator = ObjectAnimator.ofFloat(targetView, "alpha", 0f, 1f);

        // Set the total duration to 2000 milliseconds (2 seconds)
        animator.setDuration(200);

        // Set a linear interpolator for smooth animation
        animator.setInterpolator(new LinearInterpolator());

        // Set the start delay to 1000 milliseconds (1 second)
        animator.setStartDelay(1000);

        // Start the animation
        animator.start();
        this.fadeInAnimator = animator;
    }
    private void showSearchingView(boolean show) {
        if (show)
        {
            boolean fadeInAnimation = searchingView.getVisibility() != View.VISIBLE;
            searchingView.setVisibility(View.VISIBLE);
            if (fadeInAnimation)
            {
                fadeInAnimate(searchingView);
            }
        } else {
            searchingView.setVisibility(View.GONE);
        }
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new DeviceAdapter();

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderUiState);
        recyclerView.setLayoutManager(new LinearLayoutManager(this.getContext()));
        recyclerView.setAdapter(adapter);



    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.scanner_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onStart() {
        super.onStart();
        // checkForIpv6DirectConnection();
    }

    private void checkForIpv6DirectConnection() {
        boolean hasHotspot = false;
        boolean hasDataConnection = false;
        InetAddress hotspotLinkLocalAddress = null;
        String hotspotLinkLocalHostAddress = "";
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface networkInterface : Collections.list(networkInterfaces)) {
                boolean isHotspot = false;
                String interfaceName = networkInterface.getName();
                InetAddress interfaceHotspotLinkLocalAddress = null;
                String interfaceHotspotLinkLocalHostAddress = "";

                if (interfaceName.equals("wlan0")) {
                    List<InterfaceAddress> inetfaceAddresses = networkInterface.getInterfaceAddresses();
                    for (InterfaceAddress interfaceAddress : inetfaceAddresses) {
                        InetAddress address = interfaceAddress.getAddress();
                        if (address != null) {
                            if (address.isLinkLocalAddress()) {
                                interfaceHotspotLinkLocalAddress = address;
                                interfaceHotspotLinkLocalHostAddress = address.getHostAddress();
                            } else if (address instanceof Inet4Address) {
                                Inet4Address inet4Address = (Inet4Address) address;
                                byte[] addressBytes = inet4Address.getAddress();
                                if (addressBytes[0] == 10 && addressBytes[1] == 42 && addressBytes[2] == 0) {
                                    isHotspot = true;
                                }
                            }
                        }
                    }
                    if (isHotspot) {
                        hasHotspot = true;
                        hotspotLinkLocalHostAddress = interfaceHotspotLinkLocalHostAddress;
                        hotspotLinkLocalHostAddress = interfaceHotspotLinkLocalHostAddress;
                    }
                }
            }
            if (hasHotspot)
            {
                new AlertDialog.Builder(getActivity())
                        .setTitle(("Hotspot found."))
                        .setMessage(
                                "llAddress: " + hotspotLinkLocalHostAddress)
                        .setPositiveButton("OK",
                                (DialogInterface dialog, int which)-> {

                                })
                        .create().show();
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onIpAddressResult(String ipAddress)
    {
        var webUrl = "  http://" + ipAddress;
        try {
            WebProbe.checkForPiPedalWebsiteAsync(ipAddress)
            .andThen(result -> {
                if (getActivity() == null) {
                    return; // just abandon this attempt.
                }
                if (result) {
                    Preferences.setConnectionIpAddress(getActivity(),ipAddress);
                    viewModel.setDirectConnection(ipAddress);
                } else {
                    ErrorDialogFragment.execute(this,"PiPedal web server not found at that address.\n\n"+webUrl,"Error");

                }
            }).andCatch((e) -> {
                ErrorDialogFragment.execute(this,e.getMessage() + "\n\n"+webUrl,"Error");
            });

        } catch (Exception e)
        {
            ErrorDialogFragment.execute(this,"PiPedal web server not found at that address.","Error");
        }

        Preferences.setConnectionIpAddress(getActivity(),ipAddress);
    }


}


