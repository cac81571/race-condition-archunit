# レースコンディション対策 ArchUnit ルール

「共有される Bean のフィールドに、画面の入力値などリクエストごとの値を保持してしまう」不具合を、
`mvn test` の時点で検出して落とすための ArchUnit ルール集です。
一般的には **スレッドセーフでない問題**、**競合状態（レースコンディション）** と呼ばれる不具合です。

```java
@Stateless                       // Spring なら @Controller / @Service（どちらも全利用者で共有）
public class OrderSearchService {
    private String keyword;      // ← A さんが入れた値を B さんが上書きする
    public void setKeyword(String k) { this.keyword = k; }
}
```

## 中身

| フォルダ | artifactId | 内容 |
|---|---|---|
| `jakartaee/` | `race-condition-rules-jakartaee` | Jakarta EE（Java EE）用: `@Stateless` / `@Singleton` EJB、`@ApplicationScoped` CDI Bean の 9 ルール → [jakartaee/README.md](jakartaee/README.md) |
| `spring/` | `race-condition-rules-spring` | Spring MVC / Spring Boot 用: シングルトン Bean（`@Controller` / `@Service` / `@Repository` / `@Component` …）の 4 ルール → [spring/README.md](spring/README.md) |
| `annotations/` | `race-condition-annotations` | 除外用アノテーション `@AllowStatefulField("理由")` だけの依存なし jar（両方で共通） |

groupId はすべて `com.example.archrules`、version は `1.0.0` です（社内の命名に合わせて変えてください）。

各フォルダは **単独でビルドできる独立したプロジェクト** です（親 POM はありません）。
必要なほうのフォルダだけコピーして使えます。ルートの `pom.xml` は、まとめてビルドするための集約 POM です。

```
race-condition-archunit/
├── pom.xml                      … 集約 POM（mvn install で 3 つまとめてビルド）
├── annotations/
│   └── src/main/java/com/example/archrules/AllowStatefulField.java
├── jakartaee/
│   ├── README.md
│   ├── examples/                … 利用側のテストクラスと archunit.properties の例
│   ├── src/main/java/com/example/archrules/jakartaee/JakartaEeRaceConditionRules.java   … ルール本体（これ 1 ファイル）
│   └── src/test/java/…           … ルール自体のテスト（危ない書き方 / 正しい書き方のフィクスチャ）
└── spring/
    ├── README.md
    ├── examples/
    ├── src/main/java/com/example/archrules/spring/SpringRaceConditionRules.java          … ルール本体（これ 1 ファイル）
    └── src/test/java/…
```

## ビルド

Java 17 以上、Maven 3.6 以上。

```bash
mvn install          # 3 つの jar をテストしてローカルリポジトリ（~/.m2）に入れる
```

社内の Nexus / Artifactory に置く場合は `mvn deploy`（各 pom に `distributionManagement` を追加）してください。

## 設計方針

- **ルールは `src/main/java` の public な定数**（`public static final ArchRule`）。利用側はテストクラスを 1 つ書くだけで、
  解析するパッケージは利用側の `@AnalyzeClasses(packages = …)` で決めます。
- **Jakarta EE / Spring には依存しません**。アノテーションは完全修飾名の文字列で判定しているので、
  jar の依存は ArchUnit（`archunit` と `archunit-junit5-api`）だけです。Jakarta EE 9 以降の `jakarta.*` と、それ以前の `javax.*` の両方に対応しています。
- **除外アノテーションは単純名 `AllowStatefulField` で判定**します。`race-condition-annotations` を使っても、
  自分のプロジェクトに同名のアノテーションを作ってもかまいません。
- **ルール自体のテスト**は `src/test/java` のフィクスチャ（わざと危ない書き方 / 正しい書き方）に対して実行し、
  「検出すべきものを検出し、検出すべきでないものを検出しない」ことを確認しています（jar には入りません）。
