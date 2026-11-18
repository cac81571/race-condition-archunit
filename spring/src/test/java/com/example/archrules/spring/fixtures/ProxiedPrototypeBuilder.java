package com.example.archrules.spring.fixtures;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

/** prototype + proxyMode = TARGET_CLASS。直接注入してもプロキシ経由で毎回新しいインスタンスになるので OK */
@Component
@Scope(value = "prototype", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ProxiedPrototypeBuilder {
    private final List<String> lines = new ArrayList<>();
    public void line(String l) { lines.add(l); }
}
