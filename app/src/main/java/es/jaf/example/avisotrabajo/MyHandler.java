package es.jaf.example.avisotrabajo;

import android.os.Handler;
import android.os.Message;

public class MyHandler extends Handler {
    private final OverlayShowingService service;

    MyHandler(OverlayShowingService service) {
        this.service = service;
    }

    @Override
    public void handleMessage( Message msg) {
        service.handleMessage(msg);
    }
}
