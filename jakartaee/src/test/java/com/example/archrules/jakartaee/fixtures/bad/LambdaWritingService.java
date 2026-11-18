package com.example.archrules.jakartaee.fixtures.bad;

import java.util.List;

import jakarta.ejb.Stateless;

/**
 * 【NG】ラムダ（合成メソッド）からの書き込み。ラムダ本体は lambda$xxx という合成メソッドになるが、
 * ルール B はアクセス元を問わず自クラスのフィールドへの書き込みをすべて拾う。
 * 期待: A=1（last）/ B=1（last）
 */
@Stateless
public class LambdaWritingService {

    private String last;   // NG

    public void each(List<String> items) {
        items.forEach(i -> last = i);   // NG
    }
}
