package com.example.archrules.jakartaee;

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
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

/**
 * ルール自体のテスト。fixtures/bad（わざと危ない書き方）で違反が出ること、
 * fixtures/good（正しい書き方・対象外の Bean）で違反が出ないことを確認する。
 */
class JakartaEeRaceConditionRulesTest {

    static final String BAD = "com.example.archrules.jakartaee.fixtures.bad";
    static final String GOOD = "com.example.archrules.jakartaee.fixtures.good";

    static JavaClasses bad;
    static JavaClasses good;

    @BeforeAll
    static void importFixtures() {
        bad = new ClassFileImporter().importPackages(BAD);
        good = new ClassFileImporter().importPackages(GOOD);
    }

    static List<String> violations(ArchRule rule, JavaClasses classes) {
        EvaluationResult result = rule.evaluate(classes);
        return result.getFailureReport().getDetails();
    }

    static Stream<Arguments> expectedViolationCounts() {
        return Stream.of(
                // @Stateless: StatefulOrderSearchService(4/3/1) + LegacyJavax(1/1/0) + LambdaWriting(1/1/0)
                Arguments.of("STATELESS_ONLY_SAFE_FIELDS", JakartaEeRaceConditionRules.STATELESS_ONLY_SAFE_FIELDS, 6),
                Arguments.of("STATELESS_NO_FIELD_WRITES", JakartaEeRaceConditionRules.STATELESS_NO_FIELD_WRITES, 5),
                Arguments.of("STATELESS_NO_COLLECTION_MUTATION", JakartaEeRaceConditionRules.STATELESS_NO_COLLECTION_MUTATION, 1),
                // @Singleton EJB: CodeMasterCache(2/1/1)
                Arguments.of("SINGLETON_ONLY_SAFE_FIELDS", JakartaEeRaceConditionRules.SINGLETON_ONLY_SAFE_FIELDS, 2),
                Arguments.of("SINGLETON_NO_FIELD_WRITES", JakartaEeRaceConditionRules.SINGLETON_NO_FIELD_WRITES, 1),
                Arguments.of("SINGLETON_NO_COLLECTION_MUTATION", JakartaEeRaceConditionRules.SINGLETON_NO_COLLECTION_MUTATION, 1),
                // @ApplicationScoped: CurrentUserHolder(2/1/1)
                Arguments.of("APPLICATION_SCOPED_ONLY_SAFE_FIELDS", JakartaEeRaceConditionRules.APPLICATION_SCOPED_ONLY_SAFE_FIELDS, 2),
                Arguments.of("APPLICATION_SCOPED_NO_FIELD_WRITES", JakartaEeRaceConditionRules.APPLICATION_SCOPED_NO_FIELD_WRITES, 1),
                Arguments.of("APPLICATION_SCOPED_NO_COLLECTION_MUTATION", JakartaEeRaceConditionRules.APPLICATION_SCOPED_NO_COLLECTION_MUTATION, 1));
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
        List<String> writes = violations(JakartaEeRaceConditionRules.STATELESS_NO_FIELD_WRITES, bad);
        assertTrue(writes.stream().anyMatch(m -> m.startsWith(
                "[フィールド代入] StatefulOrderSearchService.setCondition() が フィールド StatefulOrderSearchService.condition に代入しています (StatefulOrderSearchService.java:")),
                () -> String.join("\n", writes));
        assertTrue(writes.stream().anyMatch(m -> m.contains("static フィールド StatefulOrderSearchService.searchCount")));
        assertTrue(writes.stream().anyMatch(m -> m.contains("LambdaWritingService.last")));
        assertTrue(writes.stream().anyMatch(m -> m.contains("LegacyJavaxStatelessService.lastKeyword")), "javax.ejb.Stateless も対象");

        List<String> mutations = violations(JakartaEeRaceConditionRules.STATELESS_NO_COLLECTION_MUTATION, bad);
        assertTrue(mutations.get(0).startsWith(
                "[コレクション変更] StatefulOrderSearchService.search() がフィールド StatefulOrderSearchService.searchHistory（型: List）の中身を add() で変更しています"),
                mutations::toString);

        List<String> fields = violations(JakartaEeRaceConditionRules.STATELESS_ONLY_SAFE_FIELDS, bad);
        assertTrue(fields.stream().anyMatch(m -> m.contains("フィールド searchCount（型: int）: static だが final ではない")), fields::toString);
        assertFalse(fields.stream().anyMatch(m -> m.contains("orderMapper")), "@Inject フィールドは対象外");
    }

    @Test
    @DisplayName("@AllowStatefulField（任意パッケージの同名アノテーション）で除外できる")
    void 除外アノテーション() {
        List<String> all = Stream.of(
                        JakartaEeRaceConditionRules.SINGLETON_ONLY_SAFE_FIELDS,
                        JakartaEeRaceConditionRules.SINGLETON_NO_FIELD_WRITES,
                        JakartaEeRaceConditionRules.SINGLETON_NO_COLLECTION_MUTATION)
                .flatMap(r -> violations(r, bad).stream()).toList();
        assertFalse(all.stream().anyMatch(m -> m.contains("departmentNames")), all::toString);
    }

    @Test
    @DisplayName("独自アノテーションにも同じルールをかけられる（ファクトリメソッド）")
    void ファクトリメソッド() {
        ArchRule rule = JakartaEeRaceConditionRules.noFieldWrites(
                "@RequestScoped（テスト用にあえて対象化）", "テスト", "jakarta.enterprise.context.RequestScoped");
        assertEquals(1, violations(rule, good).size());
    }

    @Test
    @DisplayName("ArchTests.in(JakartaEeRaceConditionRules.class) で 9 ルールが取れる")
    void ArchTestsで全ルール() {
        ArchTests tests = ArchTests.in(JakartaEeRaceConditionRules.class);
        assertEquals(JakartaEeRaceConditionRules.class, tests.getDefinitionLocation());
        long count = Stream.of(JakartaEeRaceConditionRules.class.getFields())
                .filter(f -> ArchRule.class.isAssignableFrom(f.getType()))
                .filter(f -> f.isAnnotationPresent(com.tngtech.archunit.junit.ArchTest.class))
                .count();
        assertEquals(9, count);
    }
}
