package com.example.archrules;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ステートリーク対策ルール（race-condition-rules-jakartaee / race-condition-rules-spring）から
 * 除外するための目印。
 *
 * <ul>
 *   <li>フィールドに付ける: そのフィールドの宣言と書き込みを許可</li>
 *   <li>メソッドに付ける: そのメソッド内でのフィールド書き込みを許可</li>
 *   <li>クラスに付ける: クラス全体をルールの対象外にする</li>
 * </ul>
 *
 * 「全利用者で共有してよい」「スレッドセーフである」と確認済みのものにだけ付け、
 * {@link #value()} に理由を必ず書いてください（レビューで検索しやすくするため）。
 *
 * <p>ルール側は単純名 {@code AllowStatefulField} で判定するので、この artifact を使わずに
 * 自分のプロジェクトの任意のパッケージへ同名のアノテーションを作っても動きます。
 * 依存ライブラリは一切ありません（RetentionPolicy.CLASS なので実行時にも影響しません）。
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
public @interface AllowStatefulField {
    /** 許可する理由（例: "起動時に読み込む読み取り専用キャッシュ。ConcurrentHashMap で保護"） */
    String value();
}
