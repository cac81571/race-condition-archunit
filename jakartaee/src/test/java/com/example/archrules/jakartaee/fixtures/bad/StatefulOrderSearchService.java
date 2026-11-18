package com.example.archrules.jakartaee.fixtures.bad;

import java.util.ArrayList;
import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import com.example.archrules.jakartaee.fixtures.OrderMapper;

/**
 * 【NG】画面で入力した検索条件を @Stateless のフィールドに保持している。
 * 期待: A=4（condition, page, searchHistory, searchCount）/ B=3（condition, page, searchCount）/ C=1（searchHistory.add）
 */
@Stateless
public class StatefulOrderSearchService {

    @Inject
    private OrderMapper orderMapper;          // OK: 注入

    private String condition;                 // NG: 画面の入力値を保持

    private int page;                         // NG: 画面ごとの状態

    private final List<String> searchHistory = new ArrayList<>(); // NG: 呼ぶたびに追加される

    private static int searchCount;           // NG: static 非 final

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public List<String> search(int page) {
        this.page = page;
        searchCount++;
        searchHistory.add(condition);
        return orderMapper.selectByCondition(condition);
    }
}
