package com.mycompany.myapp.arch;   // ← 自分のプロジェクトに合わせる

import com.example.archrules.spring.SpringStateLeakRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** ルールを 1 つずつ選ぶ書き方（不要なルールは行ごと消せばよい） */
@AnalyzeClasses(packages = "com.mycompany.myapp", importOptions = ImportOption.DoNotIncludeTests.class)
class StateLeakTest {

    @ArchTest static final ArchRule A_フィールド宣言 = SpringStateLeakRules.SINGLETON_ONLY_SAFE_FIELDS;
    @ArchTest static final ArchRule B_フィールド代入 = SpringStateLeakRules.SINGLETON_NO_FIELD_WRITES;
    @ArchTest static final ArchRule C_コレクション変更 = SpringStateLeakRules.SINGLETON_NO_COLLECTION_MUTATION;
    @ArchTest static final ArchRule D_prototype直接注入 = SpringStateLeakRules.NO_DIRECT_PROTOTYPE_INJECTION;
}
