/**
 * 일일 퀘스트 정의(2026-10-22 DB 이전) — 이 파일이 원본이고, seed.js 가 Firestore `starlightConfig/quests` 로 올린다.
 * 앱(Android `QuestCatalog.kt` / iOS `QuestCatalog.swift`)과 서버(functions/starlight.js claimQuest)가 **같은 문서**를 읽는다.
 * 예전엔 목록·목표 수·문구가 Android/iOS/서버 세 곳에 따로 박혀 있어 하나만 고치면 어긋났다 — 이제 여기만 고친다.
 *
 * 난이도(tier): easy(앱 기능 한 번 써 보기) · normal(발품·사람과 엮이는 일) · hard(며칠 모아야 하거나 별가루 메뉴까지 가는 일).
 * 하루 3개 = 1일째는 [firstDay] 고정(첫 사용 안내와 같은 순서), 2일째부터 쉬움 1개 + 그 이상 2개
 * (칸마다 hardChance 확률로 어려움, hardFromDay 일째부터). 보상은 [rewards] — 난이도별 별가루.
 *
 * 퀘스트 하나:
 *  - id     : 영구 식별자(이미 사용자 문서 quest.ids 에 남아 있다 — 바꾸지 말 것). 빼려면 지우지 말고 daily:false.
 *  - event  : 앱이 보고하는 이벤트 이름(Android QuestEvent / iOS QuestEvent 와 같은 철자). **앱이 모르는 이벤트는 그 앱에서 뽑지 않는다**
 *             — 새 이벤트를 넣으면 앱에 보고 코드부터 넣고, 옛 앱은 그 퀘스트를 건너뛴다(같은 날 목록은 먼저 만든 기기 것을 같이 쓴다).
 *  - target : 목표 횟수(기본 1) · tier · group(같은 group 은 하루 하나) · hours [시작, 끝](현지 "시", 끝이 24 이상이면 자정을 넘김)
 *  - title / hint : ko·en·ja
 *
 * ⚠️ pool 순서는 뽑기 결과에 영향을 준다(두 앱이 같은 순서로 섞는다) — 순서를 바꾸면 같은 날 두 기기 목록이 달라질 수 있으니
 *    새 퀘스트는 맨 뒤에 붙인다.
 */
const t3 = (ko, en, ja) => ({ ko, en, ja });

const pool = [];
function q(id, event, tier, title, hint, extra = {}) {
  pool.push({ id, event, tier, target: 1, daily: true, ...extra, title, hint });
}

// ── 쉬움 — 앱 기능 하나 써 보기 ──────────────────────────────────────────
q("upload", "UPLOAD", "easy",
  t3("별(다이어리) 하나 남기기", "Leave a star (diary)", "星（日記）をひとつ残す"),
  t3("오른쪽 아래 + 버튼으로 지금 이 자리에 별을 남겨요", "Use the + button at the bottom right to leave a star right here", "右下の＋ボタンで今いる場所に星を残そう"),
  { group: "upload" });
q("like", "LIKE", "easy",
  t3("마음에 드는 별에 좋아요 누르기", "Like a star you enjoyed", "気に入った星にいいねする"),
  t3("열린 별에서 하트를 눌러요", "Tap the heart on an opened star", "開いた星でハートをタップ"));
q("view_other3", "VIEW_OTHER", "easy",
  t3("다른 사람의 별 3개 열어 보기", "Open 3 stars from other people", "ほかの人の星を3つ開く"),
  t3("지도에서 별을 눌러요. 멀리 있는 별도 제목은 보여요", "Tap a star on the map. Even far stars show their title", "地図で星をタップ。遠くの星もタイトルは見えます"),
  { target: 3 });
q("comment", "COMMENT", "easy",
  t3("별에 댓글 남기기", "Comment on a star", "星にコメントする"),
  t3("댓글은 100m 안의 별에만 달 수 있어요. 방금 남긴 내 별도 돼요", "You can only comment within 100m — your own new star counts too", "コメントは100m以内の星だけ。さっき残した自分の星でもOK"));
q("equip_title", "EQUIP_TITLE", "easy",
  t3("칭호 장착해 보기", "Equip a title", "称号を装備する"),
  t3("메뉴 > 업적에서 얻은 칭호를 눌러 장착해요", "Go to Menu > Achievements and tap a title you earned", "メニュー > 実績で獲得した称号をタップして装備"));
q("profile_edit", "PROFILE_EDIT", "easy",
  t3("닉네임이나 프로필 사진 바꿔 보기", "Change your nickname or profile photo", "ニックネームかプロフィール写真を変える"),
  t3("메뉴 > 프로필에서 이름이나 사진을 눌러요", "Go to Menu > Profile and tap your name or photo", "メニュー > プロフィールで名前か写真をタップ"));
q("pin_star", "PIN_STAR", "easy",
  t3("프로필에 마음에 드는 별 넣어 보기", "Pin a favorite star to your profile", "お気に入りの星をプロフィールに飾る"),
  t3("프로필에서 내 별을 골라 걸어 둘 수 있어요", "On your profile, pick one of your stars to show off", "プロフィールで自分の星を選んで飾れます"));
q("constellation", "CONSTELLATION", "easy",
  t3("별자리 선 켜 보기", "Turn on constellation lines", "星座の線をつける"),
  t3("지도 오른쪽의 반짝이 버튼으로 근처 별을 이어요", "The sparkle button on the right links nearby stars", "地図右のキラキラボタンで近くの星をつなぐ"));
q("filter", "FILTER", "easy",
  t3("지도 필터 써 보기", "Use a map filter", "地図フィルターを使う"),
  t3("왼쪽 아래 나침반 버튼에서 보고 싶은 별만 골라요", "Pick which stars to see with the compass button at the bottom left", "左下のコンパスボタンで見たい星だけ選ぶ"));
q("globe", "GLOBE", "easy",
  t3("지구로 나가 보기", "Head out to the globe", "地球に出てみる"),
  t3("지도를 크게 줄이면 나타나는 '우주에서 보기'를 눌러요", "Zoom far out and tap 'View from space'", "地図を大きく縮小すると出る「宇宙から見る」をタップ"));
q("achievements", "VISIT_ACHIEVEMENTS", "easy",
  t3("업적 둘러보기", "Browse achievements", "実績を見てみる"),
  t3("메뉴 > 업적에서 다음 목표를 확인해요", "Check your next goal in Menu > Achievements", "メニュー > 実績で次の目標をチェック"));
q("user_profile", "VISIT_PROFILE", "easy",
  t3("다른 사람 프로필 구경하기", "Visit someone's profile", "ほかの人のプロフィールを見る"),
  t3("별을 열고 작성자 이름을 눌러요", "Open a star and tap the author's name", "星を開いて作成者の名前をタップ"));
q("star_log", "VISIT_STARLOG", "easy",
  t3("별 도감 둘러보기", "Browse your Star Log", "星図鑑を見てみる"),
  t3("메뉴 > 별 도감에서 내가 연 별을 모아 봐요", "See every star you've opened in Menu > Star Log", "メニュー > 星図鑑で開いた星を眺めよう"));
q("music", "MUSIC", "easy",
  t3("배경음악 바꿔 보기", "Change the background music", "BGMを変えてみる"),
  t3("메뉴 > 배경음악에서 다른 곡을 골라요", "Pick another track in Menu > Music", "メニュー > BGMで別の曲を選ぶ"));
q("focus", "FOCUS", "easy",
  t3("지도를 10초 동안 가만히 바라보기", "Gaze at the map for 10 seconds", "地図を10秒じっと眺める"),
  t3("아무것도 누르지 않으면 버튼이 사라지고 별만 남아요", "Don't touch anything — the buttons fade away and only stars remain", "何も触らなければボタンが消えて星だけが残ります"));
q("meteor", "METEOR", "easy",
  t3("지구에서 유성 보기", "Spot a meteor from the globe", "地球で流れ星を見る"),
  t3("'우주에서 보기'에서 잠시 기다리면 유성이 지나가요", "Wait a moment in 'View from space' and a meteor flies by", "「宇宙から見る」で少し待つと流れ星が通ります"));

// ── 보통 — 발품을 팔거나 다른 사람과 엮이는 일 ─────────────────────────
q("unlock_visit", "UNLOCK_VISIT", "normal",
  t3("직접 걸어가서 잠긴 별 열기", "Walk up to a locked star to open it", "歩いて行ってロックされた星を開く"),
  t3("남의 별 100m 안으로 다가가 열면 열쇠 없이 열려요", "Get within 100m of someone's star and open it — no key needed", "人の星の100m以内まで近づいて開けば、鍵なしで開きます"));
q("upload_stack", "UPLOAD_STACK", "normal",
  t3("다른 별과 겹치게 별 남기기", "Leave a star on top of another", "ほかの星と重ねて星を残す"),
  t3("이미 별이 있는 자리(30m 안)에 내 별을 올리면 별이 겹쳐요", "Post where a star already shines (within 30m) and they stack", "すでに星がある場所（30m以内）に残すと星が重なります"),
  { group: "upload" });
q("comment_other", "COMMENT_OTHER", "normal",
  t3("다른 사람의 별에 댓글 달기", "Comment on someone else's star", "ほかの人の星にコメントする"),
  t3("100m 안에 있는 남의 별을 열고 댓글을 남겨요", "Open someone's star within 100m and leave a comment", "100m以内にある人の星を開いてコメントしよう"));
q("meteor2", "METEOR", "normal",
  t3("지구에서 유성 2번 보기", "Spot 2 meteors from the globe", "地球で流れ星を2回見る"),
  t3("'우주에서 보기'에 머물러 유성을 두 번 기다려요", "Stay in 'View from space' and wait for two meteors", "「宇宙から見る」にとどまって流れ星を2回待とう"),
  { target: 2 });
q("friend_request", "FRIEND_REQUEST", "normal",
  t3("친구 요청 보내기", "Send a friend request", "友だち申請を送る"),
  t3("다른 사람 프로필이나 메뉴 > 친구에서 보낼 수 있어요", "Send one from someone's profile or Menu > Friends", "ほかの人のプロフィールかメニュー > 友だちから送れます"));
q("friend_add", "FRIEND_ADD", "normal",
  t3("친구 한 명 사귀기", "Make a new friend", "友だちをひとり作る"),
  t3("받은 친구 요청을 수락하거나, 내가 보낸 요청이 수락되면 돼요", "Accept a request, or have one of yours accepted", "届いた申請を承認するか、送った申請が承認されればOK"));
q("invite", "INVITE", "normal",
  t3("친구에게 초대 링크 보내기", "Send an invite link to a friend", "友だちに招待リンクを送る"),
  t3("메뉴 > 친구 아래의 초대 카드를 눌러 공유해요", "Tap the invite card at the bottom of Menu > Friends", "メニュー > 友だちの下の招待カードをタップして共有"));
q("like3", "LIKE", "normal",
  t3("좋아요 3번 누르기", "Give 3 likes", "いいねを3回する"),
  t3("서로 다른 별 세 곳에 하트를 눌러요", "Tap the heart on three different stars", "ちがう星3つでハートをタップ"),
  { target: 3 });
q("upload_media", "UPLOAD_MEDIA", "normal",
  t3("사진이나 영상을 담아 별 남기기", "Leave a star with a photo or video", "写真か動画を入れて星を残す"),
  t3("올리기 화면에서 사진·영상을 추가해요", "Add a photo or video on the upload screen", "投稿画面で写真・動画を追加"),
  { group: "upload" });
q("upload_morning", "UPLOAD", "normal",
  t3("아침에 별 남기기 (오전 5시~11시)", "Leave a star in the morning (5–11 AM)", "朝に星を残す（午前5時〜11時）"),
  t3("하루를 시작하며 오늘의 다짐을 남겨 봐요", "Start your day by leaving a little promise", "一日の始まりに今日の目標を残してみよう"),
  { group: "upload", hours: [5, 10] });
q("upload_night", "UPLOAD", "normal",
  t3("밤에 별 남기기 (오후 9시~새벽 2시)", "Leave a star at night (9 PM–2 AM)", "夜に星を残す（午後9時〜深夜2時）"),
  t3("하루를 마치며 오늘 있었던 일을 적어 봐요", "Wrap up your day with what happened today", "一日の終わりに今日の出来事を書いてみよう"),
  { group: "upload", hours: [21, 25] });
q("use_key", "UNLOCK_KEY", "normal",
  t3("열쇠로 멀리 있는 별 열기", "Open a far-away star with a key", "鍵で遠くの星を開く"),
  t3("잠긴 별에서 자물쇠를 눌러 열쇠를 써요", "Tap the lock on a locked star to use a key", "ロックされた星で南京錠をタップして鍵を使う"));
q("share", "SHARE", "normal",
  t3("별 공유 영상 만들기", "Make a star share video", "星のシェア動画を作る"),
  t3("별을 열고 공유 버튼을 눌러요", "Open a star and tap the share button", "星を開いてシェアボタンをタップ"));

// ── 어려움 — 며칠 모으거나 별가루 메뉴까지 가야 하는 일 ───────────────────────
// 2026-10-26 성운 상자 → 우주 항해 → 2026-10-27 잠든 우주(id·event 는 그대로 — 옛 앱은 상자를 열 때 같은 이벤트를 보고한다).
q("open_box", "OPEN_BOX", "hard",
  t3("잠든 우주 한 번 깨우기", "Awaken a sleeping cosmos", "眠れる宇宙を1回目覚めさせる"),
  t3("메뉴 › 별가루에서 별가루로 잠든 우주를 깨워요", "Spend stardust to awaken a sleeping cosmos in Menu › Stardust", "メニュー › 星くず で星くずを使って眠れる宇宙を目覚めさせる"));
q("friend_add2", "FRIEND_ADD", "hard",
  t3("새 친구 2명 사귀기", "Make 2 new friends", "新しい友だちを2人作る"),
  t3("지금 친구 수에서 두 명 더 — 요청을 수락하거나 보낸 요청이 수락되면 돼요", "Two more than you have now — accept requests or get yours accepted", "今の友だちからさらに2人 — 申請の承認でも、送った申請の承認でもOK"),
  { target: 2 });
q("invite3", "INVITE", "hard",
  t3("초대 링크 3번 보내기", "Send 3 invite links", "招待リンクを3回送る"),
  t3("메뉴 > 친구의 초대 카드로 여러 사람에게 별을 알려요", "Use the invite card in Menu > Friends to tell more people", "メニュー > 友だちの招待カードでいろんな人に星を知らせよう"),
  { target: 3 });
q("achievement", "ACHIEVEMENT", "hard",
  t3("새 업적 하나 달성하기", "Earn a new achievement", "新しい実績をひとつ達成する"),
  t3("메뉴 > 업적에서 거의 다 된 목표를 골라 도전해요", "Pick a nearly finished goal in Menu > Achievements", "メニュー > 実績で、あと少しの目標に挑戦しよう"));
q("unlock_visit2", "UNLOCK_VISIT", "hard",
  t3("직접 걸어가서 잠긴 별 2개 열기", "Walk up to 2 locked stars and open them", "歩いて行ってロックされた星を2つ開く"),
  t3("서로 다른 남의 별 두 곳에 100m 안으로 다가가 열어요", "Get within 100m of two different stars and open them", "ちがう星2つに100m以内まで近づいて開こう"),
  { target: 2 });
q("comment_other3", "COMMENT_OTHER", "hard",
  t3("다른 사람의 별 3곳에 댓글 달기", "Comment on 3 other people's stars", "ほかの人の星3つにコメントする"),
  t3("서로 다른 별 세 곳 — 댓글은 100m 안의 별에만 달 수 있어요", "Three different stars — comments only work within 100m", "ちがう星3つ — コメントは100m以内の星だけ"),
  { target: 3 });

// ── 뽑히지 않지만 예전 목록 표시용으로 남긴 것 ──────────────────────────
q("view_other", "VIEW_OTHER", "easy",
  t3("다른 사람의 별 열어 보기", "Open someone else's star", "ほかの人の星を開く"),
  t3("지도에서 별을 눌러요. 멀리 있는 별도 제목은 보여요", "Tap a star on the map. Even far stars show their title", "地図で星をタップ。遠くの星もタイトルは見えます"),
  { daily: false });
q("tutorial_star", "TUTORIAL_STAR", "easy",
  t3("근처에 뜬 웰컴 별 열어 보기", "Open the welcome star nearby", "近くに現れたウェルカム星を開く"),
  t3("지도에서 금빛으로 빛나는 STARY 별을 눌러요", "Tap the golden STARY star on the map", "地図で金色に光る STARY の星をタップ"),
  { daily: false });

const quests = {
  version: 1,
  /** 1일째(가입 첫날) — 첫 사용 안내(FirstStarGuide) 순서: 프로필 바꾸기 → 첫 별 → 남의 별. */
  firstDay: ["profile_edit", "upload", "view_other"],
  /** 칸마다 어려움이 나올 확률(그 외 보통). */
  hardChance: 0.35,
  /** 이 일차부터 어려움이 나온다(그 전엔 쉬움 1 + 보통 2). */
  hardFromDay: 4,
  /** 난이도별 보상 별가루. 보너스(3개 모두)는 starlightConfig/main bonusStardust. */
  rewards: { easy: 10, normal: 20, hard: 35 },
  pool,
};

module.exports = quests;

if (require.main === module && process.argv.includes("--json")) {
  process.stdout.write(JSON.stringify(quests, null, 1));
}
