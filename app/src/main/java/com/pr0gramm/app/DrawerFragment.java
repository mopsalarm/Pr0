package com.pr0gramm.app;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import roboguice.fragment.RoboFragment;

/**
 */
public class DrawerFragment extends RoboFragment {
    private Map<Integer, TextView> itemViews;

    private ColorStateList defaultColor = ColorStateList.valueOf(Color.WHITE);
    private ColorStateList markedColor;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.left_drawer, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // get "marked" color
        int primary = getActivity().getResources().getColor(R.color.primary);
        markedColor = ColorStateList.valueOf(primary);

        // initialize views
        itemViews = new HashMap<>();
        for (int action : actions) {
            TextView itemView = (TextView) view.findViewById(action);
            itemViews.put(action, itemView);

            defaultColor = itemView.getTextColors();

            itemView.setOnClickListener(v -> onActionClicked(action));
        }

        deselect();
    }

    private void deselect() {
        for (TextView view : itemViews.values())
            view.setTextColor(defaultColor);
    }

    public void select(int action) {
        deselect();

        TextView view = itemViews.get(action);
        if (view != null)
            view.setTextColor(markedColor);
    }

    private void onActionClicked(int id) {
        select(id);

        if (getActivity() instanceof OnDrawerActionListener)
            ((OnDrawerActionListener) getActivity()).onActionClicked(id);
    }

    public interface OnDrawerActionListener {
        /**
         * Called if a drawer action was clicked.
         *
         * @param action The action id that was clicked.
         */
        void onActionClicked(int action);
    }


    private final int[] actions = {
            R.id.action_feed_promoted,
            R.id.action_feed_new,
            R.id.action_upload,
            R.id.action_messages,
            R.id.action_favorites
    };
}
