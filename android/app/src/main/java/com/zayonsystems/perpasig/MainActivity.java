package com.zayonsystems.perpasig;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Local plugin must be registered before the bridge starts.
        registerPlugin(PerMediaPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
