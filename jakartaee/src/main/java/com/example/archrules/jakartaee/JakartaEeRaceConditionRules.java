package com.example.archrules.jakartaee;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.FieldAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaFieldAccess.AccessType;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaStaticInitializer;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * Jakarta EE（Java EE）向けレースコンディション対策ルール集:
 * 「共有インスタンスの Bean がフィールドにリクエスト固有の状態を持つ」ことを禁止する ArchUnit ルール。
 *
 * <p>対象（Bean の種類ごとに別ルール。個別に選択 / freeze できます）
 * <ul>
 *   <li>@Stateless EJB      … インスタンスがプールされ、複数の利用者で使い回される</li>
 *   <li>@Singleton EJB      … アプリ全体で 1 インスタンスを全利用者が共有</li>
 *   <li>@ApplicationScoped / @jakarta.inject.Singleton の CDI Bean … 同上、しかも並行に呼ばれる</li>
 * </ul>
 * jakarta.* / javax.* の両方に対応。アノテーションは文字列で判定するため、このライブラリ自体は
 * Jakarta EE API に依存しません。
 *
 * <p>ルール（それぞれ上の 3 種類に適用）
 * <ul>
 *   <li>A（宣言）: フィールドは「注入（@Inject 等）」「static final」「Logger」以外を宣言してはいけない</li>
 *   <li>B（代入）: コンストラクタ・@PostConstruct・@PreDestroy 以外でフィールドへ代入してはいけない（バイトコードで判定）</li>
 *   <li>C（中身の変更）: 同じく、フィールドの List / Map / StringBuilder 等を add / put / clear 等で変更してはいけない</li>
 * </ul>
 *
 * <p>使い方（利用側のテスト）:
 * <pre>{@code
 * @AnalyzeClasses(packages = "com.mycompany.myapp", importOptions = ImportOption.DoNotIncludeTests.class)
 * class RaceConditionTest {
 *     @ArchTest
 *     static final ArchTests 全ルール = ArchTests.in(JakartaEeRaceConditionRules.class);
 * }
 * }</pre>
 *
 * <p>除外: {@code @AllowStatefulField("理由")} をフィールド・メソッド・クラスに付ける（単純名で判定）。
 * 既存違反を一括で凍結するには {@code -Darchunit.freeze=true}（FreezingArchRule）。
 */
public final class JakartaEeRaceConditionRules {

    private JakartaEeRaceConditionRules() {
    }

    // ------------------------------------------------------------------
    // 設定
    // ------------------------------------------------------------------

    /** 除外用アノテーションの単純名（パッケージは問わない） */
    public static final String ALLOW_ANNOTATION_SIMPLE_NAME = "AllowStatefulField";

    public static final String[] STATELESS_EJB = {"jakarta.ejb.Stateless", "javax.ejb.Stateless"};
    public static final String[] SINGLETON_EJB = {"jakarta.ejb.Singleton", "javax.ejb.Singleton"};
    public static final String[] APPLICATION_SCOPED_CDI = {
            "jakarta.enterprise.context.ApplicationScoped", "javax.enterprise.context.ApplicationScoped",
            "jakarta.inject.Singleton", "javax.inject.Singleton"};

    /** 付いていればコンテナ管理（注入）とみなすフィールドのアノテーション */
    static final Set<String> INJECTION_ANNOTATIONS = Set.of(
            "jakarta.inject.Inject", "javax.inject.Inject",
            "jakarta.ejb.EJB", "javax.ejb.EJB",
            "jakarta.annotation.Resource", "javax.annotation.Resource",
            "jakarta.persistence.PersistenceContext", "javax.persistence.PersistenceContext",
            "jakarta.persistence.PersistenceUnit", "javax.persistence.PersistenceUnit",
            "jakarta.ws.rs.core.Context", "javax.ws.rs.core.Context",
            "org.eclipse.microprofile.config.inject.ConfigProperty");

    /** フィールドの初期化を許可するライフサイクルメソッド */
    static final Set<String> LIFECYCLE_ANNOTATIONS = Set.of(
            "jakarta.annotation.PostConstruct", "javax.annotation.PostConstruct",
            "jakarta.annotation.PreDestroy", "javax.annotation.PreDestroy");

    /** final のインスタンスフィールドとして持ってよいロガー型 */
    static final Set<String> LOGGER_TYPES = Set.of(
            "java.util.logging.Logger", "java.lang.System$Logger",
            "org.slf4j.Logger", "org.apache.logging.log4j.Logger",
            "org.apache.commons.logging.Log", "org.jboss.logging.Logger");

    /** 中身を変更できるコンテナ型（ルール C の対象） */
    static final Class<?>[] MUTABLE_CONTAINER_TYPES = {
            Collection.class, Map.class, StringBuilder.class, StringBuffer.class};

    /** コンテナの中身を変更するメソッド名（ルール C） */
    static final Set<String> MUTATOR_METHODS = Set.of(
            "add", "addAll", "addFirst", "addLast", "offer", "offerFirst", "offerLast", "push", "pop", "poll",
            "put", "putAll", "putIfAbsent", "compute", "computeIfAbsent", "computeIfPresent", "merge",
            "remove", "removeAll", "removeIf", "removeFirst", "removeLast", "retainAll", "clear",
            "set", "replace", "replaceAll", "sort",
            "append", "insert", "delete", "deleteCharAt", "setCharAt", "setLength", "reverse");

    // ------------------------------------------------------------------
    // 理由（違反時に表示される because 文）
    // ------------------------------------------------------------------

    public static final String BECAUSE_STATELESS =
            "@Stateless EJB のインスタンスはプールされ、複数の利用者・リクエストで使い回されるため、"
            + "画面の入力値などをフィールドに保持すると、同時実行時に別の利用者の値が混ざったり前回の値が残ったりします。"
            + "値はメソッドの引数・戻り値・ローカル変数で受け渡してください"
            + "（例外的に許可する場合は @AllowStatefulField(\"理由\") を付けてください）";

    public static final String BECAUSE_SINGLETON =
            "@Singleton EJB はアプリケーションに 1 インスタンスしかなく全利用者で共有されるため、"
            + "リクエストごとの値をフィールドに保持すると他の利用者の値で上書きされます"
            + "（@Lock(WRITE) で直列化されても値の混在は防げません）。"
            + "共有してよいキャッシュ等は @AllowStatefulField(\"理由\") を付けてください";

    public static final String BECAUSE_APPLICATION_SCOPED =
            "@ApplicationScoped（@jakarta.inject.Singleton）の CDI Bean はアプリケーションに 1 インスタンスしかなく、"
            + "複数スレッドから同時に呼ばれる（EJB と違いロックもない）ため、"
            + "リクエストごとの値をフィールドに保持すると他の利用者の値と混ざります。"
            + "リクエスト固有の値は引数で渡すか @RequestScoped / @ViewScoped の Bean に持たせてください"
            + "（共有してよいものは @AllowStatefulField(\"理由\") を付けてください）";

    // ------------------------------------------------------------------
    // ルール本体（ArchTests.in(JakartaEeRaceConditionRules.class) で 9 ルールすべてを実行できる）
    // ------------------------------------------------------------------

    // ---- @Stateless EJB ----
    @ArchTest
    public static final ArchRule STATELESS_ONLY_SAFE_FIELDS =
            onlySafeFields("@Stateless EJB", BECAUSE_STATELESS, STATELESS_EJB);

    @ArchTest
    public static final ArchRule STATELESS_NO_FIELD_WRITES =
            noFieldWrites("@Stateless EJB", BECAUSE_STATELESS, STATELESS_EJB);

    @ArchTest
    public static final ArchRule STATELESS_NO_COLLECTION_MUTATION =
            noCollectionMutation("@Stateless EJB", BECAUSE_STATELESS, STATELESS_EJB);

    // ---- @Singleton EJB ----
    @ArchTest
    public static final ArchRule SINGLETON_ONLY_SAFE_FIELDS =
            onlySafeFields("@Singleton EJB", BECAUSE_SINGLETON, SINGLETON_EJB);

    @ArchTest
    public static final ArchRule SINGLETON_NO_FIELD_WRITES =
            noFieldWrites("@Singleton EJB", BECAUSE_SINGLETON, SINGLETON_EJB);

    @ArchTest
    public static final ArchRule SINGLETON_NO_COLLECTION_MUTATION =
            noCollectionMutation("@Singleton EJB", BECAUSE_SINGLETON, SINGLETON_EJB);

    // ---- @ApplicationScoped CDI Bean ----
    @ArchTest
    public static final ArchRule APPLICATION_SCOPED_ONLY_SAFE_FIELDS =
            onlySafeFields("@ApplicationScoped CDI Bean", BECAUSE_APPLICATION_SCOPED, APPLICATION_SCOPED_CDI);

    @ArchTest
    public static final ArchRule APPLICATION_SCOPED_NO_FIELD_WRITES =
            noFieldWrites("@ApplicationScoped CDI Bean", BECAUSE_APPLICATION_SCOPED, APPLICATION_SCOPED_CDI);

    @ArchTest
    public static final ArchRule APPLICATION_SCOPED_NO_COLLECTION_MUTATION =
            noCollectionMutation("@ApplicationScoped CDI Bean", BECAUSE_APPLICATION_SCOPED, APPLICATION_SCOPED_CDI);

    // ------------------------------------------------------------------
    // ルール生成（独自のステレオタイプ等にも同じルールをかけたいときに使う）
    // ------------------------------------------------------------------

    /**
     * ルール A: 指定アノテーションの付いたクラスは、注入・static final・ロガー以外のフィールドを持たない。
     *
     * @param label           違反メッセージに出す対象の名前（例: "@Stateless EJB"）
     * @param because         違反時に表示する理由
     * @param beanAnnotations 対象クラスのアノテーションの完全修飾名（いずれかが付いていれば対象）
     */
    public static ArchRule onlySafeFields(String label, String because, String... beanAnnotations) {
        return freezeIfEnabled(classes()
                .that(areBeansAnnotatedWithAnyOf(label, beanAnnotations))
                .should(haveOnlyStatelessSafeFields())
                .allowEmptyShould(true)
                .because(because));
    }

    /** ルール B: コンストラクタ・@PostConstruct・@PreDestroy 以外でフィールドに代入しない。 */
    public static ArchRule noFieldWrites(String label, String because, String... beanAnnotations) {
        return freezeIfEnabled(classes()
                .that(areBeansAnnotatedWithAnyOf(label, beanAnnotations))
                .should(notWriteOwnFieldsOutsideLifecycle())
                .allowEmptyShould(true)
                .because(because));
    }

    /** ルール C: コンストラクタ・@PostConstruct・@PreDestroy 以外でフィールドのコレクション等を変更しない。 */
    public static ArchRule noCollectionMutation(String label, String because, String... beanAnnotations) {
        return freezeIfEnabled(classes()
                .that(areBeansAnnotatedWithAnyOf(label, beanAnnotations))
                .should(notMutateContainerFieldsOutsideLifecycle())
                .allowEmptyShould(true)
                .because(because));
    }

    /**
     * システムプロパティ {@code archunit.freeze=true}（mvn test -Darchunit.freeze=true）のときは
     * FreezingArchRule で包み、既存違反を記録して新規違反だけで落とす。
     * 判定はこのクラスの初期化時（＝テスト JVM 起動時）に 1 回だけ行う。
     */
    public static ArchRule freezeIfEnabled(ArchRule rule) {
        return Boolean.getBoolean("archunit.freeze") ? FreezingArchRule.freeze(rule) : rule;
    }

    // ------------------------------------------------------------------
    // 対象クラスの判定
    // ------------------------------------------------------------------

    public static DescribedPredicate<JavaClass> areBeansAnnotatedWithAnyOf(String label, String... annotations) {
        return DescribedPredicate.describe(label + "（@" + ALLOW_ANNOTATION_SIMPLE_NAME + " 付きクラスを除く）",
                c -> Arrays.stream(annotations).anyMatch(c::isAnnotatedWith) && !isAllowListed(c));
    }

    static boolean isAllowListed(HasAnnotations<?> element) {
        return element.getAnnotations().stream()
                .anyMatch(a -> a.getRawType().getSimpleName().equals(ALLOW_ANNOTATION_SIMPLE_NAME));
    }

    static boolean hasAnyAnnotation(HasAnnotations<?> element, Set<String> names) {
        return element.getAnnotations().stream().anyMatch(a -> names.contains(a.getRawType().getName()));
    }

    static boolean isSynthetic(JavaField f) {
        return f.getModifiers().contains(JavaModifier.SYNTHETIC);
    }

    /** コンストラクタ・static 初期化子・@PostConstruct/@PreDestroy・許可メソッド・合成メソッドは対象外 */
    static boolean isExemptCodeUnit(JavaCodeUnit cu) {
        return cu.isConstructor()
                || cu.getName().equals(JavaStaticInitializer.STATIC_INITIALIZER_NAME)
                || cu.getModifiers().contains(JavaModifier.SYNTHETIC)
                || hasAnyAnnotation(cu, LIFECYCLE_ANNOTATIONS)
                || isAllowListed(cu);
    }

    static String location(JavaAccess<?> access) {
        return access.getSourceCodeLocation().toString();
    }

    static String methodName(JavaCodeUnit cu) {
        JavaClass owner = cu.getOwner();
        String ownerName = owner.getSimpleName().isEmpty()   // 匿名クラスは単純名が空なので Outer$1 形式にする
                ? owner.getName().substring(owner.getName().lastIndexOf('.') + 1)
                : owner.getSimpleName();
        return ownerName + "." + cu.getName() + "()";
    }

    // ------------------------------------------------------------------
    // ルール A: 宣言できるフィールドの種類
    // ------------------------------------------------------------------

    public static ArchCondition<JavaClass> haveOnlyStatelessSafeFields() {
        return new ArchCondition<>("注入フィールド（@Inject / @EJB / @Resource / @PersistenceContext 等）、"
                + "static final 定数、ロガー以外のフィールドを持たない") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean constructorInjection = javaClass.getConstructors().stream()
                        .anyMatch(c -> hasAnyAnnotation(c, Set.of("jakarta.inject.Inject", "javax.inject.Inject")));

                for (JavaField f : javaClass.getFields()) {
                    if (isSynthetic(f) || isAllowListed(f)) {
                        continue;
                    }
                    Set<JavaModifier> m = f.getModifiers();
                    boolean isStatic = m.contains(JavaModifier.STATIC);
                    boolean isFinal = m.contains(JavaModifier.FINAL);

                    if (isStatic && isFinal) continue;                                    // 定数
                    if (hasAnyAnnotation(f, INJECTION_ANNOTATIONS)) continue;             // フィールド注入
                    if (isFinal && LOGGER_TYPES.contains(f.getRawType().getName())) continue; // ロガー
                    if (!isStatic && isFinal && constructorInjection) continue;           // コンストラクタ注入

                    String reason = isStatic
                            ? "static だが final ではない（全インスタンス・全スレッドで共有される）"
                            : "注入でも static final でもないインスタンスフィールド（リクエストの値を保持できてしまう）";
                    events.add(SimpleConditionEvent.violated(f, String.format(
                            "[フィールド宣言] %s のフィールド %s（型: %s）: %s (%s.java)",
                            javaClass.getName(), f.getName(), f.getRawType().getSimpleName(), reason,
                            javaClass.getSimpleName())));
                }
            }
        };
    }

    // ------------------------------------------------------------------
    // ルール B: フィールドへの代入（PUTFIELD / PUTSTATIC）
    // ------------------------------------------------------------------

    public static ArchCondition<JavaClass> notWriteOwnFieldsOutsideLifecycle() {
        return new ArchCondition<>("コンストラクタ・@PostConstruct・@PreDestroy 以外でフィールドに代入しない") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                // 自クラスのメソッドだけでなく、内部クラス・ラムダ・他クラスからの書き込みも拾う
                for (JavaFieldAccess access : javaClass.getFieldAccessesToSelf()) {
                    if (access.getAccessType() != AccessType.SET) continue;
                    JavaField field = access.getTarget().resolveMember().orElse(null);
                    if (field == null || isSynthetic(field) || isAllowListed(field)) continue;
                    if (field.getModifiers().contains(JavaModifier.STATIC)
                            && field.getModifiers().contains(JavaModifier.FINAL)) continue;

                    JavaCodeUnit origin = access.getOrigin();
                    boolean fromSelf = origin.getOwner().equals(javaClass);
                    if (fromSelf && isExemptCodeUnit(origin)) continue;

                    String kind = field.getModifiers().contains(JavaModifier.STATIC) ? "static フィールド" : "フィールド";
                    String from = fromSelf ? "" : "（内部クラス・他クラスからの書き込み）";
                    events.add(SimpleConditionEvent.violated(access, String.format(
                            "[フィールド代入] %s が %s %s.%s に代入しています%s %s",
                            methodName(origin), kind, javaClass.getSimpleName(), field.getName(), from,
                            location(access))));
                }
            }
        };
    }

    // ------------------------------------------------------------------
    // ルール C: フィールドのコレクション等を中身だけ変更（add / put / append ...）
    //   バイトコードには呼び出し先インスタンスの情報が無いため、
    //   「同じメソッド・同じ行で、自クラスのコンテナ型フィールドを読み、変更系メソッドを呼んでいる」で判定する
    // ------------------------------------------------------------------

    public static ArchCondition<JavaClass> notMutateContainerFieldsOutsideLifecycle() {
        return new ArchCondition<>("コンストラクタ・@PostConstruct・@PreDestroy 以外でフィールドのコレクション等を変更しない") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaCodeUnit cu : javaClass.getCodeUnits()) {
                    if (isExemptCodeUnit(cu)) continue;

                    // 行番号 → その行で読んでいる自クラスのコンテナ型フィールド
                    Map<Integer, Set<JavaField>> containerReadsByLine = new HashMap<>();
                    for (JavaFieldAccess access : cu.getFieldAccesses()) {
                        if (access.getAccessType() != AccessType.GET) continue;
                        FieldAccessTarget target = access.getTarget();
                        if (!target.getOwner().equals(javaClass)) continue;
                        JavaField field = target.resolveMember().orElse(null);
                        if (field == null || isSynthetic(field) || isAllowListed(field)) continue;
                        if (!isMutableContainer(field.getRawType())) continue;
                        containerReadsByLine.computeIfAbsent(access.getLineNumber(), k -> new HashSet<>()).add(field);
                    }
                    if (containerReadsByLine.isEmpty()) continue;

                    // sb.append(a).append(b) のように同じ行で同じ変更メソッドを連鎖させた場合は 1 件にまとめる
                    Set<String> reported = new HashSet<>();
                    for (JavaMethodCall call : cu.getMethodCallsFromSelf()) {
                        if (!MUTATOR_METHODS.contains(call.getName())) continue;
                        JavaClass callOwner = call.getTarget().getOwner();
                        if (!isMutableContainer(callOwner)) continue;
                        Set<JavaField> fields = containerReadsByLine.get(call.getLineNumber());
                        if (fields == null) continue;
                        for (JavaField field : fields) {
                            if (!(field.getRawType().isAssignableTo(callOwner.getName())
                                    || callOwner.isAssignableTo(field.getRawType().getName()))) continue;
                            if (!reported.add(field.getName() + "#" + call.getName() + "#" + call.getLineNumber())) continue;
                            events.add(SimpleConditionEvent.violated(call, String.format(
                                    "[コレクション変更] %s がフィールド %s.%s（型: %s）の中身を %s() で変更しています %s",
                                    methodName(cu), javaClass.getSimpleName(), field.getName(),
                                    field.getRawType().getSimpleName(), call.getName(), location(call))));
                        }
                    }
                }
            }
        };
    }

    static boolean isMutableContainer(JavaClass type) {
        return Arrays.stream(MUTABLE_CONTAINER_TYPES).anyMatch(type::isAssignableTo);
    }
}
