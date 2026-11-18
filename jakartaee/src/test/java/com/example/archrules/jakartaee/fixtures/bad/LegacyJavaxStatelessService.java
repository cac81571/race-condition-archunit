package com.example.archrules.jakartaee.fixtures.bad;

import javax.ejb.Stateless;

/**
 * 【NG】javax.ejb.Stateless（Java EE 8 以前）でも検出されることの確認用。
 * 期待: A=1（lastKeyword）/ B=1（lastKeyword）
 */
@Stateless
public class LegacyJavaxStatelessService {

    private String lastKeyword;   // NG

    public String search(String keyword) {
        lastKeyword = keyword;    // NG
        return lastKeyword;
    }
}
