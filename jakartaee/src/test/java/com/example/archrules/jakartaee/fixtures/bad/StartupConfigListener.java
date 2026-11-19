package com.example.archrules.jakartaee.fixtures.bad;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * 【NG】ServletContextListener。contextInitialized での初期化は B・C では免除するが、
 * それ以外のメソッドでの代入・追加は検出する。フィールド宣言自体は A で検出する。
 * 期待: A=2（latestKey, loadedKeys）/ B=1（record の latestKey。contextInitialized は免除）
 *      / C=1（record の loadedKeys.add。contextInitialized は免除）
 */
@WebListener
public class StartupConfigListener implements ServletContextListener {

    private String latestKey;                                  // NG

    private final List<String> loadedKeys = new ArrayList<>(); // NG

    @Override
    public void contextInitialized(ServletContextEvent event) {
        latestKey = "init";
        loadedKeys.add("init");
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
    }

    public void record(String key) {
        latestKey = key;
        loadedKeys.add(key);
    }
}
