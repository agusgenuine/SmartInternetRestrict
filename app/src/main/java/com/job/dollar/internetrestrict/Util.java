package com.job.dollar.internetrestrict;


import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.Set;

public class Util {
    public static void logExtras(String tag, Intent intent) {
        logBundle(tag, intent.getExtras());
    }

    public static void logBundle(String tag, Bundle data) {
        if (data != null) {
            Set<String> keys = data.keySet();
            StringBuilder stringBuilder = new StringBuilder();
            for (String key : keys)
                stringBuilder.append(key).append("=").append(data.get(key)).append("\r\n");
            Log.d(tag, stringBuilder.toString());
        }
    }
}
