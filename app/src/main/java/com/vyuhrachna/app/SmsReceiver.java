package com.vyuhrachna.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.util.LinkedHashMap;
import java.util.Map;

/** આવેલા SMS માંથી ફક્ત કંપનીના રિપ્લાય પકડે છે (બીજા અંગત SMS નહીં). */
public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null || msgs.length == 0) return;
        Map<String, StringBuilder> byFrom = new LinkedHashMap<>();
        long ts = System.currentTimeMillis();
        for (SmsMessage m : msgs) {
            if (m == null) continue;
            String from = m.getDisplayOriginatingAddress();
            if (from == null) from = "";
            StringBuilder sb = byFrom.get(from);
            if (sb == null) {
                sb = new StringBuilder();
                byFrom.put(from, sb);
            }
            String body = m.getDisplayMessageBody();
            if (body != null) sb.append(body);
            ts = m.getTimestampMillis();
        }
        for (Map.Entry<String, StringBuilder> e : byFrom.entrySet()) {
            Store.incoming(context.getApplicationContext(), e.getKey(), e.getValue().toString(), ts);
        }
    }
}
