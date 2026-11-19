package com.mycompany.myapp.arch;   // ← 自分のプロジェクトに合わせる

import com.example.archrules.jakartaee.JakartaEeStateLeakRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

@AnalyzeClasses(packages = "com.mycompany.myapp", importOptions = ImportOption.DoNotIncludeTests.class)
class StateLeakTest {

    @ArchTest
    static final ArchTests ステートリーク対策 = ArchTests.in(JakartaEeStateLeakRules.class);
}
