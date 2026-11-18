package com.example.archrules.jakartaee.fixtures.bad;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * 【NG】@ApplicationScoped にログインユーザーを保持している。
 * 期待: A=2（loginId, operationLog）/ B=1（loginId）/ C=1（operationLog.append の連鎖は 1 件にまとめる）
 */
@ApplicationScoped
public class CurrentUserHolder {

    private String loginId;                                          // NG

    private final StringBuilder operationLog = new StringBuilder(); // NG

    public void login(String loginId) {
        this.loginId = loginId;
        operationLog.append(loginId).append(" logged in\n");   // 連鎖しても 1 件
    }

    public String getLoginId() {
        return loginId;
    }
}
