package com.example.archrules.jakartaee.fixtures.good;

import java.util.List;
import java.util.logging.Logger;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.example.archrules.jakartaee.fixtures.OrderMapper;

/** 【OK】検索条件は引数で受け取り、フィールドには注入・定数・ロガーしか持たない */
@Stateless
public class GoodOrderSearchService {

    private static final Logger LOG = Logger.getLogger(GoodOrderSearchService.class.getName());
    private static final int PAGE_SIZE = 50;

    @Inject
    private OrderMapper orderMapper;

    @PersistenceContext
    private EntityManager em;

    public List<String> search(String condition, int page) {
        LOG.fine(() -> "search page=" + page + " size=" + PAGE_SIZE);
        return orderMapper.selectByCondition(condition);
    }
}
