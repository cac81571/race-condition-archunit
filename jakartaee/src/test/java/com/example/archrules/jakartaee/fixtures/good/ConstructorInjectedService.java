package com.example.archrules.jakartaee.fixtures.good;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.example.archrules.jakartaee.fixtures.OrderMapper;

/** 【OK】@Inject コンストラクタで受け取る final フィールド。業務メソッドではローカル変数だけを使う */
@ApplicationScoped
public class ConstructorInjectedService {

    private final OrderMapper mapper;

    @Inject
    public ConstructorInjectedService(OrderMapper mapper) {
        this.mapper = mapper;
    }

    protected ConstructorInjectedService() {   // CDI のプロキシ用
        this.mapper = null;
    }

    public List<String> search(String condition) {
        List<String> result = new ArrayList<>();   // ローカル変数への add は OK
        result.addAll(mapper.selectByCondition(condition));
        return result;
    }
}
