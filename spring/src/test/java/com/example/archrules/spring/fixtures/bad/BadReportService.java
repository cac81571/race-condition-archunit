package com.example.archrules.spring.fixtures.bad;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.example.archrules.spring.fixtures.OrderService;
import com.example.archrules.spring.fixtures.ReportBuilder;

/** 【NG】prototype の直接注入（D=1）と @Qualifier だけのフィールド（A=1） */
@Service
public class BadReportService {

    private final ReportBuilder builder;   // NG（D）

    @Qualifier("orderService")
    private OrderService orderService;     // NG（A）

    public BadReportService(ReportBuilder builder) {
        this.builder = builder;
    }

    public String report(String title) {
        return builder.title(title).line("...").build();
    }
}
