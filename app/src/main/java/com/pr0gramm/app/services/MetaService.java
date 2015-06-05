package com.pr0gramm.app.services;

import com.google.common.base.Joiner;
import com.google.common.primitives.Longs;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.pr0gramm.app.LoggerAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import retrofit.RestAdapter;
import retrofit.converter.GsonConverter;
import retrofit.http.GET;
import retrofit.http.Query;
import rx.Observable;

/**
 */
@Singleton
public class MetaService {
    private static final Logger logger = LoggerFactory.getLogger(MetaService.class);
    private final Api api;

    @Inject
    public MetaService() {
        api = new RestAdapter.Builder()
                .setLog(new LoggerAdapter(logger))
                .setEndpoint("http://pr0.wibbly-wobbly.de:5003")
                .setConverter(new GsonConverter(new Gson()))
                .setLogLevel(RestAdapter.LogLevel.BASIC)
                .build()
                .create(Api.class);
    }

    public Observable<InfoResponse> getInfo(Collection<Long> items) {
        if(items.isEmpty()) {
            return Observable.just(EMPTY_INFO);
        }

        return api.info(Joiner.on(",").join(items));
    }

    private interface Api {
        @GET("/items")
        Observable<InfoResponse> info(@Query("ids") String itemIds);
    }

    @SuppressWarnings("unused")
    public static class InfoResponse {
        private long[] reposts;
        private List<SizeInfo> sizes;

        InfoResponse() {
            reposts = new long[0];
            sizes = Collections.emptyList();
        }

        public List<Long> getReposts() {
            return Longs.asList(reposts);
        }

        public List<SizeInfo> getSizes() {
            return sizes;
        }
    }

    @SuppressWarnings("unused")
    public static class SizeInfo {
        private long id;
        private int width;
        private int height;

        public long getId() {
            return id;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }

    public static final InfoResponse EMPTY_INFO = new InfoResponse();
}
