package com.example.archrules.jakartaee.fixtures.good;

import java.util.logging.Logger;

import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.annotation.WebListener;

/** 【OK】リクエストの値はフィールドに持たず、ロガーだけを持つ */
@WebListener
public class GoodRequestListener implements ServletRequestListener {

    private static final Logger LOG = Logger.getLogger(GoodRequestListener.class.getName());

    @Override
    public void requestInitialized(ServletRequestEvent event) {
        LOG.fine(() -> "request " + event.getServletRequest().getRemoteAddr());
    }
}
