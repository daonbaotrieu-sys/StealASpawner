# StealASpawner

Plugin Paper (1.21.11, Java 21) mô phỏng spawner economy kiểu Lifesteal SMP:
giết quái có cơ hội rớt spawner, đặt xuống để sinh tiền thụ động, nâng cấp
3 track (Speed / Yield / Capacity) tới level 1000, và cho phép người chơi
khác trộm spawner đã đặt bằng cuốc Silk Touch sau khi hết grace period.

## Build

Cần JDK 21 và Maven. Môi trường build cần internet để Maven tải:
Paper API, VaultAPI (jitpack), PlaceholderAPI, WorldGuard + WorldEdit
(enginehub repo), GriefPrevention (jitpack), HikariCP, sqlite-jdbc.

```bash
mvn clean package
```

Output: `target/StealASpawner-1.0.0.jar`. HikariCP và sqlite-jdbc được
shade + relocate vào trong jar (xem `<relocations>` trong `pom.xml`), nên
bạn chỉ cần copy đúng 1 file jar này vào thư mục `plugins/` của server.

## Cài đặt

1. Copy `StealASpawner-1.0.0.jar` vào `plugins/`.
2. (Khuyến nghị) Cài **Vault** + một plugin economy (EssentialsX, CMI...)
   — không có economy thì shop và nâng cấp bị vô hiệu hoá, plugin sẽ log
   warning ở onEnable nhưng vẫn chạy bình thường cho phần spawner/steal.
3. (Tuỳ chọn) PlaceholderAPI, WorldGuard, GriefPrevention — tự động hook
   nếu có mặt, không bắt buộc.
4. Khởi động server, chỉnh `plugins/StealASpawner/config.yml` theo ý muốn,
   rồi `/sas reload`.

> **Lưu ý về các dependency "jitpack"** (VaultAPI, GriefPrevention): những
> coordinate trong `pom.xml` đúng tại thời điểm viết, nhưng các repo bên
> thứ 3 này thỉnh thoảng đổi version/tag. Nếu Maven báo không resolve được,
> lên https://jitpack.io tìm lại coordinate mới nhất cho từng plugin.

## Cơ chế tóm tắt

- **Giết quái** → roll `ThreadLocalRandom` theo `drop-rates` trong
  config.yml → có thể rớt item spawner (level 1/1/1 mọi track).
- **Đặt spawner** → track chủ sở hữu, vị trí, loại mob, 3 level, trong
  SQLite (`plugins/StealASpawner/spawners.db`) + cache
  `ConcurrentHashMap` trong RAM. Có grace period (mặc định 5 phút) chống
  steal ngay lập tức.
- **Steal** → cần cuốc Silk Touch, hết grace period, không phải chủ/được
  trust → drop lại spawner (giữ nguyên 3 level), hiệu ứng Enderman
  teleport + particle portal, broadcast actionbar quanh khu vực.
- **Thu nhập** → mỗi `income-interval-seconds`, mọi spawner đã đặt (kể cả
  chủ offline) cộng tiền qua Vault, dựa trên `base-income` × hệ số Yield.
- **Nâng cấp** → GUI (right-click spawner) hoặc `/spawner upgrade`. Cost
  tăng theo công thức đa thức bậc 2 (xem comment trong `config.yml`).
  Mỗi track có `effect-cap` riêng — qua khỏi đó, level vẫn tăng (dùng cho
  `/spawner top`, leaderboard, flex) nhưng không còn tăng hiệu ứng thật.

## Lệnh & quyền

| Lệnh | Mô tả | Quyền |
|---|---|---|
| `/spawnershop` | Mở shop mua spawner | `stealaspawner.use` |
| `/spawner info` | Thông tin spawner đang nhìn vào | `stealaspawner.use` |
| `/spawner upgrade <speed\|yield\|capacity> [amount]` | Nâng cấp | `stealaspawner.upgrade` |
| `/spawner top` | Bảng xếp hạng | `stealaspawner.use` |
| `/spawner trust <player>` | Cho phép pickup an toàn | `stealaspawner.use` |
| `/sas reload` | Reload config.yml | `stealaspawner.admin` |

`stealaspawner.steal` (mặc định true) điều khiển việc có được trộm spawner
người khác hay không; tắt quyền này cho một nhóm nếu bạn muốn server của
bạn chỉ cho phép đặt/nâng cấp mà không có PvP kinh tế.

## Cấu trúc code

```
src/main/java/net/lifesteal/stealaspawner/
├── StealASpawnerPlugin.java      # wiring, lifecycle, scheduled tasks
├── listeners/                    # mob death, place, break, chunk load, interact
├── commands/                     # /spawner, /spawnershop, /sas
├── data/                         # SpawnerData, SpawnerManager (cache), Database (SQLite/HikariCP)
├── gui/                          # shop + per-spawner upgrade panel
├── util/                         # config snapshot, formulas, economy/region hooks, item factory
└── placeholder/                  # PlaceholderAPI expansion
```
