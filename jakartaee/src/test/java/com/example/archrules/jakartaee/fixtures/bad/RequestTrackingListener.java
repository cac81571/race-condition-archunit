package com.example.archrules.jakartaee.fixtures.bad;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.annotation.WebListener;

/**
 * 【NG】リクエストごとのパスを、全リクエストで共有されるリスナーのフィールドに保持している。
 * 期待: A=2（currentPath, paths）/ B=1（currentPath）/ C=1（paths.add）
 */
@WebListener
public class RequestTrackingListener implements ServletRequestListener {

    private String currentPath;                              // NG

    private final List<String> paths = new ArrayList<>();    // NG

    @Override
    public void requestInitialized(ServletRequestEvent event) {
        currentPath = event.getServletRequest().getRemoteAddr();
        paths.add(currentPath);
    }

    @Override
    public void requestDestroyed(ServletRequestEvent event) {
    }
}
