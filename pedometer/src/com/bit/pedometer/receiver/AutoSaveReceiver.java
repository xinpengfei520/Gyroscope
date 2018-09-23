package com.bit.pedometer.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.bit.pedometer.service.AutoSaveService;

public class AutoSaveReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        Toast.makeText(context, "date changes", Toast.LENGTH_SHORT).show();
        context.startService(new Intent(context, AutoSaveService.class));
    }

}
