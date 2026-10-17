# vcmc-AnimalGuard

vcmc-AnimalGuardは、友好的なMobに**名札で名前をつけることで自動的に保護**できる、PaperMCおよびFolia向けの専用プラグインです。

本プラグインはPaperMC 26.2 (Minecraft 1.26.2) の独自APIをフル活用し、高いパフォーマンスと安定性を実現しています。Spigotの互換性をあえて排除し、Paperネイティブなイベント (`PlayerNameEntityEvent` など) を使用して実装されています。

## 主な機能

* **名札によるスマートな保護**:
  * Mobに名札を使用するだけで保護データベースに登録されます。
  * Paper専用APIを使用しているため、左右どちらの手で名付けても重複して処理されるバグが発生しません。
* **強力なダメージ保護**:
  * プレイヤーの攻撃、モブからの攻撃、爆発から保護します。
  * 環境ダメージ (溶岩、炎上、窒息、サボテン、密集ダメージなど) をすべてシャットアウトします。
  * ※ ただし、奈落への落下 (VOID) や `/kill` コマンドはサーバー負荷対策のためブロックしません。
* **変異・分裂の追従**:
  * スライムが分裂した場合や、村人がゾンビ村人に変異した場合など、新しいエンティティにも保護を自動的に引き継ぎます。
* **管理用GUI (`/ag`)**:
  * サーバー内のすべての保護Mobを一覧表示できるGUIを搭載。
  * 保護の強制解除、対象Mobへのテレポート、一時的な発光(グロウ)などの操作が可能です。
* **完全なFolia対応**:
  * `GlobalRegionScheduler`, `AsyncScheduler`, `EntityScheduler` を適切に使い分け、Folia環境でのマルチスレッド処理に完全対応しています。
  * データベース(SQLite)処理は全て非同期スレッドで行われます。

## コマンドと権限

| コマンド | 説明 | 権限 | デフォルト |
| :--- | :--- | :--- | :--- |
| `/ag` | 管理用GUIを開きます | `vcmc.animalguard.admin` | OP |
| `/ag reload` | Configとデータベースをリロードします | `vcmc.animalguard.admin` | OP |

| 権限ノード | 説明 | デフォルト |
| :--- | :--- | :--- |
| `vcmc.animalguard.use` | 名札を使ってMobを保護する権限 | 全員 (`true`) |
| `vcmc.animalguard.bypass` | 保護を無視してMobを攻撃・バケツ回収する権限 | なし (`false`) |

## 今後のメンテナンス・開発者向けメモ

このプラグインを今後アップデートする際の注意事項です。

1. **Spigot APIの利用は避ける**
   Paperエコシステム向けに最適化されています。Spigot固有の曖昧なイベント (`PlayerInteractEntityEvent` を使った名札のハックなど) は使用せず、Paperが提供するAPI (`PlayerNameEntityEvent` や Component関連) を優先してください。
2. **非同期処理の厳守**
   データベース(SQLite)への保存・削除は必ず `plugin.getServer().getAsyncScheduler().runNow(...)` でラップして非同期で実行してください。メインスレッドでDBを触るとFolia環境で例外や深刻なラグを引き起こします。
3. **エンティティの取得**
   GUIからテレポートする際など、UUIDからエンティティを取得・操作する場合は、必ずエンティティのRegionスレッド (`entity.getScheduler().run(...)`) 上で位置情報を取得するように実装されています。これを崩さないように注意してください。
4. **環境ダメージについて**
   `EntityProtectListener.java` にて、奈落(`VOID`)やキルコマンド(`KILL`, `SUICIDE`)以外のダメージを一括でキャンセルしています。新しいマイクラのバージョンで「保護モブが受けるべきダメージ」が追加された場合は、ここの条件式を微調整してください。
5. **ビルド環境**
   * Java 21
   * Paper API 26.2+
   * 依存関係として `sqlite-jdbc` と `HikariCP` を同梱(Shade)してビルドしています。

## ビルド方法

```bash
mvn clean package
```
ビルド完了後、`target/` ディレクトリに `vcmc-AnimalGuard-26.2.jar` (Shade済み) が生成されます。

