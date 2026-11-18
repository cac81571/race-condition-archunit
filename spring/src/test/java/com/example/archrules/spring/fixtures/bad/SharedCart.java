package com.example.archrules.spring.fixtures.bad;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.ApplicationScope;

/** 【NG】@ApplicationScope はアプリ全体で 1 個なので対象。期待: A=1 / B=1 / C=1 */
@Component
@ApplicationScope
public class SharedCart {

    private List<String> items = new ArrayList<>();   // NG（A）

    public void add(String item) {
        items.add(item);   // NG（C）
    }

    public void reset() {
        items = new ArrayList<>();   // NG（B）
    }
}
