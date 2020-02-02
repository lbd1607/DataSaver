package com.missouristate.group.datasaver;

/**
 * Created by: Chris, Jeremy, Laura, Matthew
 * 3 May 2018
 *
 * This class creates an intent service for the Geofence
 * to monitor Geofence transitions, like entry and exit.
 * The class also includes a handler for the Geofence and
 * methods to log details and errors if they occur.
 */

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.List;

import static android.content.ContentValues.TAG;

public class GeofenceTransitionsIntentService extends IntentService {

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public GeofenceTransitionsIntentService(String name) {
        super(name);
    }//end GeofenceTransitionsIntentService constructor

    //Intent handler for Geofence
    @SuppressLint("StringFormatInvalid")
    protected void onHandleIntent(Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofence event error");
            return;
        }

        // Get the transition type.
        int geofenceTransition = geofencingEvent.getGeofenceTransition();

        // Test that the reported transition was of interest.
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
                geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            // Get the geofences that were triggered. A single event can trigger
            // multiple geofences.
            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();

            // Get the transition details as a String.
            String geofenceTransitionDetails = getGeofenceTransitionDetails(
                    this,
                    geofenceTransition,
                    triggeringGeofences
            );

            // Send notification and log the transition details.
            sendNotification(geofenceTransitionDetails);
            Log.i(TAG, geofenceTransitionDetails);
        } else {
            // Log the error.
            Log.e(TAG, "Geofence trigger fail");
        }
    }//end onHandleIntent()

    private String getGeofenceTransitionDetails(GeofenceTransitionsIntentService
                                                        geofenceTransitionsIntentService, int geofenceTransition, List<Geofence>
                                                        triggeringGeofences) {
        String geofenceError = "GEOFENCE ERROR";
        return geofenceError;
    }//end getGeofenceTransitionDetails()

    private void sendNotification(String geofenceTransitionDetails) {
        Toast.makeText(this, "An error has occurred with the Geofence" , Toast.LENGTH_SHORT).show();
    }//end sendNotification()

}//end GeofenceTransitionsIntentService class
