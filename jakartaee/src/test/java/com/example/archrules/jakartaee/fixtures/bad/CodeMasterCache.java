package com.example.archrules.jakartaee.fixtures.bad;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;

import com.example.archrules.jakartaee.fixtures.AllowStatefulField;
import com.example.archrules.jakartaee.fixtures.OrderMapper;

/**
 * 【NG と OK の混在】@Singleton EJB。
 * 期待: A=2（lastSelectedDept, lastLoginUser）/ B=1（lastLoginUser）/ C=1（lastSelectedDept.put）
 * departmentNames は @AllowStatefulField、@PostConstruct 内の put は対象外。
 */
@Singleton
public class CodeMasterCache {

    @Inject
    private OrderMapper mapper;

    @AllowStatefulField("起動時に読み込む読み取り専用マスタ")
    private final Map<String, String> departmentNames = new ConcurrentHashMap<>();

    private final Map<String, String> lastSelectedDept = new HashMap<>();   // NG

    private String lastLoginUser;                                          // NG

    @PostConstruct
    void load() {
        for (String d : mapper.selectAllDepartments()) {
            departmentNames.put(d, d);   // OK: @PostConstruct 内
        }
    }

    public String name(String deptId, String loginUser) {
        lastLoginUser = loginUser;                 // NG
        lastSelectedDept.put(loginUser, deptId);   // NG
        return departmentNames.get(deptId);
    }
}
