package com.example.archrules.spring.fixtures.bad;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.archrules.spring.fixtures.OrderService;

/** 【NG】リクエストパラメータをコントローラのフィールドに保持。期待: A=2 / B=2 */
@Controller
public class StatefulOrderController {

    private final OrderService orderService;
    private String keyword;          // NG（A）
    private int page;                // NG（A）

    public StatefulOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/bad/orders")
    public List<String> search(@RequestParam String keyword, @RequestParam(defaultValue = "0") int page) {
        this.keyword = keyword;      // NG（B）
        this.page = page;            // NG（B）
        return orderService.search("guest", this.keyword + this.page);
    }
}
