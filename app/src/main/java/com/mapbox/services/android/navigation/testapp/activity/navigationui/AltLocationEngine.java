package com.mapbox.services.android.navigation.testapp.activity.navigationui;

import android.app.Activity;
import android.location.Location;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineListener;

public class AltLocationEngine extends LocationEngine {
    private long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
    private long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = 500;
    private FusedLocationProviderClient fusedLocationClient;
    private SettingsClient settingsClient;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;
    private boolean connected = false;
    private boolean receivingUpdates = false;
    private Location lastlocation = null;

    class ForwardingLocationCallback extends LocationCallback {
        AltLocationEngine locationEngine;

        ForwardingLocationCallback(AltLocationEngine engine) {
            locationEngine = engine;
        }
        @Override
        public void onLocationResult(LocationResult locationResult) {
            Location location = locationResult.getLastLocation();
            for(LocationEngineListener lel : locationEngine.locationListeners) {
                lel.onLocationChanged(location);
            }
        }
    };
    private ForwardingLocationCallback forwardingCallback;

    AltLocationEngine(@NonNull Activity activity) {
        super();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);
        settingsClient = LocationServices.getSettingsClient(activity);
        forwardingCallback = new ForwardingLocationCallback(this);
    }

    @Override
    public void activate() {
        if(!connected) {
            startLocationUpdates();
        }
    }

    @Override
    public void deactivate() {
        stopLocationUpdates();
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    @SuppressWarnings({"MissingPermission"})
    public Location getLastLocation() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                lastlocation = location;
            }
        });
        return lastlocation;
    }

    @Override
    @SuppressWarnings({"MissingPermission"})
    public void requestLocationUpdates() {
        //noinspection MissingPermission
        if(connected && !receivingUpdates) {
            fusedLocationClient.requestLocationUpdates(locationRequest, forwardingCallback, Looper.myLooper())
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            receivingUpdates = true;
                        }
                    });
        }
    }

    @Override
    public void removeLocationUpdates() {
        stopLocationUpdates();
    }

    @Override
    public Type obtainType() {
        return Type.GOOGLE_PLAY_SERVICES;
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest();
        //TODO: unhardcode these
        locationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        locationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        locationSettingsRequest = builder.build();
    }

    private void startLocationUpdates() {
        // fill out location request with most recent settings
        createLocationRequest();
        // fill out the request settings
        buildLocationSettingsRequest();

        // Begin by checking if the device has the necessary location settings.
        settingsClient.checkLocationSettings(locationSettingsRequest)
                .addOnSuccessListener(new OnSuccessListener<LocationSettingsResponse>() {
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        connected = true;
                        for(LocationEngineListener lel : locationListeners)
                            lel.onConnected();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        connected = false;
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.e("AltLocationEngine", "Location settings are not satisfied. Upgrade location settings");
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                Log.e("AltLocationEngine","Location settings are inadequate, and cannot be fixed here. Fix in Settings.");
                                break;
                        }
                    }
                });
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(forwardingCallback)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        //nothing to do here yet
                        receivingUpdates = false;
                    }
                });
    }
}
