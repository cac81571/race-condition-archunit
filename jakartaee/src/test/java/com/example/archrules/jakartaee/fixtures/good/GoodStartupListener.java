package com.example.archrules.jakartaee.fixtures.good;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import com.example.archrules.jakartaee.fixtures.AllowStatefulField;

/**
 * 【OK】起動時に 1 回だけ埋める共有設定。contextInitialized / contextDestroyed は B・C の対象外で、
 * フィールドは @AllowStatefulField で A からも外している。
 */
@WebListener
public class GoodStartupListener implements ServletContextListener {

    @AllowStatefulField("起動時に1回だけ読むアプリケーション設定。読み取り専用")
    private final Map<String, String> settings = new ConcurrentHashMap<>();

    @Override
    public void contextInitialized(ServletContextEvent event) {
        settings.put("app", event.getServletContext().getContextPath());
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        settings.clear();
    }
}
