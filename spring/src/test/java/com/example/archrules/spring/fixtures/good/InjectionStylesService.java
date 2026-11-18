package com.example.archrules.spring.fixtures.good;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import com.example.archrules.spring.fixtures.OrderService;

/**
 * 【OK】フィールド注入・@Value・@Autowired セッター・Aware コールバック。
 * （@PostConstruct / afterPropertiesSet で代入するフィールドはルール B では許可されるが、
 *   ルール A では注入ではないので @AllowStatefulField が必要 → README の「制約」参照）
 */
@Service
public class InjectionStylesService implements ApplicationContextAware {

    @Autowired
    private OrderService orderService;

    @Value("${app.page-size:20}")
    private int pageSize;

    private OrderService viaSetter;
    private ApplicationContext context;

    @Autowired
    public void setViaSetter(OrderService s) { this.viaSetter = s; }

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException { this.context = ctx; }

    public String code(String k) { return k + pageSize + context + viaSetter + orderService; }
}
