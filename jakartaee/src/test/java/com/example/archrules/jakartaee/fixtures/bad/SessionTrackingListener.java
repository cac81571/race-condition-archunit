package com.example.archrules.jakartaee.fixtures.bad;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/**
 * 【NG】@WebListener が無くても HttpSessionListener 実装なら対象（web.xml や addListener での登録を想定）。
 * 期待: A=1（lastSessionId）/ B=1（lastSessionId）
 */
public class SessionTrackingListener implements HttpSessionListener {

    private String lastSessionId;   // NG

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        lastSessionId = event.getSession().getId();
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
    }
}
