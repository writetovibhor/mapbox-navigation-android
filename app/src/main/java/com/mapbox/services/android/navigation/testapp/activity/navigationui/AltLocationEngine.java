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
    private static final int REQUEST_CHECK_SETTINGS = 0x1;
    private long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
    private long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = 500;
    private FusedLocationProviderClient mFusedLocationClient;
    private SettingsClient mSettingsClient;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest mLocationSettingsRequest;
    private boolean mConnected = false;
    private boolean mReceivingUpdates = false;
    private Location mLastlocation = null;

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
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);
        mSettingsClient = LocationServices.getSettingsClient(activity);
        forwardingCallback = new ForwardingLocationCallback(this);
    }

    @Override
    public void activate() {
        if(!mConnected) {
            startLocationUpdates();
        }
    }

    @Override
    public void deactivate() {
        stopLocationUpdates();
        mConnected = false;
    }

    @Override
    public boolean isConnected() {
        return mConnected;
    }

    @Override
    @SuppressWarnings({"MissingPermission"})
    public Location getLastLocation() {
        mFusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                mLastlocation = location;
            }
        });
        return mLastlocation;
    }

    @Override
    @SuppressWarnings({"MissingPermission"})
    public void requestLocationUpdates() {
        //noinspection MissingPermission
        if(mConnected && !mReceivingUpdates) {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, forwardingCallback, Looper.myLooper())
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            mReceivingUpdates = true;
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
        mLocationRequest = new LocationRequest();

        //TODO: unhardcode these

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);

        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();
    }

    private void startLocationUpdates() {
        // fill out location request with most recent settings
        createLocationRequest();
        // fill out the request settings
        buildLocationSettingsRequest();

        // Begin by checking if the device has the necessary location settings.
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener(new OnSuccessListener<LocationSettingsResponse>() {
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        mConnected = true;
                        for(LocationEngineListener lel : locationListeners)
                            lel.onConnected();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        mConnected = false;
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
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        mFusedLocationClient.removeLocationUpdates(forwardingCallback)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        //nothing to do here yet
                        mReceivingUpdates = false;
                    }
                });
    }
}
