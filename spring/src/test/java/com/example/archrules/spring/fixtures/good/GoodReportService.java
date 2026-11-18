package com.example.archrules.spring.fixtures.good;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.example.archrules.spring.fixtures.ProxiedPrototypeBuilder;
import com.example.archrules.spring.fixtures.ReportBuilder;

/** 【OK】prototype は ObjectProvider で毎回取得する／proxyMode 付き prototype は直接注入してよい */
@Service
public class GoodReportService {

    private final ObjectProvider<ReportBuilder> builders;
    private final ProxiedPrototypeBuilder proxied;

    public GoodReportService(ObjectProvider<ReportBuilder> builders, ProxiedPrototypeBuilder proxied) {
        this.builders = builders;
        this.proxied = proxied;
    }

    public String report(String title) {
        proxied.line(title);
        return builders.getObject().title(title).line("...").build();
    }
}
