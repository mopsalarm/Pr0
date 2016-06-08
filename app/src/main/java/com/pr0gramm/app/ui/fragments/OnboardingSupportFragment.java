package com.pr0gramm.app.ui.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.ramotion.paperonboarding.PaperOnboardingEngine;
import com.ramotion.paperonboarding.PaperOnboardingPage;
import com.ramotion.paperonboarding.listeners.PaperOnboardingOnRightOutListener;

import java.util.ArrayList;

public class OnboardingSupportFragment extends Fragment {
    private PaperOnboardingOnRightOutListener mOnRightOutListener = () -> getFragmentManager().popBackStack();
    private ArrayList<PaperOnboardingPage> mElements;

    public static OnboardingSupportFragment newInstance(ArrayList<PaperOnboardingPage> elements) {
        Bundle args = new Bundle();
        args.putSerializable("elements", elements);

        OnboardingSupportFragment fragment = new OnboardingSupportFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (this.getArguments() != null) {
            this.mElements = (ArrayList) this.getArguments().get("elements");
        }

    }

    @Nullable
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(com.ramotion.paperonboarding.R.layout.onboarding_main_layout, container, false);
        PaperOnboardingEngine mPaperOnboardingEngine = new PaperOnboardingEngine(view.findViewById(com.ramotion.paperonboarding.R.id.onboardingRootView), this.mElements, this.getActivity().getApplicationContext());
        mPaperOnboardingEngine.setOnRightOutListener(this.mOnRightOutListener);
        return view;
    }

    public void setElements(ArrayList<PaperOnboardingPage> elements) {
        this.mElements = elements;
    }

    public ArrayList<PaperOnboardingPage> getElements() {
        return this.mElements;
    }

    public void setOnRightOutListener(PaperOnboardingOnRightOutListener onRightOutListener) {
        this.mOnRightOutListener = onRightOutListener;
    }
}
