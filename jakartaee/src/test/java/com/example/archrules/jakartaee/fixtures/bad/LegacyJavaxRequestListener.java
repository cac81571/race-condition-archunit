package com.example.archrules.jakartaee.fixtures.bad;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;

/**
 * 【NG】javax.servlet.ServletRequestListener（Java EE 8 以前）でも、アノテーション無しで検出される。
 * 期待: A=1（uri）/ B=1（uri）
 */
public class LegacyJavaxRequestListener implements ServletRequestListener {

    private String uri;   // NG

    @Override
    public void requestInitialized(ServletRequestEvent event) {
        uri = event.getServletRequest().getRemoteAddr();
    }

    @Override
    public void requestDestroyed(ServletRequestEvent event) {
    }
}
