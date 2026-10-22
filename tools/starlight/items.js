/**
 * 성운 상자 뽑기 풀(별빛 공방, 2026-10-22) — 이 파일이 원본이고, seed.js 가 Firestore `starlightItems` 로 올린다.
 *
 * 등급 = **제작 난이도**(사용자 지시): 일반 60 · 희귀 28 · 영웅 10 · 전설 2 (같은 등급 안에서는 균등, `weight` 로 조절 가능).
 *  - 별 모양: 한 덩어리 윤곽(일반) → 여러 부품(희귀) → 구멍·대칭 세부(영웅) → 겹친 고리·구멍·장식(전설)
 *  - 별 색: 단색(일반) → 2색(희귀) → 3색(영웅). 색은 그라데이션 단계 이상으로 "어렵게" 만들 방법이 없어 전설은 두지 않는다.
 *  - 칭호: 단색(일반) → 2색(희귀) → 3색(영웅) → 3색 + 빛 흐름(전설)
 *  - 프로필 프레임: 정지 고리(일반) → 그라데이션·점선 회전(희귀) → 궤도 별·꽃잎(영웅) → 별자리·오로라(전설)
 *  - 이름 배지: 단순 실루엣(일반) → 부품·구멍(희귀) → 꼬리 여럿(영웅) → 눈·혀가 있는 깃털(전설)
 *  - 지도 별 오라: 빛·위성 하나·반딧불(희귀) → 맥동·쌍둥이 궤도·나선(영웅) → 토성 고리·혜성(전설)
 *    (2026-10-22 추가 5종. 영웅 맥동은 "너무 자주 두근거린다" 피드백으로 느리게 — 오라 speed 는 한 주기 초)
 *  - 천체 망원경: 굴절(일반, 업적 ??? 조건 1개) → 반사(희귀, 히든 업적 흐릿한 힌트) → 전파(영웅, 히든 업적 선명한 힌트)
 *  - 열쇠 묶음: 3개(일반) → 6개(희귀) — 뽑는 즉시 열쇠가 된다.
 *
 * ⚠️ 번호 규칙: 별 모양 `type` 은 21부터, 별 색 `index` 는 21부터(앱 내장 0~20 다음). **한 번 쓴 번호는 절대 다른 아이템에 다시 쓰지 말 것**
 *    — 이미 그 번호로 올린 별 문서가 있다(문서에 경로·색이 함께 실려 있어 그림은 유지되지만 이름/소유가 꼬인다).
 * ⚠️ 아이템을 빼려면 지우지 말고 `active: false`(뽑히지 않음, 가진 사람은 계속 쓴다).
 *
 *   node items.js --json   → 전체 JSON(미리보기용)
 */
const S = require("./shapes");
const B = require("./glyphs");

const t3 = (ko, en, ja) => ({ ko, en, ja });

const items = [];
let order = 0;
function add(id, kind, rarity, name, desc, payload) {
  items.push({ id, kind, rarity, order: order++, active: true, weight: 1, name, desc, payload });
}
/** 뽑기에서 뺀다(active:false) — 지우지 않는다(이미 가진 사람이 있다). 번호(type·index)도 다시 쓰지 말 것. */
function retire(id) {
  const it = items.find((x) => x.id === id);
  if (!it) throw new Error("retire: 없는 아이템 " + id);
  it.active = false;
}

// ── 별 모양 ────────────────────────────────────────────────────────────────
add("shape_droplet", "shape", "common",
  t3("물방울 별", "Droplet Star", "しずくの星"),
  t3("비 갠 밤, 잎 끝에 맺혀 있던 한 방울.", "A single drop clinging to a leaf on a night after rain.", "雨上がりの夜、葉先に残ったひとしずく。"),
  { type: 21, path: S.droplet() });
add("shape_soft_star", "shape", "common",
  t3("몽글 별", "Puffy Star", "ふわふわ星"),
  t3("모서리를 다 깎아 낸, 손에 쥐면 말랑할 것 같은 별.", "A star with every corner sanded away — it looks soft enough to squeeze.", "角をすべて丸めた、握ったらやわらかそうな星。"),
  { type: 22, path: S.softStar() });
add("shape_leaf", "shape", "common",
  t3("나뭇잎 별", "Leaf Star", "木の葉の星"),
  t3("바람에 실려 온 잎 한 장. 어느 숲에서 왔을까요.", "A leaf carried in on the wind. Which forest did it come from?", "風に運ばれてきた一枚の葉。どこの森から来たのでしょう。"),
  { type: 23, path: S.leaf() });
add("shape_butterfly", "shape", "rare",
  t3("나비 별", "Butterfly Star", "蝶の星"),
  t3("밤에도 날아다니는 나비. 날갯짓마다 빛가루가 떨어져요.", "A butterfly that flies even at night, shedding light with every flap.", "夜にも舞う蝶。羽ばたくたびに光の粉がこぼれます。"),
  { type: 24, path: S.butterfly() });
retire("shape_butterfly"); // 2026-10-23 "너무 실사" → 구름 위 달(30)로 교체. 가진 사람은 계속 쓴다.
add("shape_notes", "shape", "rare",
  t3("음표 별", "Melody Star", "音符の星"),
  t3("이 자리에 노래 한 소절을 남겨 두고 가요.", "Leave a bar of a song right here.", "この場所に、歌のひと節を置いていこう。"),
  { type: 25, path: S.notes() });
add("shape_whale", "shape", "rare",
  t3("고래 별", "Whale Star", "くじらの星"),
  t3("하늘 바다를 헤엄치다 숨을 쉬러 올라온 고래.", "A whale swimming the sea of the sky, surfacing for a breath.", "空の海を泳ぎ、息つぎに浮かんできたくじら。"),
  { type: 26, path: S.whale() });
add("shape_fox", "shape", "epic",
  t3("여우 별", "Fox Star", "きつねの星"),
  t3("반짝이는 눈으로 밤길을 지켜보는 여우. 각진 면마다 빛이 꺾여요.", "A bright-eyed fox watching the night road; light bends at every facet.", "きらめく目で夜道を見守るきつね。面ごとに光が折れます。"),
  { type: 27, path: S.fox() });
retire("shape_fox"); // 2026-10-23 "너무 실사" → 별 담은 병(31)으로 교체.
add("shape_moon_rabbit", "shape", "epic",
  t3("달토끼 별", "Moon Rabbit Star", "月うさぎの星"),
  t3("보름달 속에서 방아를 쉬고 있는 토끼. 오늘 밤 소원을 들어 줄지도.", "The rabbit resting inside the full moon. It might grant tonight's wish.", "満月の中でひと休みするうさぎ。今夜の願いを叶えてくれるかも。"),
  { type: 28, path: S.moonRabbit() });
add("shape_compass", "shape", "legendary",
  t3("북극성 나침반", "Polaris Compass", "北極星の羅針盤"),
  t3("길 잃은 여행자들이 마지막으로 의지하던 별. 이 별이 있는 곳에서 길이 다시 시작돼요.", "The star lost travelers trusted last. Wherever it shines, the road begins again.", "道に迷った旅人が最後に頼った星。この星がある場所から、道はまた始まります。"),
  { type: 29, path: S.compass() });
add("shape_cloud_moon", "shape", "rare",
  t3("구름달 별", "Cloud Moon Star", "雲月の星"),
  t3("몽실한 구름에 걸터앉아 쉬어 가는 초승달.", "A crescent moon taking a rest on a fluffy cloud.", "ふんわり雲に腰かけてひと休みする三日月。"),
  { type: 30, path: S.cloudMoon() });
add("shape_star_jar", "shape", "epic",
  t3("별 담은 병", "Star in a Jar", "星の小瓶"),
  t3("오늘 밤 주운 별 하나를 코르크 마개로 꼭 닫아 두었어요.", "One star picked up tonight, sealed tight with a cork.", "今夜ひろった星をひとつ、コルクでしっかり閉じこめました。"),
  { type: 31, path: S.starJar() });

// ── 별 색 ─────────────────────────────────────────────────────────────────
add("color_peach", "color", "common",
  t3("복숭아빛", "Peach Glow", "ピーチグロウ"),
  t3("해 질 녘 구름 가장자리의 따뜻한 빛.", "The warm light on the edge of clouds at dusk.", "夕暮れの雲のふちの、あたたかな光。"),
  { index: 21, colors: ["#FFB38A"] });
add("color_lilac", "color", "common",
  t3("라일락 안개", "Lilac Mist", "ライラックミスト"),
  t3("봄밤에 깔리는 옅은 보랏빛 안개.", "A pale violet mist settling on a spring night.", "春の夜に立ちこめる、淡い紫の霧。"),
  { index: 22, colors: ["#C9B8FF"] });
add("color_teal", "color", "common",
  t3("심해 청록", "Deep Teal", "ディープティール"),
  t3("깊은 바다 아래 빛나는 생물들의 색.", "The color of creatures glowing deep beneath the sea.", "深い海の底で光る生き物たちの色。"),
  { index: 23, colors: ["#2EC4B6"] });
add("color_lemon", "color", "common",
  t3("레몬 셔벗", "Lemon Sorbet", "レモンシャーベット"),
  t3("한입 베어 물면 새콤할 것 같은 노랑.", "A yellow you can almost taste — bright and tangy.", "ひと口かじれば甘酸っぱそうな黄色。"),
  { index: 24, colors: ["#F9F48B"] });
add("color_cherry_rain", "color", "rare",
  t3("벚꽃비", "Cherry Rain", "桜雨"),
  t3("연분홍에서 진분홍으로, 꽃잎이 쏟아지는 순간.", "From blush to deep pink — the moment the petals pour down.", "淡いピンクから濃いピンクへ、花びらが降りそそぐ瞬間。"),
  { index: 25, colors: ["#FFD1E3", "#FF6FA5"] });
add("color_night_sea", "color", "rare",
  t3("밤바다", "Night Sea", "夜の海"),
  t3("깊은 남색 위로 달빛이 번지는 바다.", "Moonlight spreading over deep indigo water.", "深い藍色に月明かりがにじむ海。"),
  { index: 26, colors: ["#1E3A8A", "#4FB3FF"] });
add("color_firefly", "color", "rare",
  t3("반딧불 숲", "Firefly Forest", "ホタルの森"),
  t3("짙은 숲 속에서 하나둘 켜지는 연둣빛.", "Yellow-green lights blinking on, one by one, in a dark forest.", "深い森にひとつ、ふたつと灯る黄緑の光。"),
  { index: 27, colors: ["#0B6E4F", "#D4F75B"] });
add("color_opal", "color", "epic",
  t3("오팔", "Opal", "オパール"),
  t3("보는 각도마다 다른 빛이 숨어 있는 보석.", "A gem hiding a different light at every angle.", "見る角度ごとに違う光がひそむ宝石。"),
  { index: 28, colors: ["#A8FFE0", "#FDFBD3", "#FFB3E6"] });
add("color_nebula", "color", "epic",
  t3("성운의 숨결", "Nebula Breath", "星雲の息吹"),
  t3("별이 태어나는 구름 속, 보라에서 노을빛까지.", "Inside the clouds where stars are born — violet fading into sunset.", "星が生まれる雲の中、紫から夕焼け色まで。"),
  { index: 29, colors: ["#5B2C9F", "#E0607E", "#FFB37B"] });
add("color_aurora", "color", "epic",
  t3("극광", "Borealis", "オーロラ"),
  t3("북쪽 하늘에 커튼처럼 드리우는 초록·하늘·보라.", "Green, sky and violet hanging like curtains over the northern sky.", "北の空にカーテンのように垂れる緑・空色・紫。"),
  { index: 30, colors: ["#00F5A0", "#00D9F5", "#9B5CFF"] });

// ── 칭호 ──────────────────────────────────────────────────────────────────
add("title_night_stroller", "title", "common",
  t3("밤산책자", "Night Stroller", "夜の散歩者"),
  t3("늦은 밤 동네를 한 바퀴 도는 사람.", "Someone who takes a slow lap around the neighborhood late at night.", "夜更けに町をひと回りする人。"),
  { colors: ["#CFE3FF"] });
add("title_star_collector", "title", "common",
  t3("별 수집가", "Star Collector", "星のコレクター"),
  t3("남의 별을 하나하나 열어 보는 게 취미.", "Opening other people's stars, one by one, is their hobby.", "人の星をひとつずつ開けるのが趣味。"),
  { colors: ["#FFE6A8"] });
add("title_moon_courier", "title", "common",
  t3("달빛 우체부", "Moonlight Courier", "月明かりの郵便屋"),
  t3("밤마다 누군가에게 이야기를 배달해요.", "Delivers a story to someone every night.", "毎晩だれかに物語を届けます。"),
  { colors: ["#E8DDFF"] });
add("title_ferryman", "title", "rare",
  t3("은하수 뱃사공", "Milky Way Ferryman", "天の川の渡し守"),
  t3("이쪽 별에서 저쪽 별로 마음을 건네주는 사람.", "Ferries feelings from one star to another.", "こちらの星からあちらの星へ、心を渡す人。"),
  { colors: ["#9FD3FF", "#E6C8FF"] });
add("title_dawn_waiter", "title", "rare",
  t3("새벽을 기다리는 사람", "One Who Awaits Dawn", "夜明けを待つ人"),
  t3("가장 어두운 시간을 지나 빛이 오는 걸 지켜본 사람.", "Stayed through the darkest hour to watch the light arrive.", "いちばん暗い時間を越えて、光が来るのを見届けた人。"),
  { colors: ["#FFB59A", "#FFE6A8"] });
add("title_star_weaver", "title", "epic",
  t3("별을 엮는 사람", "Weaver of Stars", "星を紡ぐ人"),
  t3("흩어진 별들을 이어 별자리를 만드는 손.", "Hands that thread scattered stars into constellations.", "散らばった星をつないで星座にする手。"),
  { colors: ["#7CF5D6", "#9FB3FF", "#F5A3FF"] });
add("title_lighthouse", "title", "epic",
  t3("잠들지 않는 등대", "The Sleepless Lighthouse", "眠らない灯台"),
  t3("누군가 길을 잃지 않게 밤새 불을 켜 두는 사람.", "Keeps the light on all night so no one loses their way.", "だれも道に迷わないよう、夜通し灯りをともす人。"),
  { colors: ["#FFE29A", "#FF9E7A", "#B48CFF"] });
add("title_night_composer", "title", "legendary",
  t3("밤하늘의 작곡가", "Composer of the Night Sky", "夜空の作曲家"),
  t3("별 하나하나가 음표라면, 이 사람의 지도는 한 곡의 노래예요.", "If every star were a note, this person's map would be a song.", "星のひとつひとつが音符なら、この人の地図は一曲の歌です。"),
  { colors: ["#FFE6A8", "#FF8FD1", "#8EC5FF"], shimmer: true });

// ── 프로필 프레임 ─────────────────────────────────────────────────────────
add("frame_silver", "frame", "common",
  t3("은빛 고리", "Silver Ring", "銀の輪"),
  t3("군더더기 없는 은빛 테두리.", "A clean silver border, nothing more.", "飾り気のない銀のふち取り。"),
  { pattern: "ring", colors: ["#F2F5FA", "#A9B4C6"], width: 0.05 });
add("frame_dawn", "frame", "rare",
  t3("새벽 고리", "Dawn Ring", "夜明けの輪"),
  t3("복숭아빛·금빛·보랏빛이 한 바퀴를 도는 새벽 하늘.", "Dawn sky in a loop — peach, gold, and violet.", "ピーチ・金・紫がひと巡りする夜明けの空。"),
  { pattern: "ring", colors: ["#FFB59A", "#FFE6A8", "#B8A2FF"], width: 0.06, glow: true });
add("frame_dotted_orbit", "frame", "rare",
  t3("점선 궤도", "Dotted Orbit", "点線の軌道"),
  t3("천천히 도는 인공위성의 궤도.", "The slow-turning orbit of a satellite.", "ゆっくり回る人工衛星の軌道。"),
  { pattern: "dash", colors: ["#9FD3FF", "#CFE3FF"], width: 0.045, count: 24, speed: 40 });
add("frame_three_stars", "frame", "epic",
  t3("세 별의 궤도", "Orbit of Three", "三つ星の軌道"),
  t3("금빛·하늘빛·분홍빛 별 셋이 나를 중심으로 돌아요.", "Three stars — gold, sky, and pink — circle around you.", "金・空色・ピンクの三つの星が、あなたを中心に回ります。"),
  { pattern: "orbit", colors: ["#FFE6A8", "#9FD3FF", "#F5A3FF"], width: 0.055, count: 3, speed: 14, glow: true });
add("frame_blossom", "frame", "epic",
  t3("벚꽃 화관", "Blossom Wreath", "桜の花冠"),
  t3("꽃잎 열두 장을 엮어 만든 화관.", "A wreath woven from twelve petals.", "十二枚の花びらで編んだ花冠。"),
  { pattern: "petals", colors: ["#FFD1E3", "#FF9CC2"], width: 0.05, count: 12, speed: 140 });
add("frame_constellation", "frame", "legendary",
  t3("별자리 화관", "Constellation Crown", "星座の冠"),
  t3("아홉 개의 별이 선으로 이어져 나를 감싸고, 하나씩 깜빡여요.", "Nine stars joined by lines encircle you, each twinkling in turn.", "九つの星が線でつながってあなたを囲み、ひとつずつまたたきます。"),
  { pattern: "constellation", colors: ["#FFFFFF", "#FFE6A8", "#9FD3FF"], width: 0.05, count: 9, speed: 50, glow: true });
add("frame_aurora", "frame", "legendary",
  t3("오로라 베일", "Aurora Veil", "オーロラのヴェール"),
  t3("초록·하늘·보랏빛 극광 커튼이 사진 둘레에서 일렁이고, 빛줄기가 밤하늘로 번져요.", "Curtains of green, sky-blue and violet aurora ripple around your photo, their rays bleeding into the night.", "緑・空色・紫のオーロラのカーテンが写真のまわりで揺らめき、光の筋が夜空へにじみます。"),
  { pattern: "aurora", colors: ["#3DFFB0", "#36D6FF", "#A16BFF"], width: 0.06, speed: 18, glow: true });

// ── 이름 배지 ─────────────────────────────────────────────────────────────
add("badge_crescent", "badge", "common",
  t3("초승달 배지", "Crescent Badge", "三日月バッジ"),
  t3("이름 옆에 걸린 작은 초승달.", "A little crescent hung beside your name.", "名前のとなりにかかる小さな三日月。"),
  { path: B.crescent(), colors: ["#FFE6A8"] });
add("badge_twinkle", "badge", "common",
  t3("반짝이 배지", "Twinkle Badge", "きらめきバッジ"),
  t3("반짝, 하고 이름을 밝혀 줘요.", "A little twinkle that lights up your name.", "キラッと名前を照らします。"),
  { path: B.twinkle(), colors: ["#CFE3FF"] });
add("badge_planet", "badge", "rare",
  t3("고리 행성 배지", "Ringed Planet Badge", "環のある惑星バッジ"),
  t3("고리를 두른 작은 행성.", "A tiny planet wearing its ring.", "環をまとった小さな惑星。"),
  { path: B.ringedPlanet(), colors: ["#9FD3FF", "#E6C8FF"] });
add("badge_feather", "badge", "rare",
  t3("깃털 배지", "Feather Badge", "羽根バッジ"),
  t3("가볍게 써 내려간 이야기에 어울리는 깃털.", "A feather for stories written with a light hand.", "軽やかに綴られた物語に似合う羽根。"),
  { path: B.feather(), colors: ["#FFD1E3", "#FF8FB8"] });
add("badge_shooting_star", "badge", "epic",
  t3("유성 배지", "Shooting Star Badge", "流れ星バッジ"),
  t3("꼬리 셋을 끌며 떨어지는 유성. 소원을 빌 시간이에요.", "A meteor trailing three tails — time to make a wish.", "三本の尾を引いて落ちる流れ星。願いごとをする時間です。"),
  { path: B.shootingStar(), colors: ["#FFF6C2", "#FFC27A", "#FF7AB6"] });
add("badge_phoenix", "badge", "legendary",
  t3("불사조", "Phoenix", "不死鳥"),
  t3("불꽃 속에서 다시 날아오르는 새. 이름 옆에서 금빛 날개를 활짝 펼쳐요.", "The bird that rises again from the flames, spreading golden wings beside your name.", "炎の中から再び舞い上がる鳥。名前のそばで金色の翼を大きく広げます。"),
  { path: B.phoenix(), colors: ["#FFF6C2", "#FF7A59", "#C2185B"] });

// ── 지도 별 오라 ──────────────────────────────────────────────────────────
// pattern: halo(빛) · pulse(퍼지는 고리) · orbit(count 개 위성) · fireflies(count 개 반딧불) · spiral(나선 별가루) · saturn(기운 고리) · comet(혜성)
// speed = 한 주기(초) — 회전형은 한 바퀴, 맥동형은 한 번 퍼지는 시간. 앱은 모르는 pattern 을 halo 로 그린다.
add("aura_halo", "aura", "rare",
  t3("은은한 후광", "Gentle Halo", "やわらかな後光"),
  t3("내 별 둘레에 숨 쉬듯 번지는 빛.", "Light that breathes softly around your stars.", "あなたの星のまわりに、息づくように広がる光。"),
  { pattern: "halo", colors: ["#CFE3FF", "#9FB3FF"], speed: 4.5 });
add("aura_pulse", "aura", "epic",
  t3("별의 맥박", "Star Pulse", "星の鼓動"),
  t3("잔잔한 숨결처럼 금빛 물결이 천천히 퍼져 나가요.", "Golden ripples spread out slowly, like a calm breath.", "穏やかな息のように、金色の波紋がゆっくり広がります。"),
  { pattern: "pulse", colors: ["#FFE6A8", "#FFB59A"], speed: 4.2 });
add("aura_saturn", "aura", "legendary",
  t3("토성의 고리", "Rings of Saturn", "土星の環"),
  t3("겹겹의 금빛 고리가 비스듬히 내 별을 감싸고, 작은 위성 하나가 고리를 따라 돌아요.", "Layered golden rings wrap your star at a tilt, and a tiny moon rides along them.", "幾重もの金色の環が斜めに星を包み、小さな衛星が環にそって回ります。"),
  { pattern: "saturn", colors: ["#FFF1C9", "#F2B66D", "#9FD3FF"], tilt: -20, speed: 14 });
add("aura_moon", "aura", "rare",
  t3("달빛 위성", "Moonlit Satellite", "月明かりの衛星"),
  t3("작은 달 하나가 내 별 곁을 천천히 맴돌아요.", "A tiny moon slowly circles your stars.", "小さな月がひとつ、あなたの星のそばをゆっくり巡ります。"),
  { pattern: "orbit", count: 1, colors: ["#F4F1E0", "#9FB3FF"], speed: 10 });
add("aura_fireflies", "aura", "rare",
  t3("반딧불 산책", "Firefly Stroll", "ホタルの散歩"),
  t3("연둣빛 반딧불 다섯 마리가 별 둘레를 느긋하게 날아다녀요.", "Five yellow-green fireflies drift lazily around your stars.", "黄緑のホタル五匹が、星のまわりをのんびり飛びまわります。"),
  { pattern: "fireflies", count: 5, colors: ["#E4FF8A", "#7CF5A8"], speed: 16 });
add("aura_twins", "aura", "epic",
  t3("쌍둥이 별", "Twin Stars", "ふたご星"),
  t3("하늘빛과 분홍빛 두 별이 서로를 마주 보며 궤도를 돌아요.", "A sky-blue star and a pink star orbit, always facing each other.", "空色と桃色のふたつの星が、向かい合ったまま軌道を回ります。"),
  { pattern: "orbit", count: 2, colors: ["#9FD3FF", "#FFB3E6"], speed: 9 });
add("aura_spiral", "aura", "epic",
  t3("은하 소용돌이", "Galactic Swirl", "銀河のうず"),
  t3("별가루가 나선 팔을 그리며 내 별을 중심으로 천천히 돌아요.", "Stardust traces spiral arms, slowly turning around your star.", "星くずが渦巻く腕を描き、あなたの星を中心にゆっくり回ります。"),
  { pattern: "spiral", colors: ["#F0DCFF", "#8C7CFF"], speed: 20 });
add("aura_comet", "aura", "legendary",
  t3("혜성의 궤적", "Comet's Trail", "彗星の軌跡"),
  t3("푸른 꼬리를 길게 끄는 혜성이 내 별 둘레를 쉼 없이 돌아요.", "A comet with a long blue tail circles your stars without rest.", "青い尾を長く引く彗星が、星のまわりを休まず巡ります。"),
  { pattern: "comet", colors: ["#FFFFFF", "#8FE6FF", "#7C5CFF"], speed: 7 });

// ── 천체 망원경 ───────────────────────────────────────────────────────────
add("telescope_refractor", "telescope", "common",
  t3("굴절 망원경", "Refracting Telescope", "屈折望遠鏡"),
  t3("업적 화면에서 ??? 로 가려진 조건 하나를 또렷하게 보여 줘요. 쓰면 사라져요.", "Reveals one achievement condition hidden as ??? on the achievements screen. Used up on use.", "実績画面で ??? になっている条件をひとつはっきり見せてくれます。使うとなくなります。"),
  { mode: "reveal", icon: B.refractor(), colors: ["#E9EEF5", "#9FB3C8"] });
add("telescope_reflector", "telescope", "rare",
  t3("반사 망원경", "Reflecting Telescope", "反射望遠鏡"),
  t3("세상에 단 한 명만 가질 수 있는 히든 업적 하나의 흐릿한 단서를 비춰 줘요.", "Shows a faint clue to one hidden achievement — the kind only one person in the world can earn.", "世界でただ一人しか得られない隠し実績の、かすかな手がかりを映します。"),
  { mode: "hint", level: 1, icon: B.reflector(), colors: ["#9FD3FF", "#5B7CFF"] });
add("telescope_radio", "telescope", "epic",
  t3("전파 망원경", "Radio Telescope", "電波望遠鏡"),
  t3("보이지 않는 신호까지 잡아, 히든 업적 하나의 선명한 단서를 알려 줘요.", "Catches even invisible signals to give a clear clue to one hidden achievement.", "見えない信号までとらえ、隠し実績ひとつのはっきりした手がかりを教えてくれます。"),
  { mode: "hint", level: 2, icon: B.radioDish(), colors: ["#E6C8FF", "#9B5CFF"] });

// ── 열쇠 묶음 ─────────────────────────────────────────────────────────────
add("keys_3", "keyBundle", "common",
  t3("열쇠 묶음", "Key Bundle", "鍵の束"),
  t3("고리에 꿰인 열쇠 세 개. 상자에서 나오자마자 열쇠가 돼요.", "Three keys on a ring — they become keys the moment they leave the box.", "輪に通した鍵が三つ。箱から出たとたん鍵になります。"),
  { keys: 3 });
add("keys_6", "keyBundle", "rare",
  t3("큰 열쇠 묶음", "Big Key Bundle", "大きな鍵の束"),
  t3("묵직한 열쇠 여섯 개. 멀리 있는 별들을 마음껏 열어 보세요.", "Six heavy keys. Open faraway stars to your heart's content.", "ずっしりした鍵が六つ。遠くの星を思う存分開けてみて。"),
  { keys: 6 });

/** 공개 설정 — functions/starlight.js DEFAULT_CONFIG 와 같은 값(이 문서가 있으면 서버·앱 모두 이걸 쓴다). */
const config = {
  questStardust: 10,
  bonusStardust: 20,
  keyCost: 10,
  boxCost: 100,
  rarityWeights: { common: 60, rare: 28, epic: 10, legendary: 2 },
  pityEpic: 10,
  pityLegendary: 50,
  dupRefund: { common: 10, rare: 25, epic: 50, legendary: 100 },
  maxOpen: 10,
  maxExchange: 99,
  /** 공방 "성운 상자 열기" 영역에 겹쳐 보여 줄 대표 아이템(전설) — 바꾸려면 여기만. */
  showcase: ["shape_compass", "frame_constellation", "aura_saturn"],
};

module.exports = { items, config };

if (require.main === module && process.argv.includes("--json")) {
  process.stdout.write(JSON.stringify({ items, config }, null, 1));
}
