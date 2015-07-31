package com.pr0gramm.app.ab;

import android.app.Application;

/**
 * Base class for experiments where every user can participate.
 */
public abstract class EveryoneExperiment<Case extends Enum<Case>, Action extends Enum<Action>>
        extends Experiment<Case, Action> {

    @Override
    protected final boolean canParticipate(Application context) {
        return true;
    }
}
