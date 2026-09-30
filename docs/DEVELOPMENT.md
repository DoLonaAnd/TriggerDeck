# TriggerDeck 開発者向けメモ

## ビルド

JDK 17 以上が必要（Android Studio 同梱の JBR で可）。

```bash
./gradlew installDebug
```

### リリースビルド

`keystore.properties`（git 管理外）をリポジトリ直下に置くと、署名付きでビルドされる。

```properties
storeFile=release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

```bash
./gradlew assembleRelease
```

## 構成

| ファイル | 役割 |
| --- | --- |
| `TriggerService` | AccessibilityService。システムにバインドされ、再起動後も自動で復帰する |
| `backend/AccessibilityBackend` | `onKeyEvent` → `RawKeyEvent` |
| `backend/PowerButtonBackend` | 画面 ON/OFF の連続から電源ボタン2回押しを検出 |
| `backend/NativeInputBackend` | 機種固有入力用のスタブ（無効） |
| `TriggerDispatcher` | キー → `RIGHT_TRIGGER_DOWN/UP/SINGLE/DOUBLE/LONG`、キー学習、ダブル待ち |
| `ActionResolver` | (入力元, ジェスチャー) → 割り当てられたアクション |
| `ActionExecutor` | アクションの実行 (アプリ起動・ライト・グローバル操作など) |
| `WakeLaunchActivity` | 画面 OFF / ロック中にアプリを起動するための中継 |
| `WalletLauncher` | Google Wallet の起動 |
| `TgkController` | REDMAGIC の入力 API でトリガーを有効化し、握りつぶしを解除する |
| `ForegroundAppTracker` | 前面アプリを追跡し、抑止するか判定する（ホワイトリスト > ブラックリスト > ゲーム自動判定） |
| `BootReceiver` | 起動時刻を記録し、サービスが復帰したかを診断する |
| `MainActivity` / `ui/` | ホーム (割り当て・設定) と診断画面 |

## GameSpace 外でトリガーを有効にする仕組み (REDMAGIC)

REDMAGIC OS はゲーム外ではトリガーのセンサーを止め、ネイティブ入力層で F7/F8 を握りつぶす。
`IInputManager` の REDMAGIC 拡張 API は権限チェックがないため、通常アプリから以下を呼ぶ（`TgkController`）。

| API | 役割 |
| --- | --- |
| `enableLeftTgkDrive` / `enableRightTgkDrive` (true) | センサーを駆動 |
| `setLeftGameKeyEnable` / `setRightGameKeyEnable` (true) | 入力層でトリガーを有効化 |
| `setConsumeTgkKey` (false) | キーの握りつぶしを解除 |

- システムは画面 ON/OFF やゲームの開始・終了で状態を戻すため、前面アプリの切り替え・画面 ON・`nubia_game_scene` の変化のたびに再適用する
- ゲームや除外アプリが前面のときは GameSpace 側の制御に任せて触らない
- トランザクション番号は `IInputManager$Stub.TRANSACTION_*` から名前で解決する。解決できないときの決め打ちの番号は、動作確認済みの RED MAGIC 11 Pro (NX809J) / Android 16 でのみ使う。番号がずれると無関係な API を呼ぶ危険があるため

## 判定時間

| 項目 | 既定 | 範囲 |
| --- | --- | --- |
| 電源ボタンの2回押し (画面 OFF→ON の間隔) | 400ms | 150–800ms |
| トリガーのダブルタップ | 300ms | 150–600ms |
| トリガーの長押し | 500ms | 300–1500ms |

- ダブルに割り当てがあるトリガーは、2回目を待つ間シングルの実行を遅らせる
- 電源キーは OS が先に処理するため KeyEvent を受け取れない。画面の切り替えが続いた間隔で判定する

## ログ

- リリース版は、トリガーのキーと「キーを学習」中のキーだけを記録する（外付けキーボードの入力などは記録しない）
- デバッグ版は全キーを記録し、`files/diag.log` にも書き出す（REDMAGIC は `log.tag=S` で logcat にアプリのログが出ないため）

```bash
adb shell run-as net.dolonaand.apk.triggerdeck cat files/diag.log
```

## 前面アプリとゲームの判定

- 原則として Activity への遷移だけを前面アプリの変化とみなす（GameSpace のオーバーレイを除外するため）
- ゲーム・リスト登録済みアプリは、ダイアログなど Activity 以外のウィンドウでも前面アプリとみなす（荒野行動は起動直後にスプラッシュをダイアログで出すため）
- REDMAGIC では `Settings.Global nubia_game_scene=1` の間も GameSpace のゲーム中として抑止する。ゲーム名は `Settings.System nubia_game_scene_package_name` で、ホワイトリストにあれば抑止しない

## 既知の挙動 (REDMAGIC 11 Pro)

- パッケージ名が `com.redmagic.` で始まると GameSpace がゲームと誤判定する
- アプリを強制停止（`am force-stop` / `am start -S` を含む）するとユーザー補助の登録が外れる
- 画面 OFF からの電源2回押しは、画面 OFF のブロードキャストが遅れて届くため検出できない

## トリガーが検出されない場合

```bash
adb shell getevent -lp
```

```bash
adb shell getevent -l
```

`nubia_tgk_aw_sar` などのデバイスでトリガー押下時にイベントが出るか確認する。
