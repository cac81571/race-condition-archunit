package com.example.archrules.spring;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.FieldAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaFieldAccess.AccessType;
import com.tngtech.archunit.core.domain.JavaMethod;
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
 * Spring MVC / Spring Boot 向けステートリーク対策ルール集:
 * 「シングルトン Bean がフィールドにリクエスト固有の状態を持つ」ことを禁止する ArchUnit ルール。
 *
 * <p>対象: {@code @Component} が（メタアノテーションとして）付いたクラス。
 * {@code @Controller} / {@code @RestController} / {@code @Service} / {@code @Repository} /
 * {@code @Configuration} / {@code @ControllerAdvice} / {@code @SpringBootApplication} など、すべて含む。
 * <ul>
 *   <li>除外: {@code @Scope("request" | "session" | "prototype" | "websocket")}、
 *       {@code @RequestScope} / {@code @SessionScope}（メタアノテーション経由の @Scope も判定）</li>
 *   <li>除外: {@code @ConfigurationProperties}（起動時にバインドされる設定クラス）</li>
 *   <li>対象のまま: スコープ指定なし（＝singleton）、{@code @Scope("singleton")}、{@code @ApplicationScope}、その他のカスタムスコープ</li>
 * </ul>
 * アノテーションは文字列で判定するため、このライブラリ自体は Spring に依存しません。
 *
 * <p>ルール
 * <ul>
 *   <li>A（宣言）: フィールドは「注入（@Autowired / @Value / @Inject / @Resource 等）」「コンストラクタ注入の final」
 *       「@Autowired セッター・Aware コールバックで代入されるもの」「static final」「Logger」だけ</li>
 *   <li>B（代入）: コンストラクタ・@PostConstruct / @PreDestroy・afterPropertiesSet() / destroy()・
 *       @Autowired セッター・Aware コールバック以外でフィールドに代入しない</li>
 *   <li>C（中身の変更）: 同じく、List / Map / StringBuilder 等のフィールドを add / put / append 等で変更しない</li>
 *   <li>D（prototype の直接注入）: シングルトンに prototype Bean を直接注入しない（ObjectProvider で取得する）</li>
 * </ul>
 *
 * <p>使い方（利用側のテスト）:
 * <pre>{@code
 * @AnalyzeClasses(packages = "com.mycompany.myapp", importOptions = ImportOption.DoNotIncludeTests.class)
 * class StateLeakTest {
 *     @ArchTest
 *     static final ArchTests 全ルール = ArchTests.in(SpringStateLeakRules.class);
 * }
 * }</pre>
 *
 * <p>除外: {@code @AllowStatefulField("理由")} をフィールド・メソッド・クラスに付ける（単純名で判定）。
 * 既存違反を一括で凍結するには {@code -Darchunit.freeze=true}（FreezingArchRule）。
 */
public final class SpringStateLeakRules {

    private SpringStateLeakRules() {
    }

    // ------------------------------------------------------------------
    // 設定
    // ------------------------------------------------------------------

    /** 除外用アノテーションの単純名（パッケージは問わない） */
    public static final String ALLOW_ANNOTATION_SIMPLE_NAME = "AllowStatefulField";

    /** 違反メッセージの先頭。ログや CI でこの不具合だけを拾えるようにする */
    public static final String VIOLATION_PREFIX = "【ステートリーク】";

    static final String COMPONENT = "org.springframework.stereotype.Component";
    static final String SCOPE = "org.springframework.context.annotation.Scope";

    /** 対象外にする（＝インスタンスが共有されない）スコープ名 */
    static final Set<String> NON_SHARED_SCOPES = Set.of("request", "session", "prototype", "websocket", "globalSession");

    /** 付いていれば対象外にするクラスアノテーション */
    static final Set<String> EXCLUDED_CLASS_ANNOTATIONS = Set.of(
            "org.springframework.boot.context.properties.ConfigurationProperties");

    /** 付いていれば注入とみなすフィールド／メソッドのアノテーション（@Qualifier だけでは注入されないので含めない） */
    static final Set<String> INJECTION_ANNOTATIONS = Set.of(
            "org.springframework.beans.factory.annotation.Autowired",
            "org.springframework.beans.factory.annotation.Value",
            "jakarta.inject.Inject", "javax.inject.Inject",
            "jakarta.annotation.Resource", "javax.annotation.Resource",
            "jakarta.persistence.PersistenceContext", "javax.persistence.PersistenceContext",
            "jakarta.persistence.PersistenceUnit", "javax.persistence.PersistenceUnit");

    static final Set<String> QUALIFIER_ANNOTATIONS = Set.of(
            "org.springframework.beans.factory.annotation.Qualifier",
            "jakarta.inject.Named", "javax.inject.Named");

    /** フィールドの初期化を許可するライフサイクルメソッドのアノテーション */
    static final Set<String> LIFECYCLE_ANNOTATIONS = Set.of(
            "jakarta.annotation.PostConstruct", "javax.annotation.PostConstruct",
            "jakarta.annotation.PreDestroy", "javax.annotation.PreDestroy");

    /** 「インターフェース名 → 許可するメソッド名」: 実装クラスのこの名前のメソッドはライフサイクルとみなす */
    static final Map<String, Set<String>> LIFECYCLE_INTERFACE_METHODS = Map.of(
            "org.springframework.beans.factory.InitializingBean", Set.of("afterPropertiesSet"),
            "org.springframework.beans.factory.DisposableBean", Set.of("destroy"));

    /** *Aware インターフェースのコールバック（注入の一種とみなす） */
    static final String AWARE = "org.springframework.beans.factory.Aware";
    static final Set<String> AWARE_METHODS = Set.of(
            "setApplicationContext", "setBeanFactory", "setBeanName", "setBeanClassLoader", "setEnvironment",
            "setResourceLoader", "setApplicationEventPublisher", "setMessageSource", "setEmbeddedValueResolver",
            "setServletContext", "setServletConfig", "setApplicationStartup", "setImportMetadata", "setLoadTimeWeaver");

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

    public static final String BECAUSE_SINGLETON =
            "Spring の Bean（@Controller / @RestController / @Service / @Repository / @Component / @Configuration）は、"
            + "スコープを指定しなければシングルトン（アプリ全体で 1 インスタンス）で、全リクエストのスレッドから同時に呼ばれます。"
            + "EJB のようなインスタンスプールもロックも無いため、画面の入力値などをフィールドに保持すると、"
            + "同時アクセス時に別の利用者の値が混ざったり上書きされたりします。"
            + "値はメソッドの引数・戻り値・ローカル変数・Model で受け渡し、どうしても Bean に持たせたい場合は "
            + "@RequestScope / @SessionScope の Bean にしてください"
            + "（全利用者で共有してよいキャッシュ等は @AllowStatefulField(\"理由\") を付けてください）";

    public static final String BECAUSE_PROTOTYPE =
            "prototype スコープの Bean は「取得するたびに新しいインスタンス」ですが、シングルトンに直接注入すると "
            + "注入は起動時の 1 回だけなので 1 個に固定され、結局全利用者で共有されます。"
            + "ObjectProvider<T>（または jakarta.inject.Provider<T>）を注入して、使うたびに getObject() で取得してください"
            + "（@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS) を付ける方法もあります）";

    // ------------------------------------------------------------------
    // ルール本体（ArchTests.in(SpringStateLeakRules.class) で 4 ルールすべてを実行できる）
    // ------------------------------------------------------------------

    /** ルール A: フィールドは注入・コンストラクタ注入の final・static final・ロガーのみ */
    @ArchTest
    public static final ArchRule SINGLETON_ONLY_SAFE_FIELDS = freezeIfEnabled(classes()
            .that(areSharedSpringBeans())
            .should(haveOnlyStatelessSafeFields())
            .allowEmptyShould(true)
            .because(BECAUSE_SINGLETON));

    /** ルール B: 業務メソッドでフィールドに代入しない */
    @ArchTest
    public static final ArchRule SINGLETON_NO_FIELD_WRITES = freezeIfEnabled(classes()
            .that(areSharedSpringBeans())
            .should(notWriteOwnFieldsOutsideLifecycle())
            .allowEmptyShould(true)
            .because(BECAUSE_SINGLETON));

    /** ルール C: 業務メソッドでコレクション等のフィールドの中身を変更しない */
    @ArchTest
    public static final ArchRule SINGLETON_NO_COLLECTION_MUTATION = freezeIfEnabled(classes()
            .that(areSharedSpringBeans())
            .should(notMutateContainerFieldsOutsideLifecycle())
            .allowEmptyShould(true)
            .because(BECAUSE_SINGLETON));

    /** ルール D: prototype の Bean をシングルトンに直接注入しない */
    @ArchTest
    public static final ArchRule NO_DIRECT_PROTOTYPE_INJECTION = freezeIfEnabled(classes()
            .that(areSharedSpringBeans())
            .should(notInjectPrototypeBeansDirectly())
            .allowEmptyShould(true)
            .because(BECAUSE_PROTOTYPE));

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

    public static DescribedPredicate<JavaClass> areSharedSpringBeans() {
        return DescribedPredicate.describe(
                "共有される Spring Bean（@Component 系。request / session / prototype スコープ、"
                        + "@ConfigurationProperties、@" + ALLOW_ANNOTATION_SIMPLE_NAME + " 付きクラスを除く）",
                SpringStateLeakRules::isSharedSpringBean);
    }

    static boolean isSharedSpringBean(JavaClass c) {
        if (c.isInterface() || c.isAnnotation() || c.isEnum()) return false;
        if (!(c.isAnnotatedWith(COMPONENT) || c.isMetaAnnotatedWith(COMPONENT))) return false;
        if (isAllowListed(c)) return false;
        if (EXCLUDED_CLASS_ANNOTATIONS.stream().anyMatch(a -> c.isAnnotatedWith(a) || c.isMetaAnnotatedWith(a))) {
            return false;
        }
        return !NON_SHARED_SCOPES.contains(scopeAnnotation(c).map(SpringStateLeakRules::scopeName).orElse(""));
    }

    /** @Scope を直接、またはメタアノテーション（@RequestScope 等）経由で探す */
    static Optional<JavaAnnotation<?>> scopeAnnotation(JavaClass c) {
        return findAnnotation(c.getAnnotations(), SCOPE, new HashSet<>());
    }

    static Optional<JavaAnnotation<?>> findAnnotation(Collection<? extends JavaAnnotation<?>> annotations,
                                                      String name, Set<String> visited) {
        for (JavaAnnotation<?> a : annotations) {
            if (a.getRawType().getName().equals(name)) return Optional.of(a);
        }
        for (JavaAnnotation<?> a : annotations) {
            JavaClass type = a.getRawType();
            if (!visited.add(type.getName()) || type.getName().startsWith("java.lang.annotation.")) continue;
            Optional<JavaAnnotation<?>> found = findAnnotation(type.getAnnotations(), name, visited);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    /** @Scope の value / scopeName（エイリアス）の空でない方 */
    static String scopeName(JavaAnnotation<?> scope) {
        for (String attr : List.of("value", "scopeName")) {
            Object v = scope.get(attr).orElse("");
            if (v instanceof String s && !s.isEmpty()) return s;
        }
        return "";
    }

    static boolean isPrototypeWithoutProxy(JavaClass c) {
        Optional<JavaAnnotation<?>> scope = scopeAnnotation(c);
        if (scope.isEmpty() || !"prototype".equals(scopeName(scope.get()))) return false;
        Object proxy = scope.get().get("proxyMode").orElse(null);
        String mode = proxy instanceof JavaEnumConstant e ? e.name() : "DEFAULT";
        return !(mode.equals("INTERFACES") || mode.equals("TARGET_CLASS"));
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

    /** @Autowired / @Value / @Inject / @Resource 付きメソッド（セッター注入）、または *Aware のコールバック */
    static boolean isInjectionCodeUnit(JavaCodeUnit cu) {
        if (cu.isConstructor()) return false;
        if (hasAnyAnnotation(cu, INJECTION_ANNOTATIONS)) return true;
        return cu.getOwner().isAssignableTo(AWARE)
                && AWARE_METHODS.contains(cu.getName())
                && cu.getRawParameterTypes().size() == 1;
    }

    /** InitializingBean.afterPropertiesSet() / DisposableBean.destroy() の実装 */
    static boolean isLifecycleInterfaceMethod(JavaCodeUnit cu) {
        if (!cu.getRawParameterTypes().isEmpty()) return false;
        return LIFECYCLE_INTERFACE_METHODS.entrySet().stream()
                .anyMatch(e -> cu.getOwner().isAssignableTo(e.getKey()) && e.getValue().contains(cu.getName()));
    }

    /** コンストラクタ・static 初期化子・ライフサイクル・注入メソッド・許可メソッド・合成メソッドは対象外 */
    static boolean isExemptCodeUnit(JavaCodeUnit cu) {
        return cu.isConstructor()
                || cu.getName().equals(JavaStaticInitializer.STATIC_INITIALIZER_NAME)
                || cu.getModifiers().contains(JavaModifier.SYNTHETIC)
                || hasAnyAnnotation(cu, LIFECYCLE_ANNOTATIONS)
                || isLifecycleInterfaceMethod(cu)
                || isInjectionCodeUnit(cu)
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
        return new ArchCondition<>("注入フィールド（@Autowired / @Value / @Inject / @Resource 等、コンストラクタ注入の final、"
                + "@Autowired セッター）、static final 定数、ロガー以外のフィールドを持たない") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                // コンストラクタ引数の型（Spring 4.3 以降はコンストラクタが 1 つなら @Autowired 不要。Lombok @RequiredArgsConstructor も同じ）
                Set<String> ctorParamTypes = new HashSet<>();
                for (JavaConstructor c : javaClass.getConstructors()) {
                    c.getRawParameterTypes().forEach(t -> ctorParamTypes.add(t.getName()));
                }

                for (JavaField f : javaClass.getFields()) {
                    if (isSynthetic(f) || isAllowListed(f)) continue;
                    Set<JavaModifier> m = f.getModifiers();
                    boolean isStatic = m.contains(JavaModifier.STATIC);
                    boolean isFinal = m.contains(JavaModifier.FINAL);

                    if (isStatic && isFinal) continue;                                          // 定数
                    if (hasAnyAnnotation(f, INJECTION_ANNOTATIONS)) continue;                   // フィールド注入
                    if (isFinal && LOGGER_TYPES.contains(f.getRawType().getName())) continue;   // ロガー
                    if (!isStatic && isFinal && ctorParamTypes.contains(f.getRawType().getName())) continue; // コンストラクタ注入
                    if (!isStatic && isWrittenOnlyByInjectionMethods(javaClass, f)) continue;  // セッター注入・Aware

                    String reason;
                    if (isStatic) {
                        reason = "static だが final ではない（全インスタンス・全スレッドで共有される）";
                    } else if (hasAnyAnnotation(f, QUALIFIER_ANNOTATIONS)) {
                        reason = "@Qualifier / @Named だけでは注入されない（@Autowired を付けるか、コンストラクタ注入にする）";
                    } else if (isFinal) {
                        reason = "final だがコンストラクタ注入ではない（自分で new したオブジェクトを全利用者で共有している）";
                    } else {
                        reason = "注入でも static final でもないインスタンスフィールド（リクエストの値を保持できてしまう）";
                    }
                    events.add(SimpleConditionEvent.violated(f, String.format(
                            VIOLATION_PREFIX + "[フィールド宣言] %s のフィールド %s（型: %s）: %s (%s.java)",
                            javaClass.getName(), f.getName(), f.getRawType().getSimpleName(), reason,
                            javaClass.getSimpleName())));
                }
            }
        };
    }

    static boolean isWrittenOnlyByInjectionMethods(JavaClass owner, JavaField field) {
        List<JavaFieldAccess> writes = new ArrayList<>();
        for (JavaFieldAccess a : owner.getFieldAccessesToSelf()) {
            if (a.getAccessType() == AccessType.SET && a.getTarget().getName().equals(field.getName())) writes.add(a);
        }
        return !writes.isEmpty() && writes.stream()
                .allMatch(a -> a.getOrigin().getOwner().equals(owner) && isInjectionCodeUnit(a.getOrigin()));
    }

    // ------------------------------------------------------------------
    // ルール B: フィールドへの代入（PUTFIELD / PUTSTATIC）
    // ------------------------------------------------------------------

    public static ArchCondition<JavaClass> notWriteOwnFieldsOutsideLifecycle() {
        return new ArchCondition<>("コンストラクタ・@PostConstruct・@PreDestroy・afterPropertiesSet()・destroy()・"
                + "@Autowired セッター以外でフィールドに代入しない") {
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
                            VIOLATION_PREFIX + "[フィールド代入] %s が %s %s.%s に代入しています%s %s",
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
        return new ArchCondition<>("コンストラクタ・ライフサイクルメソッド以外でフィールドのコレクション等を変更しない") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaCodeUnit cu : javaClass.getCodeUnits()) {
                    if (isExemptCodeUnit(cu)) continue;

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
                                    VIOLATION_PREFIX + "[コレクション変更] %s がフィールド %s.%s（型: %s）の中身を %s() で変更しています %s",
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

    // ------------------------------------------------------------------
    // ルール D: prototype Bean をシングルトンに直接注入（コンストラクタ引数・注入フィールド・注入メソッド引数）
    // ------------------------------------------------------------------

    public static ArchCondition<JavaClass> notInjectPrototypeBeansDirectly() {
        return new ArchCondition<>("prototype スコープの Bean を直接注入しない（ObjectProvider で取得する）") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaConstructor c : javaClass.getConstructors()) {
                    for (JavaClass t : c.getRawParameterTypes()) {
                        if (isPrototypeWithoutProxy(t)) report(events, c, javaClass, "コンストラクタ引数", t);
                    }
                }
                for (JavaField f : javaClass.getFields()) {
                    if (!isAllowListed(f) && hasAnyAnnotation(f, INJECTION_ANNOTATIONS) && isPrototypeWithoutProxy(f.getRawType())) {
                        events.add(SimpleConditionEvent.violated(f, String.format(
                                VIOLATION_PREFIX + "[prototype 直接注入] %s の注入フィールド %s に prototype の %s を直接注入しています (%s.java)",
                                javaClass.getName(), f.getName(), f.getRawType().getSimpleName(), javaClass.getSimpleName())));
                    }
                }
                for (JavaMethod m : javaClass.getMethods()) {
                    if (!isInjectionCodeUnit(m)) continue;
                    for (JavaClass t : m.getRawParameterTypes()) {
                        if (isPrototypeWithoutProxy(t)) report(events, m, javaClass, "注入メソッド " + m.getName() + "() の引数", t);
                    }
                }
            }

            private void report(ConditionEvents events, JavaCodeUnit cu, JavaClass owner, String where, JavaClass type) {
                events.add(SimpleConditionEvent.violated(cu, String.format(
                        VIOLATION_PREFIX + "[prototype 直接注入] %s の%sに prototype の %s を直接注入しています (%s.java)",
                        owner.getName(), where, type.getSimpleName(), owner.getSimpleName())));
            }
        };
    }
}
