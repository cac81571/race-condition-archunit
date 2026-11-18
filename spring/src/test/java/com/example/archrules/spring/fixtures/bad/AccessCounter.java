package com.example.archrules.spring.fixtures.bad;

import org.springframework.stereotype.Component;

/** 【NG】static 非 final のカウンタ。期待: A=1 / B=1 */
@Component
public class AccessCounter {

    private static int count;   // NG（A）

    public int countUp() {
        return ++count;          // NG（B）
    }
}
