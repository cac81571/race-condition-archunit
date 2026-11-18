package com.mycompany.myapp.arch;   // ← 自分のプロジェクトに合わせる

import com.example.archrules.spring.SpringRaceConditionRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** ルールを 1 つずつ選ぶ書き方（不要なルールは行ごと消せばよい） */
@AnalyzeClasses(packages = "com.mycompany.myapp", importOptions = ImportOption.DoNotIncludeTests.class)
class RaceConditionTest {

    @ArchTest static final ArchRule A_フィールド宣言 = SpringRaceConditionRules.SINGLETON_ONLY_SAFE_FIELDS;
    @ArchTest static final ArchRule B_フィールド代入 = SpringRaceConditionRules.SINGLETON_NO_FIELD_WRITES;
    @ArchTest static final ArchRule C_コレクション変更 = SpringRaceConditionRules.SINGLETON_NO_COLLECTION_MUTATION;
    @ArchTest static final ArchRule D_prototype直接注入 = SpringRaceConditionRules.NO_DIRECT_PROTOTYPE_INJECTION;
}
