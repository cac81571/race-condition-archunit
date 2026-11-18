package com.example.archrules.jakartaee.fixtures.good;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.RequestScoped;

/** 【対象外】@RequestScoped はリクエストごとに別インスタンスなので状態を持ってよい */
@RequestScoped
public class RequestScopedSearchCondition {
    private String keyword;
    private final List<String> selected = new ArrayList<>();

    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getKeyword() { return keyword; }
    public void select(String id) { selected.add(id); }
}
