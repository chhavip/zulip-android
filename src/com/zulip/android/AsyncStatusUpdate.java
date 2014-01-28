package com.zulip.android;

import android.content.Context;
import android.os.*;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends a status update and fetches updates for other users.
 */
public class AsyncStatusUpdate extends ZulipAsyncPushTask {

    private final Context context;

    /**
     * Declares a new HumbugAsyncPushTask, passing the activity as context.
     * 
     * @param activity
     */
    public AsyncStatusUpdate(ZulipActivity activity) {
        super(activity.app);
        this.context = activity;

        setProperty("status", "active");
    }

    public final void execute() {
        execute("POST", "/v1/users/me/presence");
    }

    /**
     * Choose latest presence object under two minutes in age, or null if none
     * are available
     */
    private JSONObject chooseLatestPresence(JSONObject person)
            throws JSONException {
        Iterator keys = person.keys();
        JSONObject latestPresence = null;
        while (keys.hasNext()) {
            String key = (String) keys.next();
            JSONObject presence = person.getJSONObject(key);
            long timestamp = presence.getLong("timestamp");
            if (latestPresence == null) {
                latestPresence = presence;
            } else if (latestPresence.getLong("timestamp") < timestamp) {
                latestPresence = presence;
            }
        }
        return latestPresence;
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);

        if (result != null) {
            try {
                JSONObject obj = new JSONObject(result);

                if (obj.getString("result").equals("success")) {

                    ConcurrentHashMap<String, Presence> presenceLookup = this.app.presences;
                    presenceLookup.clear();

                    JSONObject presences = obj.getJSONObject("presences");
                    long serverTimestamp = (long) obj
                            .getDouble("server_timestamp");
                    if (presences != null) {
                        Iterator emailIterator = presences.keys();
                        while (emailIterator.hasNext()) {
                            String email = (String) emailIterator.next();
                            JSONObject person = presences.getJSONObject(email);

                            // iterate through the devices providing updates and
                            // use the status of the latest one
                            JSONObject latestPresenceObj = chooseLatestPresence(person);
                            if (latestPresenceObj != null) {
                                long age = serverTimestamp
                                        - latestPresenceObj
                                                .getLong("timestamp");
                                String status = latestPresenceObj
                                        .getString("status");
                                String client = latestPresenceObj
                                        .getString("client");
                                Presence presence = new Presence(age, client,
                                        status);
                                presenceLookup.put(email, presence);
                            }
                        }
                    }

                    callback.onTaskComplete(result);

                    return;
                }
            } catch (JSONException e) {
                ZLog.logException(e);
            }
        }
        Toast.makeText(context, "Unknown error", Toast.LENGTH_LONG).show();
        Log.wtf("login", "We shouldn't have gotten this far.");
    }
}
