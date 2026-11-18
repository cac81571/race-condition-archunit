package com.example.archrules.spring.fixtures.bad;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/** 【NG】final の List に検索条件を追加。期待: A=1 / C=1 */
@Service
public class SearchHistoryService {

    private final List<String> history = new ArrayList<>();   // NG（A）

    public List<String> remember(String user, String keyword) {
        history.add(user + ":" + keyword);   // NG（C）
        return List.copyOf(history);
    }
}
