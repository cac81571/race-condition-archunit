package com.example.archrules.jakartaee.fixtures.good;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.ejb.Singleton;

import com.example.archrules.jakartaee.fixtures.AllowStatefulField;

/** 【OK】共有してよい状態に @AllowStatefulField を付けて許可したもの（フィールド・メソッド・クラスの各パターン） */
@Singleton
public class AllowListedSingleton {

    @AllowStatefulField("全利用者共通のキャッシュ。ConcurrentHashMap で保護")
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @AllowStatefulField("統計用の通算カウンタ。AtomicLong で保護")
    private final AtomicLong hits = new AtomicLong();

    @AllowStatefulField("夜間バッチで差し替える設定値。volatile")
    private volatile String settings;

    public String get(String key) {
        hits.incrementAndGet();
        return cache.computeIfAbsent(key, k -> k.toUpperCase());
    }

    @AllowStatefulField("管理画面からの全体リロード")
    public void reload(String newSettings) {
        settings = newSettings;
        cache.clear();
    }

    @AllowStatefulField("クラスごと除外する例")
    @Singleton
    public static class WholeClassAllowed {
        private String anything;
        public void set(String v) { anything = v; }
    }
}
