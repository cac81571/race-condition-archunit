package com.example.archrules.jakartaee.fixtures;

import java.util.List;

/** フィクスチャ用の MyBatis Mapper 風インターフェース */
public interface OrderMapper {
    List<String> selectByCondition(String condition);
    List<String> selectAllDepartments();
}
