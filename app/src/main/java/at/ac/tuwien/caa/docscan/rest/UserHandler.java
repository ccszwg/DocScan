package at.ac.tuwien.caa.docscan.rest;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Created by fabian on 09.02.2017.
 */
public class UserHandler {

    private static final String NAME_KEY =          "userName";
    private static final String PASSWORD_KEY =      "userPassword";
    private static final String DROPBOX_TOKEN_KEY = "dropboxToken";
    private static final String CONNECTION_KEY =    "connection";

    public static void saveDropboxToken(Context context) {

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(CONNECTION_KEY, User.getInstance().getConnection());
        editor.putString(DROPBOX_TOKEN_KEY, User.getInstance().getDropboxToken());
        editor.apply();
        editor.commit();

    }

    public static boolean loadDropboxToken(Context context) {

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

        String token = sharedPref.getString(DROPBOX_TOKEN_KEY, null);

        if (token == null)
            return false;
        else {
            User.getInstance().setDropboxToken(token);
            return true;
        }

    }

    public static void saveTranskribusCredentials(Activity activity) {

        //        TODO: use here encryption
//        SharedPreferences sharedPref = activity.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(NAME_KEY, User.getInstance().getUserName());
        editor.putString(PASSWORD_KEY, User.getInstance().getPassword());
        editor.putInt(CONNECTION_KEY, User.getInstance().getConnection());
        editor.apply();
        editor.commit();

    }


    public static int loadConnection(Context context) {

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

        int noValue = -1;
        int connection = sharedPref.getInt(CONNECTION_KEY, noValue);

        return connection;

    }

    public static boolean loadCredentials(Context context) {

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);


        User.getInstance().setConnection(loadConnection(context));
//        SharedPreferences sharedPref = activity.getApplicationContext().getPreferences(Context.MODE_PRIVATE);
        String name = sharedPref.getString(NAME_KEY, null);
        String defaultPassword = null;
        String password = sharedPref.getString(PASSWORD_KEY, defaultPassword);

        if (name == null)
            return false;
        else {
            User.getInstance().setUserName(name);
            User.getInstance().setPassword(password);
            return true;
        }

    }
}
