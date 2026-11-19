package com.example.archrules.jakartaee.fixtures.bad;

import jakarta.servlet.ServletContextAttributeEvent;
import jakarta.servlet.ServletContextAttributeListener;
import jakarta.servlet.annotation.WebListener;

/**
 * 【NG】3 インターフェース以外でも @WebListener が付いていれば対象。
 * 期待: A=1（lastName）/ B=1（lastName）
 */
@WebListener
public class AttributeTrackingListener implements ServletContextAttributeListener {

    private String lastName;   // NG

    @Override
    public void attributeAdded(ServletContextAttributeEvent event) {
        lastName = event.getName();
    }
}
