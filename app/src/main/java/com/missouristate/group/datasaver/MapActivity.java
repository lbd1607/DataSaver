package com.missouristate.group.datasaver;

import android.Manifest;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by: Chris, Jeremy, Laura, Matthew
 * 3 May 2018
 *
 * This app uses the Location, Google maps, Geolocation, and Geofence
 * APIs to find a user's current location and allow them to set Geofences by
 * searching for their desired location on the map. When a user is inside the
 * Geofence's radius or enters it, their Wifi and Bluetooth is enabled. When
 * the user is outside the Geofence's radius or exits it, their Wifi and Bluetooth
 * is disabled in order to save data usage on their mobile device.
 */

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    //Set constants for Geofence radius and expiration.
    public static final float GEOFENCE_RADIUS_IN_METERS = 100;
    public static final long GEOFENCE_EXPIRATION_IN_MILLISECONDS = 10000;

    //Create WiFiManager and Bluetooth objects
    private WifiManager wifiManager;
    private BluetoothAdapter mBluetoothAdapter;

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Toast.makeText(this, "Map is Ready", Toast.LENGTH_LONG).show();
        Log.d(TAG, "onMapReady: map is ready");
        mMap = googleMap;

        if (mLocationPermissionsGranted) {
            getDeviceLocation();

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);

            init();
        }

        //Keeps the screen locked in portrait mode to keep from destroying the activity
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private static final String TAG = "MapActivity";

    private static final String FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final String COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1234;
    private static final float DEFAULT_ZOOM = 15f;

    //widgets
    private EditText mSearchText;

    //varaibles
    private Boolean mLocationPermissionsGranted = false;
    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationProviderClient;

    //Geofence variables
    private GeofencingClient mGeofencingClient;
    private PendingIntent mGeofencePendingIntent;
    private List<Geofence> mGeofenceList = new ArrayList<>();
    private int transitions;
    LatLng geoLatLng;
    LatLng deviceLatLng;
    double myLatitude;
    double myLongitude;
    double geoLatitude;
    double geoLongitude;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        mSearchText = (EditText) findViewById(R.id.input_search);

        //Call to get location permissions
        getLocationPermission();

        //Initialize WifiManager object
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        //Enable Wifi to better determine the user's preliminary location and related services
        wifiManager.setWifiEnabled(true);

        //Initialize Bluetooth adapter object
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    private void init() {
        Log.d(TAG, "init: initializing");

        mSearchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE
                        || keyEvent.getAction() == KeyEvent.ACTION_DOWN
                        || keyEvent.getAction() == KeyEvent.KEYCODE_ENTER) {

                    //execute method for search
                    geoLocate();
                }

                return false;
            }
        });
        hideSoftKeyboard();
    }

    private void geoLocate() {

        Log.d(TAG, "geoLocate: geolocating");

        String searchString = mSearchText.getText().toString();

        Geocoder geocoder = new Geocoder(MapActivity.this);
        List<Address> list = new ArrayList<>();

        try {
            list = geocoder.getFromLocationName(searchString, 1);
        } catch (IOException e) {
            Log.d(TAG, "geoLocate: IOException" + e.getMessage());
            e.printStackTrace();

        }

        if (list.size() > 0) {
            Address address = list.get(0);

            Log.d(TAG, "geoLocate: Found location" + address.toString());

            moveCamera(new LatLng(address.getLatitude(), address.getLongitude()), DEFAULT_ZOOM, address.getAddressLine(0));

            //Set Geofence latitude and longitude to current address latitude and longitude
            geoLatitude = address.getLatitude();
            geoLongitude = address.getLongitude();
            //Call makeGeofence and pass Geolocate address coordinates
            makeGeofence(new LatLng(geoLatitude, geoLongitude));
        }

        hideSoftKeyboard();
    }


    private void getDeviceLocation() {
        Log.d(TAG, "getDeviceLocation: getting devices current location");
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        try {
            if (mLocationPermissionsGranted) {
                final Task location = mFusedLocationProviderClient.getLastLocation();
                location.addOnCompleteListener(new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "onComplete: found location");
                            final Location currentLocation = (Location) task.getResult();

                            //Saves user's current latitude and longitude for checkPosition()
                            myLatitude = currentLocation.getLatitude();
                            myLongitude = currentLocation.getLongitude();

                            //Send current location to checkPosition()
                            checkPosition(geoLatLng, new LatLng(myLatitude, myLongitude));

                            moveCamera(new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()), DEFAULT_ZOOM, "My Location");

                        } else {
                            Log.d(TAG, "onComplete: current location is null");
                            Toast.makeText(MapActivity.this, "unable to get current location", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }


        } catch (SecurityException e) {
            Log.e(TAG, "getDeviceLocation: SercurityException: " + e.getMessage());
        }
    }

    private void moveCamera(LatLng latLng, float zoom, String title) {
        Log.d(TAG, "moveCamera: moving the camera to: " + latLng.latitude + latLng.longitude);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));

        if (!title.equals("My Location")) {
            MarkerOptions options = new MarkerOptions()
                    .position(latLng)
                    .title(title);
            mMap.addMarker(options);
        }

        hideSoftKeyboard();

        //Call makeGeofence and pass it the LatLng object
        makeGeofence(latLng);
    }

    private void initMap() {
        Log.d(TAG, "initMap: Initializing Map");
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        mapFragment.getMapAsync(MapActivity.this);
    }

    private void getLocationPermission() {
        Log.d(TAG, "getLocationPermission: Getting Locaion Permissions");
        String[] permission = {Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION};

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            if (ContextCompat.checkSelfPermission(this.getApplicationContext(), COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mLocationPermissionsGranted = true;
                initMap();
            } else {
                ActivityCompat.requestPermissions(this, permission, LOCATION_PERMISSION_REQUEST_CODE);
            }
        } else {
            ActivityCompat.requestPermissions(this, permission, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionResult: called");
        mLocationPermissionsGranted = false;

        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE: {
                if (grantResults.length > 0) {
                    for (int i = 0; i < grantResults.length; i++) {
                        mLocationPermissionsGranted = false;
                        Log.d(TAG, "onRequestPermissionResult: Permission Failed ");
                        return;
                    }
                }
                Log.d(TAG, "onRequestPermissionResult: permission Granted");
                mLocationPermissionsGranted = true;
                //init map
                initMap();
            }
        }
    }

    private void hideSoftKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null){
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    //*********************************** Geofence *****************************

    public void makeGeofence(final LatLng geoLatLng) {

        //Combine transitions into one statement
        transitions = Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_DWELL | Geofence.GEOFENCE_TRANSITION_EXIT;

        //Initializes GeofencingClient object in this activity
        mGeofencingClient = LocationServices.getGeofencingClient(this);

        //Clears the map of all markers and circles
        mMap.clear();

        //Builds Geofence according to user-chosen location
        mGeofenceList.add(new Geofence.Builder()
                // Set the request ID of the geofence. This is a string to identify this geofence.
                .setRequestId(String.valueOf(LOCATION_PERMISSION_REQUEST_CODE))

                //Set preliminary Geofence parameters
                .setCircularRegion(
                        geoLatLng.latitude,
                        geoLatLng.longitude,
                        GEOFENCE_RADIUS_IN_METERS
                )
                .setExpirationDuration(GEOFENCE_EXPIRATION_IN_MILLISECONDS)
                .setTransitionTypes(transitions)
                .setLoiteringDelay(30000)
                .build());

        //Checks Location permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            getLocationPermission();
        }

        //Adds a Geofence and logs success or failure
        mGeofencingClient.addGeofences(getGeofencingRequest(), getGeofencePendingIntent())
                .addOnSuccessListener(this, new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Geofence successfullly added");

                        //Create CircleOptions object for Geofence marker
                        final CircleOptions circleOptions = new CircleOptions();

                        //Fills in a circle marker inside the Geofence radius on the map
                        circleOptions
                                .radius(GEOFENCE_RADIUS_IN_METERS)
                                .fillColor(Color.argb(40, 0, 255, 152))
                                .strokeWidth(1)
                                .strokeColor((Color.argb(40, 0, 255, 152)))
                                .center(geoLatLng);
                        mMap.addCircle(circleOptions);
                        checkPosition(geoLatLng, deviceLatLng);
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d(TAG, "Geofence add failed");
                    }
                });

        //Removes a Geofence and logs success or failure
        mGeofencingClient.removeGeofences(getGeofencePendingIntent())
                .addOnSuccessListener(this, new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Geofence successsfully removed");
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d(TAG, "Geofence remove failed");
                    }
                });
    }//end makeGeofence()

    //Pending intent for the Geofence that is reused upon check.
    private PendingIntent getGeofencePendingIntent() {

        // Reuse the PendingIntent if we already have it.
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }
        Intent intent = new Intent(this, GeofenceTransitionsIntentService.class);

        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when
        // calling addGeofences() and removeGeofences().
        mGeofencePendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.
                FLAG_UPDATE_CURRENT);

        return mGeofencePendingIntent;
    }//end getGeofencingPendingIntent()

    //Sends a request to build the Geofence
    private GeofencingRequest getGeofencingRequest() {

        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(mGeofenceList);
        return builder.build();
    }//end getGeofencingRequest()

    //@Params geoLatitude and geoLongitude (contained in LatLng object geoLatLng)
    //@Params myLatitude and myLongitude (contained in deviceLatLng)
    public void checkPosition(final LatLng geoLatLng, final LatLng deviceLatLng) {

        //Create Location object to hold Geofence's latitude and longitude
        Location geofenceLoc = new Location("GeofenceLoc");
        geofenceLoc.setLatitude(geoLatitude);
        geofenceLoc.setLongitude(geoLongitude);

        //Create Location object to hold device latitude and longitude
        Location myLoc = new Location("myLoc");
        myLoc.setLatitude(myLatitude);
        myLoc.setLongitude(myLongitude);

        //Set distance between user's location and Geofence using distanceTo()
        float distance = myLoc.distanceTo(geofenceLoc);

        //Check Geofence position against device postition to determine whether
        //Wifi and Bluetooth should be enabled or disabled
        if(distance <= GEOFENCE_RADIUS_IN_METERS) {
            featuresEnable();
            Log.d(TAG, "Features are enabled");
        }
        else if (distance > GEOFENCE_RADIUS_IN_METERS){
            featuresDisable();
            Log.d(TAG, "Features are disabled");
        }
    }//end checkPosition()

    //Enable Wifi and bluetooth
    public void featuresEnable(){
                String servicesOnMsg = (String) getText(R.string.wifi_bluetooth_on);
                wifiManager.setWifiEnabled(true);
                Toast.makeText(this, servicesOnMsg, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Wifi enabled");

                mBluetoothAdapter.enable();
                Log.d(TAG, "Bluetooth enabled");
    }//end featuresEnable()

    //Disable Wifi and bluetooth
    public void featuresDisable(){
                String servicesOffMsg = (String) getText(R.string.wifi_bluetooth_off);
                wifiManager.setWifiEnabled(false);
                Toast.makeText(this, servicesOffMsg, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Wifi disabled");

                mBluetoothAdapter.disable();
                Log.d(TAG, "Bluetooth disabled");
    }//end featuresDisable()

}//end MapActivity class

