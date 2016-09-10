package com.pr0gramm.app.ui.intro.slides;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.ui.base.BaseFragment;

import java.util.List;

import butterknife.ButterKnife;

/**
 */
public abstract class ActionItemsSlide extends BaseFragment {
    List<ActionItem> actionItems;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(getLayoutId(), container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView titleView = ButterKnife.findById(view, R.id.title);
        TextView descriptionView = ButterKnife.findById(view, R.id.description);

        if (titleView != null) {
            titleView.setText(getIntroTitle());
        }

        if (descriptionView != null) {
            descriptionView.setText(getIntroDescription());
        }

        actionItems = getIntroActionItems();

        ListView listView = ButterKnife.findById(view, R.id.list);
        listView.setChoiceMode(singleChoice() ? ListView.CHOICE_MODE_SINGLE : ListView.CHOICE_MODE_MULTIPLE);
        listView.setAdapter(new ArrayAdapter<>(getActivity(),
                android.R.layout.simple_list_item_multiple_choice, android.R.id.text1,
                actionItems));

        listView.setOnItemClickListener((parent, view1, position, id) -> {
            ActionItem item = actionItems.get(position);
            if (listView.isItemChecked(position)) {
                item.activate();
            } else {
                item.deactivate();
            }
        });

        for (int idx = 0; idx < actionItems.size(); idx++) {
            if (actionItems.get(idx).enabled()) {
                listView.setItemChecked(idx, true);
            }
        }
    }

    protected boolean singleChoice() {
        return false;
    }

    @Override
    protected void injectComponent(ActivityComponent activityComponent) {
    }

    protected int getLayoutId() {
        return R.layout.intro_fragment_items;
    }

    protected abstract String getIntroTitle();

    protected abstract String getIntroDescription();

    protected abstract List<ActionItem> getIntroActionItems();
}
