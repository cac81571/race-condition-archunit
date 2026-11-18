package com.example.archrules.spring.fixtures.good;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.archrules.spring.fixtures.OrderService;

/** 【OK】コンストラクタ注入の final・ロガー・定数だけ。値は引数とローカル変数で受け渡す */
@Controller
public class GoodOrderController {

    private final Log log = LogFactory.getLog(GoodOrderController.class);   // final のインスタンスロガーも OK
    private static final int PAGE_SIZE = 20;

    private final OrderService orderService;

    public GoodOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders")
    public List<String> search(@RequestParam String keyword) {
        log.debug("search " + keyword + " size=" + PAGE_SIZE);
        return orderService.search("guest", keyword);
    }
}
