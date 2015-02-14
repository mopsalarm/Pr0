package com.pr0gramm.app;

import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pr0gramm.app.api.Api;
import com.pr0gramm.app.api.Feed;
import com.pr0gramm.app.api.InstantDeserializer;
import com.pro0gramm.app.R;
import com.squareup.picasso.Picasso;

import org.joda.time.Instant;

import java.util.ArrayList;
import java.util.List;

import retrofit.Endpoints;
import retrofit.RestAdapter;
import retrofit.converter.GsonConverter;
import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectView;
import rx.functions.Action1;

import static rx.android.observables.AndroidObservable.bindActivity;


/**
 * This is the main class of our pr0gramm app.
 */
@ContentView(R.layout.activity_main)
public class MainActivity extends RoboActionBarActivity {

    @InjectView(R.id.list)
    private RecyclerView recyclerView;

    private ItemAdapter adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        adapter = new ItemAdapter();

        // use better layout manager, maybe write our own?
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerView.setAdapter(adapter);

        loadFeed();
    }

    /**
     * Loads the feed from pr0gramm. This should be put into some kind of service
     * that is injected into our activities.
     */
    private void loadFeed() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantDeserializer())
                .create();

        Api api = new RestAdapter.Builder()
                .setEndpoint("http://pr0gramm.com")
                .setConverter(new GsonConverter(gson))
                .setLogLevel(RestAdapter.LogLevel.BASIC)
                .build()
                .create(Api.class);

        // perform api request in the background and call
        // back to the main thread on finish
        bindActivity(this, api.itemsGet(1)).subscribe(new Action1<Feed>() {
            @Override
            public void call(Feed feed) {
                // we are now back in the main thread
                handleFeedResponse(feed);
            }
        });
    }

    /**
     * Display the elements from the feed
     *
     * @param feed The feed to display
     */
    private void handleFeedResponse(Feed feed) {
        // display feed now.
        Log.i("MainActivity", "Number of items: " + feed.getItems().size());
        adapter.addItems(feed.getItems());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class ItemAdapter extends RecyclerView.Adapter<ItemView> {
        private List<Feed.Item> items = new ArrayList<>();

        ItemAdapter() {
            setHasStableIds(true);
        }

        @Override
        public ItemView onCreateViewHolder(ViewGroup viewGroup, int i) {
            LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
            View view = inflater.inflate(R.layout.item_view, viewGroup, false);
            return new ItemView(view);
        }

        @Override
        public void onBindViewHolder(ItemView itemView, int i) {
            String url = "http://img.pr0gramm.com/" + items.get(i).getThumb();
            Picasso.with(MainActivity.this)
                    .load(url)
                    .into(itemView.image);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        public void addItems(List<Feed.Item> items) {
            int oldCount = this.items.size();
            this.items.addAll(items);
            notifyItemRangeInserted(oldCount, this.items.size());
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).getId();
        }
    }

    /**
     * View holder for a view in the list of items
     */
    private class ItemView extends RecyclerView.ViewHolder {
        final ImageView image;

        public ItemView(View itemView) {
            super(itemView);
            image = (ImageView) itemView.findViewById(R.id.image);
        }
    }
}
