# race-condition-rules-jakartaee（Jakarta EE 用レースコンディション対策ルール）

`@Stateless` / `@Singleton` EJB と `@ApplicationScoped` CDI Bean が、フィールドにリクエストごとの値
（画面の入力値、ログインユーザー、検索条件など）を持っていたら `mvn test` を失敗させる ArchUnit ルールです。

## どんな不具合を防ぐか

一般的な呼び名は **スレッドセーフでない問題 / 競合状態（レースコンディション）** です。

| Bean の種類 | インスタンス | フィールドに値を入れると |
|---|---|---|
| `@Stateless` EJB | プールされ、呼び出しのたびに空いているものが貸し出される | 別の利用者の値が混ざる・前回の値が残る |
| `@Singleton` EJB | アプリ全体で 1 個 | 全利用者で上書きし合う（`@Lock(WRITE)` でも値の混在は防げない） |
| `@ApplicationScoped` / `@jakarta.inject.Singleton` CDI Bean | アプリ全体で 1 個、ロックもない | 全利用者で上書きし合う |

`@Inject` などの注入フィールドは問題ありません（中身は共有してよい部品やプロキシで、参照が途中で変わらないため）。
対象外: `@Stateful`、`@RequestScoped`、`@SessionScoped`、`@ViewScoped`、`@ConversationScoped`、`@Dependent`（共有されないため）。

## ルール一覧（9 ルール = 3 種類の Bean × 3 ルール）

| 定数 | 内容 |
|---|---|
| `STATELESS_ONLY_SAFE_FIELDS` / `SINGLETON_…` / `APPLICATION_SCOPED_…` | **A 宣言**: フィールドは注入（`@Inject` `@EJB` `@Resource` `@PersistenceContext` `@PersistenceUnit` `@Context` `@ConfigProperty`）、`@Inject` コンストラクタで受け取る `final`、`static final`、`final` のロガーだけ |
| `STATELESS_NO_FIELD_WRITES` / `SINGLETON_…` / `APPLICATION_SCOPED_…` | **B 代入**: コンストラクタ・`@PostConstruct`・`@PreDestroy` 以外でフィールドに代入しない（ラムダ・内部クラスからの代入も検出） |
| `STATELESS_NO_COLLECTION_MUTATION` / `SINGLETON_…` / `APPLICATION_SCOPED_…` | **C 中身の変更**: 同じく、`List` / `Map` / `Set` / `StringBuilder` などのフィールドを `add` / `put` / `remove` / `clear` / `append` などで変更しない |

違反はこのように日本語・行番号付きで出ます。

```
[フィールド代入] StatefulOrderSearchService.setCondition() が フィールド StatefulOrderSearchService.condition に代入しています (StatefulOrderSearchService.java:34)
[コレクション変更] StatefulOrderSearchService.search() がフィールド StatefulOrderSearchService.searchHistory（型: List）の中身を add() で変更しています (StatefulOrderSearchService.java:40)
[フィールド宣言] com.example.app.antipattern.CurrentUserHolder のフィールド loginId（型: String）: 注入でも static final でもないインスタンスフィールド（リクエストの値を保持できてしまう） (CurrentUserHolder.java)
```

## 既存プロジェクトへの入れ方

### 方法 1: jar を依存に追加する（おすすめ）

1. このフォルダ（または集約 POM のあるルート）で `mvn install`（社内リポジトリがあれば `mvn deploy`）
2. 利用側の `pom.xml` に追加

```xml
<!-- 除外アノテーション（業務コードで使うので compile。依存なしの小さな jar） -->
<dependency>
  <groupId>com.example.archrules</groupId>
  <artifactId>race-condition-annotations</artifactId>
  <version>1.0.0</version>
</dependency>
<!-- ルール本体（テストだけ） -->
<dependency>
  <groupId>com.example.archrules</groupId>
  <artifactId>race-condition-rules-jakartaee</artifactId>
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
```

3. テストクラスを 1 つ置く（`examples/RaceConditionTest.java`）。`packages` を自分のルートパッケージにします。

```java
package com.mycompany.myapp.arch;

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
```

ルールを選びたいときは 1 つずつ書きます（不要な行を消すだけ）。

```java
    @ArchTest static final ArchRule stateless宣言 = JakartaEeRaceConditionRules.STATELESS_ONLY_SAFE_FIELDS;
    @ArchTest static final ArchRule stateless代入 = JakartaEeRaceConditionRules.STATELESS_NO_FIELD_WRITES;
    @ArchTest static final ArchRule stateless変更 = JakartaEeRaceConditionRules.STATELESS_NO_COLLECTION_MUTATION;
    // SINGLETON_… / APPLICATION_SCOPED_… も同様
```

Gradle なら `testImplementation 'com.example.archrules:race-condition-rules-jakartaee:1.0.0'` と
`testImplementation 'com.tngtech.archunit:archunit-junit5:1.5.1'`、`implementation 'com.example.archrules:race-condition-annotations:1.0.0'` です。

### 方法 2: ソースをコピーする

jar を配布できない場合は、`src/main/java/com/example/archrules/jakartaee/JakartaEeRaceConditionRules.java` を
利用側の `src/test/java` の好きなパッケージにコピーし（package 文だけ直す）、上のテストクラスを置いてください。
必要な依存は `archunit-junit5`（test）だけです。`@AllowStatefulField` も自分で 1 ファイル作れば使えます（下記）。

### 独自のステレオタイプにもかけたいとき

ファクトリメソッドで同じルールを作れます（アノテーションは完全修飾名、直接付いているものが対象）。

```java
@ArchTest static final ArchRule 独自 = JakartaEeRaceConditionRules.noFieldWrites(
        "@MyService", "理由の文章", "com.mycompany.MyService");
// onlySafeFields(...) / noCollectionMutation(...) も同じ引数
```

## 除外（共有してよいもの）

全利用者で共有してよい、スレッドセーフだと確認したものには `@AllowStatefulField("理由")` を付けます。

```java
@Singleton
public class CodeMasterCache {
    @AllowStatefulField("起動時に @PostConstruct で読み込む部署マスタ。読み取り専用")
    private final Map<Long, String> departmentNames = new ConcurrentHashMap<>();
}
```

- フィールドに付ける: そのフィールドの宣言・代入・中身の変更を許可
- メソッドに付ける: そのメソッドの中での代入・変更を許可
- クラスに付ける: クラス全体を対象外にする

判定は単純名なので、`race-condition-annotations` を使わずに自分で作っても動きます。

```java
package com.mycompany.myapp.arch;
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
@java.lang.annotation.Target({java.lang.annotation.ElementType.FIELD,
        java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.TYPE})
public @interface AllowStatefulField { String value(); }
```

## 既存の違反が多いとき（freeze）

既存の違反をいったん記録し、**新しく増えた違反だけで落とす** ことができます（ArchUnit の `FreezingArchRule`）。

1. `examples/archunit.properties` を利用側の `src/test/resources/` にコピー
2. `mvn test -Darchunit.freeze=true` を 1 回実行 → `archunit_store/` に今の違反が記録される（テストは成功）
3. `archunit_store/` をコミット。以後も `-Darchunit.freeze=true` 付きで実行すると、記録にない違反だけで失敗します
   （CI の設定や `pom.xml` の surefire `systemPropertyVariables` に入れておくと楽です）
4. 違反を直すと記録から自動で消えます

`-Darchunit.freeze=true` を付けなければ、記録は無視して全違反で落ちます。
自分で包みたいときは `JakartaEeRaceConditionRules.freezeIfEnabled(rule)` か `FreezingArchRule.freeze(rule)` を使ってください。
記録はルールの説明文で紐づくので、このライブラリを更新して文言が変わったら `freeze.refreeze=true` で記録し直します。

## 検出できないもの（制約）

- **ローカル変数を経由した変更**: `List<String> l = this.history; l.add(x);` は C で検出しません（読み取りと変更が同じ行にあるものだけ判定）。ただしフィールド自体は A で検出されます。
- **注入した別オブジェクトの中身の変更**: `holder.setKeyword(k)` は、呼び出し側では検出しません。`holder` が `@ApplicationScoped` などなら、そちら側で検出されます。普通の POJO・DTO をフィールドに持っている場合は A で検出されます。
- **`@PostConstruct` で初期化するフィールド**: B・C では許可されますが、A（宣言）では注入ではないため検出されます。読み取り専用キャッシュなどは `@AllowStatefulField` を付けてください。
- **スレッドセーフな型も検出します**: `AtomicInteger`、`ConcurrentHashMap` などでも、リクエストごとの値を入れるのは誤りなので検出します。共有してよいものだけ `@AllowStatefulField` で除外してください。
- **対象はクラスに直接付いたアノテーション**だけです。ステレオタイプ（メタアノテーション）、`ejb-jar.xml` や `beans.xml` での宣言、`@Produces` メソッドで作るオブジェクトは対象外です。
- **親クラスのフィールド**: アノテーションの付いていない抽象基底クラスのフィールドは検査しません。
- **C の変更メソッドは名前で判定**しています（`add` `put` `remove` `clear` `append` など）。独自コレクションの独自メソッドや `AtomicXxx.incrementAndGet()` は C の対象外です（A では検出されます）。
- `@SessionScoped` は対象外ですが、同じ利用者が複数タブを開くと共有される点には注意してください。

## このプロジェクト自体のテスト

```bash
mvn test
```

`src/test/java/.../fixtures/bad` のわざと危ないクラスで、各ルールが決まった件数（A 6 / B 5 / C 1、`@Singleton` 2 / 1 / 1、`@ApplicationScoped` 2 / 1 / 1）を検出すること、
`fixtures/good`（正しい書き方、`@Stateful`・`@RequestScoped`、`@AllowStatefulField` 付き）で 1 件も検出しないことを確認します（22 テスト）。
`javax.ejb.Stateless` やラムダからの代入も検出できることを確認しています。
