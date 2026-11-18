package com.example.archrules.jakartaee.fixtures.good;

import java.util.ArrayList;
import java.util.List;

import jakarta.ejb.Stateful;

/** 【対象外】@Stateful EJB は利用者ごとのインスタンスなので状態を持ってよい */
@Stateful
public class ShoppingCartBean {
    private final List<String> items = new ArrayList<>();
    private int total;

    public void add(String item, int price) {
        items.add(item);
        total += price;
    }
}
