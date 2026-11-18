package com.mycompany.myapp.arch;   // ← 自分のプロジェクトに合わせる

import com.example.archrules.jakartaee.JakartaEeRaceConditionRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

@AnalyzeClasses(packages = "com.mycompany.myapp", importOptions = ImportOption.DoNotIncludeTests.class)
class RaceConditionTest {

    @ArchTest
    static final ArchTests レースコンディション対策 = ArchTests.in(JakartaEeRaceConditionRules.class);
}
