package com.pr0gramm.app.ui.upload;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.pr0gramm.app.R;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 */
public class ChooseMediaTypeFragment extends Fragment {
    @Bind(R.id.media_type_image)
    View btnImage;

    @Bind(R.id.media_type_video)
    View btnVideo;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_choose_media_type, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ButterKnife.bind(this, view);

        btnImage.setOnClickListener(v -> openWithType("image/*"));
        btnVideo.setOnClickListener(v -> openWithType("video/*"));
    }

    private void openWithType(String type) {
        Listener listener = (Listener) getActivity();
        listener.onMediaTypeChosen(type);
    }

    public interface Listener {
        void onMediaTypeChosen(String type);
    }
}

