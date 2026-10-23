package com.vyuhrachna.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** સમય થતાં SMS મોકલે; ફોન ચાલુ થાય / એપ અપડેટ થાય ત્યારે ફરી ગોઠવે */
public class SchedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Context c = context.getApplicationContext();
        String a = intent == null ? "" : String.valueOf(intent.getAction());
        if (SmsScheduler.ACTION.equals(a)) {
            final PendingResult pr = goAsync();
            new Thread(() -> {
                try {
                    SmsScheduler.runDue(c);
                } finally {
                    pr.finish();
                }
            }).start();
        } else {
            SmsScheduler.reschedule(c);
        }
    }
}
