package com.carlink.vehicle;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

/** Renders the same TBT view on an Android secondary display when one exists. */
public final class ClusterPresentation extends Presentation {
    private final NavigationClusterView clusterView;

    public ClusterPresentation(Context outerContext, Display display, NavigationInfo info) {
        super(outerContext, display);
        clusterView = new NavigationClusterView(getContext());
        clusterView.setOutputLabel("ANDROID 副屏");
        clusterView.setNavigationInfo(info);
    }

    public void update(NavigationInfo info) {
        clusterView.setNavigationInfo(info);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        if (window != null) {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setBackgroundDrawableResource(android.R.color.black);
        }
        clusterView.setBackgroundColor(Color.BLACK);
        setContentView(clusterView);
    }
}
