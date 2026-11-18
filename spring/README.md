# race-condition-rules-spring（Spring MVC / Spring Boot 用レースコンディション対策ルール）

Spring のシングルトン Bean（`@Controller` / `@RestController` / `@Service` / `@Repository` / `@Component` / `@Configuration` …）が、
フィールドにリクエストごとの値（画面の入力値、ログインユーザー、検索条件など）を持っていたら `mvn test` を失敗させる ArchUnit ルールです。

## どんな不具合を防ぐか

一般的な呼び名は **スレッドセーフでない問題 / 競合状態（レースコンディション）** です。

Spring の Bean は、スコープを指定しなければ **シングルトン（アプリ全体で 1 個）** で、全リクエストのスレッドから同時に呼ばれます。
EJB のようなインスタンスプールもロックもないので、フィールドに入れた値は同時アクセスでそのまま混ざります。

```java
@Controller
public class OrderController {
    private String keyword;                       // ← 全利用者で共有される
    @GetMapping("/orders")
    public String search(@RequestParam String keyword, Model model) {
        this.keyword = keyword;                   // A さんの値を B さんが上書きする
        ...
    }
}
```

## 対象の判定

- 対象: `@Component` が直接またはメタアノテーションとして付いたクラス（`@Controller` `@RestController` `@Service` `@Repository` `@Configuration` `@ControllerAdvice` `@SpringBootApplication` なども含む）。スコープ指定なし、`@Scope("singleton")`、`@ApplicationScope` は対象
- 対象外: `@Scope("request" | "session" | "prototype" | "websocket")`、`@RequestScope`、`@SessionScope`（メタアノテーション経由の `@Scope` も判定）、`@ConfigurationProperties`、`@AllowStatefulField` 付きクラス

## ルール一覧（4 ルール）

| 定数 | 内容 |
|---|---|
| `SINGLETON_ONLY_SAFE_FIELDS` | **A 宣言**: フィールドは注入（`@Autowired` `@Value` `@Inject` `@Resource` `@PersistenceContext`）、コンストラクタ注入の `final`（`@Autowired` 不要、Lombok `@RequiredArgsConstructor` も可）、`@Autowired` セッター・`*Aware` コールバックで代入されるもの、`static final`、`final` のロガーだけ。`@Qualifier` だけのフィールドや、自分で `new` した `final` の `List` などは個別のメッセージで検出 |
| `SINGLETON_NO_FIELD_WRITES` | **B 代入**: コンストラクタ、`@PostConstruct` / `@PreDestroy`、`afterPropertiesSet()` / `destroy()`、`@Autowired` セッター、`*Aware` コールバック以外でフィールドに代入しない |
| `SINGLETON_NO_COLLECTION_MUTATION` | **C 中身の変更**: 同じく、`List` / `Map` / `StringBuilder` などのフィールドを `add` / `put` / `append` などで変更しない |
| `NO_DIRECT_PROTOTYPE_INJECTION` | **D prototype の直接注入**: シングルトンに prototype の Bean を直接注入しない（最初の 1 個が使い回されて結局共有される）。`ObjectProvider<T>` で毎回取得するか、`proxyMode = TARGET_CLASS` を付ける |

違反はこのように日本語・行番号付きで出ます。

```
[フィールド代入] StatefulOrderController.search() が フィールド StatefulOrderController.keyword に代入しています (StatefulOrderController.java:27)
[フィールド宣言] com.example.shop.antipattern.SearchHistoryService のフィールド history（型: List）: final だがコンストラクタ注入ではない（自分で new したオブジェクトを全利用者で共有している） (SearchHistoryService.java)
[prototype 直接注入] com.example.shop.antipattern.BadReportService のコンストラクタ引数に prototype の ReportBuilder を直接注入しています (BadReportService.java)
```

## 既存プロジェクトへの入れ方

### 方法 1: jar を依存に追加する（おすすめ）

1. このフォルダ（または集約 POM のあるルート）で `mvn install`（社内リポジトリがあれば `mvn deploy`）
2. 利用側の `pom.xml` に追加

```xml
<!-- ルール本体（テストだけ） -->
<dependency>
  <groupId>com.example.archrules</groupId>
  <artifactId>race-condition-rules-spring</artifactId>
  <version>1.0.0</version>
  <scope>test</scope>
</dependency>
<!-- ArchUnit の JUnit 5 実行エンジン（まだ入っていなければ） -->
<dependency>
  <groupId>com.tngtech.archunit</groupId>
  <artifactId>archunit-junit5</artifactId>
  <version>1.5.1</version>
  <scope>test</scope>
</dependency>
<!-- 除外アノテーションを使うなら（自分で同名のアノテーションを作ってもよい） -->
<dependency>
  <groupId>com.example.archrules</groupId>
  <artifactId>race-condition-annotations</artifactId>
  <version>1.0.0</version>
</dependency>
```

3. テストクラスを 1 つ置く（`examples/RaceConditionTest.java`）。`packages` を自分のルートパッケージにします。

```java
package com.mycompany.myapp.arch;

import com.example.archrules.spring.SpringRaceConditionRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.mycompany.myapp", importOptions = ImportOption.DoNotIncludeTests.class)
class RaceConditionTest {

    @ArchTest static final ArchRule A_フィールド宣言 = SpringRaceConditionRules.SINGLETON_ONLY_SAFE_FIELDS;
    @ArchTest static final ArchRule B_フィールド代入 = SpringRaceConditionRules.SINGLETON_NO_FIELD_WRITES;
    @ArchTest static final ArchRule C_コレクション変更 = SpringRaceConditionRules.SINGLETON_NO_COLLECTION_MUTATION;
    @ArchTest static final ArchRule D_prototype直接注入 = SpringRaceConditionRules.NO_DIRECT_PROTOTYPE_INJECTION;
}
```

全部まとめてなら 1 行で書けます。

```java
    @ArchTest static final ArchTests レースコンディション対策 = ArchTests.in(SpringRaceConditionRules.class);
```

Spring Boot の `spring-boot-starter-test` には ArchUnit は入っていないので、`archunit-junit5` の追加が必要です。
Gradle なら `testImplementation 'com.example.archrules:race-condition-rules-spring:1.0.0'` と `testImplementation 'com.tngtech.archunit:archunit-junit5:1.5.1'` です。

### 方法 2: ソースをコピーする

jar を配布できない場合は、`src/main/java/com/example/archrules/spring/SpringRaceConditionRules.java` を
利用側の `src/test/java` の好きなパッケージにコピーし（package 文だけ直す）、上のテストクラスを置いてください。
必要な依存は `archunit-junit5`（test）だけです。

## 除外（共有してよいもの）

全利用者で共有してよい、スレッドセーフだと確認したものには `@AllowStatefulField("理由")` を付けます。

```java
@Repository
public class CodeMasterRepository {
    @AllowStatefulField("起動時に読み込む読み取り専用のコードマスタ。ConcurrentHashMap で保護")
    private final Map<String, String> cache = new ConcurrentHashMap<>();
}
```

- フィールドに付ける: そのフィールドの宣言・代入・中身の変更を許可
- メソッドに付ける: そのメソッドの中での代入・変更を許可
- クラスに付ける: クラス全体を対象外にする

判定は単純名 `AllowStatefulField` なので、`race-condition-annotations` を使わずに自分のプロジェクトの好きなパッケージに
同名のアノテーション（`String value()` を持つもの）を作っても動きます。

## 既存の違反が多いとき（freeze）

既存の違反をいったん記録し、**新しく増えた違反だけで落とす** ことができます（ArchUnit の `FreezingArchRule`）。

1. `examples/archunit.properties` を利用側の `src/test/resources/` にコピー
2. `mvn test -Darchunit.freeze=true` を 1 回実行 → `archunit_store/` に今の違反が記録される（テストは成功）
3. `archunit_store/` をコミット。以後も `-Darchunit.freeze=true` 付きで実行すると、記録にない違反だけで失敗します
4. 違反を直すと記録から自動で消えます

`-Darchunit.freeze=true` を付けなければ全違反で落ちます。自分で包みたいときは `SpringRaceConditionRules.freezeIfEnabled(rule)` か
`FreezingArchRule.freeze(rule)` を使ってください。記録はルールの説明文で紐づくので、ライブラリ更新で文言が変わったら `freeze.refreeze=true` で記録し直します。

## 検出できないもの（制約）

- **`@Bean` メソッドや XML で登録した Bean**: クラスに `@Component` 系が付いていないので検査されません。`@Configuration` で `@Bean` を多用している場合は注意してください。
- **`@Bean` メソッドに付けた `@Scope("prototype")`**: D はクラスに付いた `@Scope` で判定するため、`@Bean` 側でスコープを指定したものは検出しません。
- **ローカル変数を経由した変更**: `List<String> l = this.history; l.add(x);` は C で検出しません（フィールド自体は A で検出されます）。
- **注入した別オブジェクトの中身の変更**: `holder.setKeyword(k)` は呼び出し側では検出しません（`holder` がシングルトン Bean なら、そちら側で検出されます）。
- **`@PostConstruct` / `afterPropertiesSet()` で初期化するフィールド**: B・C では許可されますが、A では注入ではないため検出されます。読み取り専用キャッシュなどは `@AllowStatefulField` を付けてください。
- **コンストラクタ注入の判定は型の一致**です。`final` フィールドと同じ型の引数がコンストラクタにあれば注入とみなします。
- **スレッドセーフな型も検出します**: `AtomicInteger`、`ConcurrentHashMap` なども、共有してよいものだけ `@AllowStatefulField` で除外してください。
- **親クラスのフィールド**: `@Component` の付いていない抽象基底クラスのフィールドは検査しません。
- **C の変更メソッドは名前で判定**しています（`add` `put` `remove` `clear` `append` など）。

## このプロジェクト自体のテスト

```bash
mvn test
```

`src/test/java/.../fixtures/bad` のわざと危ないクラスで A 6 件 / B 4 件 / C 2 件 / D 1 件を検出すること、
`fixtures/good`（コンストラクタ注入、`@Autowired` セッター、`*Aware`、`ObjectProvider`、proxy 付き prototype、
`@RequestScope` / `@SessionScope` / prototype / `@ConfigurationProperties`、`@AllowStatefulField`）で 1 件も検出しないことを確認します（11 テスト）。
