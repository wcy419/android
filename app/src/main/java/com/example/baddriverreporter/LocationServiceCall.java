package com.example.baddriverreporter;

        import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;

import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;


/**
 * Created by Ishwarya on 2/11/2017.
 */

public class LocationServiceCall implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    protected GoogleApiClient mGoogleApiClient;

    protected Location mLastLocation;
    protected Location mCurrentLocation;
    protected String mLatitudeText;
    protected String mLongitudeText;
    protected LocationRequest mLocationRequest;
    Context mContext;

    public synchronized void buildGoogleApiClient(){
        if (mGoogleApiClient == null){
            mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .addApi(AppIndex.API).build();
        }
        connect();
    }

    public LocationServiceCall(Context context){
        mContext = context;
        buildGoogleApiClient();
        createLocationRequest();
     //   connect();
    }

    public GoogleApiClient setGoogleApi(){
        return mGoogleApiClient;
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    public void connect(){

        mGoogleApiClient.connect();

    }

    public void disconnect(){
        mGoogleApiClient.disconnect();

    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(2000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    public void getLocationUpdates(){
       // connect();
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);


    }

    public void removeLocationUpdate(){
     //   disconnect();
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient,  this);
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        }
        if (mLastLocation != null) {
            mLatitudeText = (String.valueOf(mLastLocation.getLatitude()));
            mLongitudeText = (String.valueOf(mLastLocation.getLongitude()));
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }


    public String getLatitude(){
        return mLatitudeText;
    }

    public String getLongitude(){
        return mLongitudeText;
    }


    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        if (mCurrentLocation != null) {
            mLatitudeText = (String.valueOf(mCurrentLocation.getLatitude()));
            mLongitudeText = (String.valueOf(mCurrentLocation.getLongitude()));
        }
        updateUI();
    }

    private void updateUI() {
        mLatitudeText = String.valueOf(mCurrentLocation.getLatitude());
        mLongitudeText = String.valueOf(mCurrentLocation.getLongitude());
    }

}
