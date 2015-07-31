package com.pr0gramm.app.ab;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.common.base.Enums;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.pr0gramm.app.Track;

import rx.functions.Action1;

/**
 */
@Singleton
public class ExperimentService {
    private final Object lock = new Object();

    private final Application application;
    private final SharedPreferences preferences;

    @Inject
    public ExperimentService(Application app) {
        this.application = app;
        this.preferences = app.getSharedPreferences("ExperimentService", Context.MODE_PRIVATE);
    }

    public <Case extends Enum<Case>, Action extends Enum<Action>>
    void participate(Experiment<Case, Action> experiment,
                     Action1<? super Case> onParticipate) {

        if (!experiment.canParticipate(application)) {
            clear(experiment);
            return;
        }

        Case selectedCase;
        synchronized (lock) {
            selectedCase = currentCaseOf(experiment).or(() -> startParticipating(experiment));
        }

        onParticipate.call(selectedCase);
    }

    public <Case extends Enum<Case>, Action extends Enum<Action>>
    void report(Experiment<Case, Action> experiment, Action action) {
        Optional<Case> currentCase = currentCaseOf(experiment);
        if (currentCase.isPresent()) {
            String caseName = enumName(currentCase.get());
            String actionName = enumName(action);
            Track.experimentEvent(experiment.getName(), caseName, actionName);
        }
    }

    private <Case extends Enum<Case>, Action extends Enum<Action>>
    Case startParticipating(Experiment<Case, Action> experiment) {
        ImmutableList<Case> cases = experiment.getCases();
        Case selectedCase = cases.get((int) (Math.random() * cases.size()));

        // track the info, that a user starts participating in this experiment
        Track.experimentEvent(experiment.getName(), "NowParticipating", enumName(selectedCase));

        // store info for the next time
        preferences.edit()
                .putString(experiment.getName(), selectedCase.name())
                .apply();

        return selectedCase;
    }

    private <Case extends Enum<Case>, Action extends Enum<Action>>
    Optional<Case> currentCaseOf(Experiment<Case, Action> experiment) {
        String caseName = preferences.getString(experiment.getName(), null);
        if (caseName == null)
            return Optional.absent();

        return Enums.getIfPresent(experiment.getCaseType(), caseName);
    }

    private <Case extends Enum<Case>, Action extends Enum<Action>>
    void clear(Experiment<Case, Action> experiment) {
        synchronized (lock) {
            preferences.edit().remove(experiment.getName()).apply();
        }
    }

    /**
     * Removes all participation information for all experiments.
     */
    public void clear() {
        synchronized (lock) {
            preferences.edit().clear().apply();
        }
    }

    private static <E extends Enum<E>> String enumName(E value) {
        return value.name().toLowerCase().replace("_", " ");
    }
}
