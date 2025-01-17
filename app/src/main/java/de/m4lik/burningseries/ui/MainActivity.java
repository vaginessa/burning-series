package de.m4lik.burningseries.ui;

import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.trello.rxlifecycle.android.RxLifecycleAndroid;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import de.m4lik.burningseries.ActivityComponent;
import de.m4lik.burningseries.BuildConfig;
import de.m4lik.burningseries.R;
import de.m4lik.burningseries.api.API;
import de.m4lik.burningseries.api.APIInterface;
import de.m4lik.burningseries.api.objects.GenreMap;
import de.m4lik.burningseries.api.objects.GenreObj;
import de.m4lik.burningseries.api.objects.ShowObj;
import de.m4lik.burningseries.database.DatabaseUtils;
import de.m4lik.burningseries.database.MainDBHelper;
import de.m4lik.burningseries.services.SyncBroadcastReceiver;
import de.m4lik.burningseries.ui.base.ActivityBase;
import de.m4lik.burningseries.ui.dialogs.UpdateDialog;
import de.m4lik.burningseries.ui.mainFragments.FavsFragment;
import de.m4lik.burningseries.ui.mainFragments.GenresFragment;
import de.m4lik.burningseries.ui.mainFragments.HistoryFragment;
import de.m4lik.burningseries.ui.mainFragments.NewsFragment;
import de.m4lik.burningseries.ui.mainFragments.SeriesFragment;
import de.m4lik.burningseries.util.Logger;
import de.m4lik.burningseries.util.Settings;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import rx.Observable;

import static de.m4lik.burningseries.database.SeriesContract.SQL_TRUNCATE_GENRES_TABLE;
import static de.m4lik.burningseries.database.SeriesContract.SQL_TRUNCATE_SERIES_TABLE;
import static de.m4lik.burningseries.database.SeriesContract.genresTable;
import static de.m4lik.burningseries.database.SeriesContract.seriesTable;
import static de.m4lik.burningseries.services.ThemeHelperService.theme;
import static rx.android.schedulers.AndroidSchedulers.mainThread;

/**
 * MainActivity class
 *
 * @author Malik Mann
 */

public class MainActivity extends ActivityBase
        implements NavigationView.OnNavigationItemSelectedListener {

    private static String userName;
    private static String userSession;

    private Menu menu;

    private Boolean seriesList = false;

    private boolean isTablet = false;

    ProgressDialog progressDialog;

    @BindView(R.id.nav_view)
    NavigationView navigationView;

    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @Override
    protected void injectComponent(ActivityComponent appComponent) {
        appComponent.inject(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(theme().translucentStatus);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Bitmap icon = BitmapFactory.decodeResource(getApplicationContext().getResources(),
                R.drawable.ic_stat_name);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setTaskDescription(
                    new ActivityManager.TaskDescription(
                            "Burning Series",
                            icon,
                            getApplicationContext().getResources().getColor(theme().primaryColor)
                    ));
        }
        setSupportActionBar(toolbar);

        if (getApplicationContext().getResources().getBoolean(R.bool.isTablet)) {
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            isTablet = true;
        } else {
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }

        Settings settings = Settings.of(this);
        userName = settings.getUserName();
        userSession = settings.getUserSession();

        navigationView.setNavigationItemSelectedListener(this);

        if (!isTablet) {
            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                    this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
            toggle.syncState();
        }

        //Update check
        Observable.just(null)
                .delay(10, TimeUnit.SECONDS, mainThread())
                .compose(RxLifecycleAndroid.bindActivity(lifecycle()))
                .subscribe(o -> UpdateDialog.checkForUpdates(MainActivity.this, false));

        TextView userTextView = (TextView) navigationView.getHeaderView(0).findViewById(R.id.nav_username_text);
        userTextView.setText(userName);

        if (userSession.equals("")) {
            navigationView.getMenu().findItem(R.id.login_menu_item).setVisible(true);
            navigationView.getMenu().findItem(R.id.logout_menu_item).setVisible(false);
        } else {
            navigationView.getMenu().findItem(R.id.login_menu_item).setVisible(false);
            navigationView.getMenu().findItem(R.id.logout_menu_item).setVisible(true);
        }
    }

    @Override
    public void onBackPressed() {

        if (seriesList) {
            setFragment("genres");
            seriesList = false;
            return;
        }

        if (!isTablet) {
            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            if (!drawer.isDrawerOpen(GravityCompat.START)) {
                drawer.openDrawer(GravityCompat.START);
                return;
            }
        }

        super.onBackPressed();

    }

    private void fetchFirebase() {
        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        remoteConfig.setConfigSettings(
                new FirebaseRemoteConfigSettings.Builder()
                        .setDeveloperModeEnabled(BuildConfig.DEBUG)
                        .build());
        remoteConfig.fetch()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        remoteConfig.activateFetched();
                        setup();
                    } else {
                        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), "Da ist was schiefgelaufen.\nVersuche es noch einmal...", Snackbar.LENGTH_SHORT);
                        View snackbarView = snackbar.getView();
                        snackbarView.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), theme().primaryColorDark));
                        snackbar.show();
                    }
                });
    }

    private void setup() {
        if (DatabaseUtils.with(this).isSeriesListEmpty())
            fetchSeries();
        else
            setFragment(Settings.of(getApplicationContext()).getStartupView());

        SearchView searchView = (SearchView) MenuItemCompat.getActionView(this.menu.findItem(R.id.action_search));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                SeriesFragment fragment = (SeriesFragment) getSupportFragmentManager().findFragmentByTag("seriesFragment");
                fragment.filterList(newText);
                return false;
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        // schedule a sync operation every minute
        Observable.interval(0, 1, TimeUnit.MINUTES, mainThread())
                .compose(RxLifecycleAndroid.bindActivity(lifecycle()))
                .subscribe(event -> SyncBroadcastReceiver.syncNow(MainActivity.this));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        this.menu = menu;

        fetchFirebase();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            fetchSeries();
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        Intent intent;

        switch (item.getItemId()) {

            case R.id.nav_series:
                setFragment("series");
                break;

            case R.id.nav_genres:
                setFragment("genres");
                break;

            case R.id.nav_favs:
                setFragment("favorites");
                break;

            case R.id.nav_history:
                setFragment("history");
                break;

            case R.id.login_menu_item:
                intent = new Intent(this, LoginActivity.class);
                startActivity(intent);
                break;

            case R.id.logout_menu_item:
                logout();
                break;

            /*case R.id.nav_news:
                setFragment("news");
                break;*/

            case R.id.nav_stats:
                intent = new Intent(this, StatisticsActivity.class);
                startActivity(intent);
                break;

            case R.id.nav_share:
                String text = "Versuch mal die neue Burning-Series App. https://github.com/M4lik/burning-series/releases";
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Burning-Series app");
                shareIntent.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_using)));
                break;

            case R.id.nav_settings:
                intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
        }

        if (!isTablet) {
            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            drawer.closeDrawer(GravityCompat.START);
        }
        return true;
    }

    public void logout() {

        Log.d("Logout", "Logging out...");

        final API api = new API();
        api.setSession(Settings.of(this).getUserSession());
        api.generateToken("logout");

        Logger.logout(getApplicationContext());

        APIInterface apii = api.getInterface();
        Call<ResponseBody> call = apii.logout(api.getToken(), api.getUserAgent(), api.getSession());
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Settings.of(getApplicationContext()).raw().edit()
                        .remove("pref_session")
                        .remove("pref_user")
                        .commit();

                navigationView.getMenu().findItem(R.id.logout_menu_item).setVisible(false);
                navigationView.getMenu().findItem(R.id.login_menu_item).setVisible(true);

                Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), "Ausgeloggt", Snackbar.LENGTH_SHORT);
                View snackbarView = snackbar.getView();
                snackbarView.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), theme().primaryColorDark));
                snackbar.show();

                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                Log.d("Logout", "Restarting...");
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Snackbar.make(findViewById(android.R.id.content), "Verbindungsfehler.", Snackbar.LENGTH_SHORT).show();
            }
        });

    }

    private void setFragment(String fragment) {

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        MenuItem searchItem = menu.findItem(R.id.action_search);

        Fragment replaceFragment;
        String tag;

        if (fragment == null)
            fragment = "series";

        switch (fragment) {
            case "genres":
                searchItem.setVisible(false);
                replaceFragment = new GenresFragment();
                tag = "genresFragment";
                break;
            case "favorites":
                searchItem.setVisible(false);
                replaceFragment = new FavsFragment();
                tag = "favsFragment";
                break;
            case "history":
                searchItem.setVisible(false);
                replaceFragment = new HistoryFragment();
                tag = "historyFragment";
                break;
            case "news":
                searchItem.setVisible(false);
                replaceFragment = new NewsFragment();
                tag = "newsFragment";
                break;
            default:
                searchItem.setVisible(true);
                replaceFragment = new SeriesFragment();
                tag = "seriesFragment";
                break;
        }

        transaction.replace(R.id.fragmentContainerMain, replaceFragment, tag);
        transaction.commit();
    }

    private void fetchSeries() {

        progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setMessage("Serien werden geladen.\nBitte kurz warten...");
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();

        API api = new API();
        APIInterface apiInterface = api.getInterface();
        api.setSession(MainActivity.userSession);
        api.generateToken("series:genre");
        Call<GenreMap> call = apiInterface.getSeriesGenreList(api.getToken(), api.getUserAgent(), api.getSession());
        call.enqueue(new Callback<GenreMap>() {
            @Override
            public void onResponse(Call<GenreMap> call, Response<GenreMap> response) {

                MainDBHelper dbHelper = new MainDBHelper(getApplicationContext());
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.execSQL(SQL_TRUNCATE_SERIES_TABLE);
                db.execSQL(SQL_TRUNCATE_GENRES_TABLE);
                db.close();

                new SeriesDatabaseUpdate(response.body()).execute();
            }

            @Override
            public void onFailure(Call<GenreMap> call, Throwable t) {

                progressDialog.dismiss();

                t.printStackTrace();

                Snackbar snackbar = Snackbar.make(
                        findViewById(android.R.id.content),
                        "Da ist was schief gelaufen...\n" +
                                "Bitte Serien neuladen.",
                        Snackbar.LENGTH_LONG
                );

                View snackbarView = snackbar.getView();
                snackbarView.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), theme().primaryColor));
                snackbar.show();

                getApplicationContext().deleteDatabase(MainDBHelper.DATABASE_NAME);
            }
        });
    }

    public Boolean getSeriesList() {
        return seriesList;
    }

    public void setSeriesList(Boolean seriesList) {
        this.seriesList = seriesList;
    }

    /**
     * Method to fetch user favorites from burning series
     * @see API
     * @see APIInterface
     * @see retrofit2.Retrofit
     */
    public void fetchFavorites() {

        API api = new API();
        APIInterface apiInterface = api.getInterface();

        api.setSession(userSession);
        api.generateToken("user/series");
        Call<List<ShowObj>> favscall = apiInterface.getFavorites(api.getToken(), api.getUserAgent(), api.getSession());
        favscall.enqueue(new Callback<List<ShowObj>>() {
            @Override
            public void onResponse(Call<List<ShowObj>> call, Response<List<ShowObj>> response) {
                new favoritesDatabaseUpdate(response.body()).execute();
            }

            @Override
            public void onFailure(Call<List<ShowObj>> call, Throwable t) {
                t.printStackTrace();

                progressDialog.dismiss();

                Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), "Da ist was schief gelaufen...", Snackbar.LENGTH_SHORT);
                View snackbarView = snackbar.getView();
                snackbarView.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), theme().primaryColorDark));
                snackbar.show();
            }
        });
    }

    /**
     * Async task to write shows list to database
     */
    private class SeriesDatabaseUpdate extends AsyncTask<Void, Void, Void> {

        GenreMap genreMap;

        SeriesDatabaseUpdate(GenreMap genreMap) {
            this.genreMap = genreMap;
        }

        @Override
        protected Void doInBackground(Void... voids) {


            MainDBHelper dbHelper = new MainDBHelper(getApplicationContext());
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            int genreID = 0;
            for (Map.Entry<String, GenreObj> entry : genreMap.entrySet()) {
                String currentGenre = entry.getKey();
                GenreObj go = entry.getValue();
                ContentValues values = new ContentValues();
                values.put(genresTable.COLUMN_NAME_GENRE, currentGenre);
                values.put(genresTable.COLUMN_NAME_ID, genreID);
                db.insert(genresTable.TABLE_NAME, null, values);
                Iterator itr = Arrays.asList(go.getShows()).iterator();
                int i = 0;
                while (i < go.getShows().length) {
                    int j = 1;

                    db.beginTransaction();
                    while (j <= 50 && itr.hasNext()) {
                        ShowObj show = (ShowObj) itr.next();

                        ContentValues cv = new ContentValues();
                        cv.put(seriesTable.COLUMN_NAME_ID, show.getId());
                        cv.put(seriesTable.COLUMN_NAME_TITLE, show.getName());
                        cv.put(seriesTable.COLUMN_NAME_GENRE, currentGenre);
                        cv.put(seriesTable.COLUMN_NAME_DESCR, "");
                        cv.put(seriesTable.COLUMN_NAME_ISFAV, 0);

                        db.insert(seriesTable.TABLE_NAME, null, cv);

                        j++;
                        i++;
                    }
                    db.setTransactionSuccessful();
                    db.endTransaction();
                }
                genreID++;
            }


            db.close();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            progressDialog.dismiss();
            if (userSession.equals(""))
                setFragment("series");
            else
                fetchFavorites();
            super.onPostExecute(aVoid);
        }
    }

    /**
     * Async task to write favorites to database
     */
    private class favoritesDatabaseUpdate extends AsyncTask<Void, Void, Void> {

        List<ShowObj> list;

        favoritesDatabaseUpdate(List<ShowObj> list) {
            this.list = list;
        }

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setMessage("Favoriten werden geladen.\nBitte kurz warten...");
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.show();
        }

        @Override
        protected Void doInBackground(Void... voids) {


            MainDBHelper dbHelper = new MainDBHelper(getApplicationContext());
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            Iterator itr = list.iterator();
            int i = 0;
            while (i < list.size()) {
                int j = 1;

                db.beginTransaction();
                while (j <= 50 && itr.hasNext()) {
                    ShowObj show = (ShowObj) itr.next();
                    ContentValues cv = new ContentValues();
                    cv.put(seriesTable.COLUMN_NAME_ISFAV, 1);
                    db.update(
                            seriesTable.TABLE_NAME,
                            cv,
                            seriesTable.COLUMN_NAME_ID + " = ?",
                            new String[]{show.getId().toString()}
                    );

                    j++;
                    i++;
                }
                db.setTransactionSuccessful();
                db.endTransaction();
            }

            db.close();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            progressDialog.dismiss();
            setFragment("series");

            super.onPostExecute(aVoid);
        }
    }
}