package com.pr0gramm.app.ab;

import android.app.Application;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;

import java.util.EnumSet;

/**
 */
public abstract class Experiment<Case extends Enum<Case>, Action extends Enum<Action>> {
    private final String name = getClass().getSimpleName();

    protected Experiment() {
        if(caseType.getRawType().getEnclosingClass() != getClass()) {
            throw new IllegalStateException("Case enum must be declared as nested enum of " + name);
        }

        // get action type from inheritance info
        TypeToken<Action> actionType = new TypeToken<Action>(Experiment.this.getClass()) {
        };

        if(actionType.getRawType().getEnclosingClass() != getClass()) {
            throw new IllegalStateException("Action enum must be declared as nested enum of " + name);
        }
    }

    protected abstract boolean canParticipate(Application context);

    /**
     * Returns the name of this experiment.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets a list with all valid classes for this experiment.
     */
    public ImmutableList<Case> getCases() {
        //noinspection unchecked
        return ImmutableList.copyOf(EnumSet.allOf((Class<Case>) caseType.getRawType()));
    }

    public Class<Case> getCaseType() {
        //noinspection unchecked
        return (Class<Case>) caseType.getRawType();
    }

    private final TypeToken<Case> caseType = new TypeToken<Case>(getClass()) {
    };
}
