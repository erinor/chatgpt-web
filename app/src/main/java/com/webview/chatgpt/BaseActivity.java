package com.webview.chatgpt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class BaseActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    protected void initSharedTabs() {
        View t1 = findViewById(R.id.tab1);
        View t2 = findViewById(R.id.tab2);
        View t3 = findViewById(R.id.tab3);
        View t4 = findViewById(R.id.tab4);
        View t5 = findViewById(R.id.tab5);

        if (t1 != null) t1.setOnClickListener(v -> openTab(Tab1Activity.class));
        if (t2 != null) t2.setOnClickListener(v -> openTab(Tab2Activity.class));
        if (t3 != null) t3.setOnClickListener(v -> openTab(Tab3Activity.class));
        if (t4 != null) t4.setOnClickListener(v -> openTab(Tab4Activity.class));
        if (t5 != null) t5.setOnClickListener(v -> openTab(Tab5Activity.class));
    }

    // FLAG_ACTIVITY_REORDER_TO_FRONT so existing activity is reused
    private void openTab(Class<?> cls) {
        Intent i = new Intent(this, cls);
        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
        // removing default animations to reduce flicker
        overridePendingTransition(0, 0);
    }

    protected void highlightCurrentTab(int index) {
        View t1 = findViewById(R.id.tab1);
        View t2 = findViewById(R.id.tab2);
        View t3 = findViewById(R.id.tab3);
        View t4 = findViewById(R.id.tab4);
        View t5 = findViewById(R.id.tab5);

        if (t1 != null) t1.setSelected(false);
        if (t2 != null) t2.setSelected(false);
        if (t3 != null) t3.setSelected(false);
        if (t4 != null) t4.setSelected(false);
        if (t5 != null) t5.setSelected(false);

        switch (index) {
            case 1:
                if (t1 != null) t1.setSelected(true);
                break;
            case 2:
                if (t2 != null) t2.setSelected(true);
                break;
            case 3:
                if (t3 != null) t3.setSelected(true);
                break;
            case 4:
                if (t4 != null) t4.setSelected(true);
                break;
            case 5:
                if (t5 != null) t5.setSelected(true);
                break;
        }
    }
}
