package com.pr0gramm.app.services.config;

import android.annotation.TargetApi;
import android.os.Build;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

/**
 * Simple config service to do remove configuration with local fallback
 */
@Singleton
public class ConfigService {
    private static final Logger logger = LoggerFactory.getLogger("ConfigService");

    private final Config configState;
    private final FirebaseRemoteConfig remoteConfig;
    private final Subject<Config, Config> configSubject;

    @Inject
    public ConfigService() {
        // create a remote-config instance and decorate it.
        remoteConfig = FirebaseRemoteConfig.getInstance();
        remoteConfig.setDefaults(Config.defaultValues());

        configState = new Config(remoteConfig);
        configSubject = BehaviorSubject.create(configState).toSerialized();

        // schedule updates once every 15 minutes
        Observable.interval(0, 15, TimeUnit.MINUTES, Schedulers.io()).subscribe(event -> update());
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void update() {
        logger.info("Fetch remote-config from firebase");

        remoteConfig.fetch(TimeUnit.MINUTES.toSeconds(10)).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                logger.info("Activating fetched remote-config.");
                remoteConfig.activateFetched();

                publishState();

            } else {
                logger.warn("Could not fetch remote config", task.getException());
            }
        });
    }

    private void publishState() {
        logger.info("Publishing change in config state");
        try {
            configSubject.onNext(configState);
        } catch (Exception err) {
            logger.warn("Could not publish the current state Oo", err);
        }
    }

    /**
     * Observes the config. The config changes are not observed on any particual thread.
     */
    public Observable<Config> observeConfig() {
        return configSubject;
    }

    public Config config() {
        return configState;
    }
}
