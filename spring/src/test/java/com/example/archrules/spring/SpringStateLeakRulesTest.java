package com.example.archrules.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

/**
 * ルール自体のテスト。fixtures/bad（わざと危ない書き方）で違反が出ること、
 * fixtures/good（正しい書き方・対象外の Bean）で違反が出ないことを確認する。
 */
class SpringStateLeakRulesTest {

    static JavaClasses bad;
    static JavaClasses good;

    @BeforeAll
    static void importFixtures() {
        bad = new ClassFileImporter().importPackages("com.example.archrules.spring.fixtures.bad");
        good = new ClassFileImporter().importPackages("com.example.archrules.spring.fixtures.good");
    }

    static List<String> violations(ArchRule rule, JavaClasses classes) {
        return rule.evaluate(classes).getFailureReport().getDetails();
    }

    static Stream<Arguments> expectedViolationCounts() {
        return Stream.of(
                // A: AccessCounter 1, BadReportService 1, SearchHistoryService 1, SharedCart 1, StatefulOrderController 2
                Arguments.of("SINGLETON_ONLY_SAFE_FIELDS", SpringStateLeakRules.SINGLETON_ONLY_SAFE_FIELDS, 6),
                // B: AccessCounter 1, SharedCart 1, StatefulOrderController 2
                Arguments.of("SINGLETON_NO_FIELD_WRITES", SpringStateLeakRules.SINGLETON_NO_FIELD_WRITES, 4),
                // C: SearchHistoryService 1, SharedCart 1
                Arguments.of("SINGLETON_NO_COLLECTION_MUTATION", SpringStateLeakRules.SINGLETON_NO_COLLECTION_MUTATION, 2),
                // D: BadReportService 1
                Arguments.of("NO_DIRECT_PROTOTYPE_INJECTION", SpringStateLeakRules.NO_DIRECT_PROTOTYPE_INJECTION, 1));
    }

    @ParameterizedTest(name = "{0}: 危ない書き方で {2} 件検出する")
    @MethodSource("expectedViolationCounts")
    void 危ない書き方を検出する(String name, ArchRule rule, int expected) {
        List<String> v = violations(rule, bad);
        assertEquals(expected, v.size(), () -> name + " の違反:\n" + String.join("\n", v));
    }

    @ParameterizedTest(name = "{0}: 正しい書き方・対象外の Bean では違反なし")
    @MethodSource("expectedViolationCounts")
    void 正しい書き方では違反なし(String name, ArchRule rule, int ignored) {
        List<String> v = violations(rule, good);
        assertTrue(v.isEmpty(), () -> name + " で誤検出:\n" + String.join("\n", v));
    }

    @Test
    @DisplayName("違反メッセージに種類・クラス・フィールド・行番号が日本語で出る")
    void 違反メッセージ() {
        List<String> writes = violations(SpringStateLeakRules.SINGLETON_NO_FIELD_WRITES, bad);
        assertTrue(writes.stream().anyMatch(m -> m.startsWith(
                "【ステートリーク】[フィールド代入] StatefulOrderController.search() が フィールド StatefulOrderController.keyword に代入しています (StatefulOrderController.java:")),
                () -> String.join("\n", writes));

        List<String> fields = violations(SpringStateLeakRules.SINGLETON_ONLY_SAFE_FIELDS, bad);
        assertTrue(fields.stream().anyMatch(m -> m.contains("SearchHistoryService のフィールド history（型: List）: final だがコンストラクタ注入ではない")), fields::toString);
        assertTrue(fields.stream().anyMatch(m -> m.contains("orderService") && m.contains("@Qualifier / @Named だけでは注入されない")), fields::toString);
        assertFalse(fields.stream().anyMatch(m -> m.contains("StatefulOrderController のフィールド orderService")), "コンストラクタ注入の final は対象外");

        List<String> proto = violations(SpringStateLeakRules.NO_DIRECT_PROTOTYPE_INJECTION, bad);
        assertTrue(proto.get(0).contains("[prototype 直接注入]") && proto.get(0).contains("コンストラクタ引数に prototype の ReportBuilder"), proto::toString);
    }

    @Test
    @DisplayName("@ApplicationScope は対象、@RequestScope / @SessionScope / prototype / @ConfigurationProperties は対象外")
    void スコープ判定() {
        assertTrue(violations(SpringStateLeakRules.SINGLETON_NO_FIELD_WRITES, bad).stream().anyMatch(m -> m.contains("SharedCart")));
        assertTrue(SpringStateLeakRules.areSharedSpringBeans().test(good.get(
                com.example.archrules.spring.fixtures.good.GoodOrderController.class)));
        for (String nested : List.of("PrototypeCsvWriter", "RequestScopedCondition", "RequestScopeAnnotated",
                "SessionCart", "AppProperties", "WholeClassAllowed")) {
            assertFalse(SpringStateLeakRules.areSharedSpringBeans().test(
                    good.get("com.example.archrules.spring.fixtures.good.ScopedBeans$" + nested)), nested);
        }
    }

    @Test
    @DisplayName("ルールクラスに @ArchTest 付きの ArchRule が 4 つある（ArchTests.in で全部実行できる）")
    void ArchTestsで全ルール() {
        long count = Stream.of(SpringStateLeakRules.class.getFields())
                .filter(f -> ArchRule.class.isAssignableFrom(f.getType()))
                .filter(f -> f.isAnnotationPresent(com.tngtech.archunit.junit.ArchTest.class))
                .count();
        assertEquals(4, count);
    }
}
