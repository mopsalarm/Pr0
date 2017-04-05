package com.pr0gramm.app.ui.intro;

import android.os.Bundle;

import com.github.paolorotolo.appintro.AppIntroBaseFragment;
import com.pr0gramm.app.R;

public final class GenericAppIntroFragment extends AppIntroBaseFragment {
    public static GenericAppIntroFragment newInstance(CharSequence title, CharSequence description, int imageDrawable, int bgColor) {
        return newInstance(title, description, imageDrawable, bgColor, 0, 0);
    }

    public static GenericAppIntroFragment newInstance(CharSequence title, CharSequence description, int imageDrawable, int bgColor, int titleColor, int descColor) {
        GenericAppIntroFragment slide = new GenericAppIntroFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title.toString());
        args.putString(ARG_DESC, description.toString());
        args.putInt(ARG_DRAWABLE, imageDrawable);
        args.putInt(ARG_BG_COLOR, bgColor);
        args.putInt(ARG_TITLE_COLOR, titleColor);
        args.putInt(ARG_DESC_COLOR, descColor);
        slide.setArguments(args);
        return slide;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.intro_fragment_generic;
    }
}
