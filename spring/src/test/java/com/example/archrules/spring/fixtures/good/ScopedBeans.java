package com.example.archrules.spring.fixtures.good;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.context.annotation.SessionScope;

import com.example.archrules.spring.fixtures.AllowStatefulField;

/** 【対象外】共有されないスコープ・設定クラス・除外アノテーション付きクラスは状態を持ってよい */
public class ScopedBeans {

    @Component
    @Scope("prototype")
    public static class PrototypeCsvWriter {
        private final StringBuilder buf = new StringBuilder();
        public void row(String... cols) { buf.append(String.join(",", cols)); }
    }

    @Component
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    public static class RequestScopedCondition {
        private String keyword;
        public void setKeyword(String k) { keyword = k; }
    }

    @Component
    @RequestScope
    public static class RequestScopeAnnotated {
        private final List<String> ids = new ArrayList<>();
        public void select(String id) { ids.add(id); }
    }

    @Component
    @SessionScope
    public static class SessionCart {
        private final List<String> items = new ArrayList<>();
        public void add(String item) { items.add(item); }
    }

    @Component
    @ConfigurationProperties(prefix = "app")
    public static class AppProperties {
        private String title;
        public void setTitle(String t) { title = t; }
        public String getTitle() { return title; }
    }

    @Component
    @AllowStatefulField("クラスごと除外する例")
    public static class WholeClassAllowed {
        private String anything;
        public void set(String v) { anything = v; }
    }

    @Component
    public static class FieldAllowed {
        @AllowStatefulField("全利用者共通の読み取り専用キャッシュ")
        private final java.util.Map<String, String> cache = new java.util.concurrent.ConcurrentHashMap<>();
        public String get(String k) { return cache.computeIfAbsent(k, String::toUpperCase); }
    }
}
