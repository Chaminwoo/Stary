/**
 * 히든 업적 단서(천체 망원경) — seed.js 가 `starlightHints/{업적 id}` 로 올린다(규칙상 아무도 못 읽고, 망원경을 쓴 사람 지갑에만 복사된다).
 * 레벨 1 = 반사 망원경(흐릿한 단서: 분위기·방향만), 레벨 2 = 전파 망원경(선명한 단서: 무엇을 해야 하는지 거의 보인다).
 * 업적 id 는 Android `HiddenAchievements.kt` / iOS `HiddenAchievements.swift` 와 같다. 히든 업적을 새로 만들면 여기도 추가.
 */
const t3 = (ko, en, ja) => ({ ko, en, ja });

module.exports = {
  secret_word: {
    1: t3("어떤 낱말은 별에게 보내는 암호가 돼요. 제목을 짓는 순간을 눈여겨보세요.",
      "Some words are passwords sent to the stars. Pay attention when you name your entry.",
      "ある言葉は星への合言葉になります。タイトルをつける瞬間に注目して。"),
    2: t3("우주를 떠도는 아주 작은 알갱이… 그 이름을 별의 제목에 적어 보세요.",
      "The tiniest grains drifting through space… write their name in a star's title.",
      "宇宙をただよう、ごく小さな粒… その名前を星のタイトルに書いてみて。"),
  },
  remote_place: {
    1: t3("사람의 발길이 거의 닿지 않는 곳 — 가장 춥거나, 가장 높은 곳.",
      "A place few people ever reach — the coldest, or the highest.",
      "人の足がほとんど届かない場所 — いちばん寒いか、いちばん高いところ。"),
    2: t3("얼음 대륙의 끝, 혹은 세상에서 가장 높은 봉우리에 별을 남겨 보세요.",
      "Leave a star at the end of the ice continent, or on the world's highest peak.",
      "氷の大陸の果て、または世界でいちばん高い峰に星を残してみて。"),
  },
  place_desert: {
    1: t3("모래와 태양만이 끝없이 이어지는 곳에서 빛이 기다려요.",
      "Light waits where only sand and sun stretch on forever.",
      "砂と太陽だけがどこまでも続く場所で、光が待っています。"),
    2: t3("아프리카 북부, 세계에서 가장 넓은 뜨거운 사막.",
      "Northern Africa — the largest hot desert in the world.",
      "アフリカ北部、世界でいちばん広い暑い砂漠。"),
  },
  place_trench: {
    1: t3("빛이 한 번도 닿지 않은, 가장 깊은 곳.",
      "The deepest place, where light has never reached.",
      "光が一度も届いたことのない、いちばん深い場所。"),
    2: t3("태평양 서쪽 바닷속, 지구에서 가장 깊은 골짜기 위.",
      "Above the deepest trench on Earth, in the western Pacific.",
      "太平洋の西、地球でいちばん深い海の谷の上。"),
  },
  place_triangle: {
    1: t3("배와 비행기가 사라졌다는 소문이 도는 바다가 있어요.",
      "There is a sea where ships and planes are rumored to vanish.",
      "船や飛行機が消えるという噂の海があります。"),
    2: t3("대서양, 세 섬과 도시를 잇는 전설의 삼각 해역.",
      "The legendary triangle of the Atlantic, between three islands and cities.",
      "大西洋の、三つの島と街を結ぶ伝説の三角海域。"),
  },
  all_rounder: {
    1: t3("모든 길을 끝까지 걸어 본 사람에게 주어지는 자리.",
      "A place reserved for someone who has walked every road to its end.",
      "すべての道を最後まで歩いた人に与えられる場所。"),
    2: t3("히든을 뺀 업적을 하나도 빠짐없이 모아 보세요.",
      "Collect every achievement except the hidden ones — without missing one.",
      "隠し実績を除くすべての実績を、ひとつ残らず集めてみて。"),
  },
  cosmic_rascal: {
    1: t3("남의 별을 아주, 아주 많이 들여다본 사람.",
      "Someone who has peeked into a great, great many stars.",
      "人の星を、とても、とてもたくさん覗いた人。"),
    2: t3("다른 사람의 별을 수백 개 — 300개쯤 열어 보면….",
      "Open other people's stars by the hundreds — around 300…",
      "人の星を何百個も — 300個ほど開けてみると…。"),
  },
  lone_observer: {
    1: t3("곁에 아무도 없어도 꾸준히 빛을 내는 사람.",
      "Someone who keeps shining even with no one beside them.",
      "そばに誰もいなくても、こつこつ光を放つ人。"),
    2: t3("친구 없이, 별을 50개 남겨 보세요.",
      "Leave 50 stars without any friends.",
      "友達なしで、星を50個残してみて。"),
  },
  meteor_seven: {
    1: t3("소원은 한 번으로 끝나지 않아요. 연달아 빌어 보세요.",
      "One wish is never enough. Try wishing again and again, without a break.",
      "願いごとは一度では終わりません。続けて願ってみて。"),
    2: t3("지구본에서 유성을 놓치지 않고 일곱 개 연속으로.",
      "On the globe, catch seven meteors in a row without missing one.",
      "地球儀で、流れ星を逃さず七つ連続で。"),
  },
  heart_frenzy: {
    1: t3("마음이 두근거리면 손이 멈추지 않죠.",
      "When your heart races, your hands won't stop.",
      "胸がドキドキすると、手が止まらなくなりますよね。"),
    2: t3("프로필 화면을 떠나지 말고, 하트를 쉬지 않고 100번.",
      "Without leaving the profile screen, tap the heart 100 times.",
      "プロフィール画面を離れずに、ハートを休まず100回。"),
  },
  melomaniac: {
    1: t3("모든 노래를 끝까지 들어 본 사람만 들을 수 있는 소리가 있어요.",
      "There is a sound only those who have heard every song can hear.",
      "すべての曲を最後まで聴いた人にだけ聞こえる音があります。"),
    2: t3("배경음악 화면을 떠나지 않고 모든 곡을 들어 보세요.",
      "Listen to every track without leaving the music screen.",
      "BGM画面を離れずに、すべての曲を聴いてみて。"),
  },
  earth_pilgrim: {
    1: t3("모두가 아는 그곳에 남긴 별이, 누군가에게 닿을 때.",
      "When a star left at a place everyone knows reaches someone.",
      "誰もが知るあの場所に残した星が、誰かに届くとき。"),
    2: t3("세계의 유명 관광지에 별을 남기고, 다른 사람이 그 별을 열어 보게 해요.",
      "Leave a star at a world-famous landmark, and have someone else open it.",
      "世界の有名な観光地に星を残し、ほかの人にその星を開けてもらおう。"),
  },
};
