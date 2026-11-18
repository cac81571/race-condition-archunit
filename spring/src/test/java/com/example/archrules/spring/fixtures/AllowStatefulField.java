package com.example.archrules.spring.fixtures;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** テスト用の除外アノテーション。ルールは単純名で判定するので、どのパッケージに作っても効くことを確認する */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
public @interface AllowStatefulField {
    String value();
}
