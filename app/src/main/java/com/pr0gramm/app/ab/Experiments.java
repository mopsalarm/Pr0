package com.pr0gramm.app.ab;

import android.app.Application;

import com.pr0gramm.app.services.UserService;

import roboguice.RoboGuice;

/**
 * Class holding all the experiments
 */
public class Experiments {

    public static class DrawerExperiment extends Experiment<DrawerExperiment.Cases, DrawerExperiment.Actions> {
        private DrawerExperiment() {
        }

        @Override
        protected boolean canParticipate(Application context) {
            return RoboGuice
                    .getOrCreateBaseApplicationInjector(context)
                    .getInstance(UserService.class)
                    .isAuthorized();
        }

        public enum Cases {
            DONT_SHOW_DRAWER_HINT, SHOW_DRAWER_HINT
        }

        public enum Actions {
            INBOX_OPENED, MESSAGE_WRITTEN, BOOKMARK_CREATED, UPLOAD_ACTIVITY, IMAGE_UPLOADED
        }
    }

    public static final DrawerExperiment DRAWER_EXPERIMENT = new DrawerExperiment();
}
