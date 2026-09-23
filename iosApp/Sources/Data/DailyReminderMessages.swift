import Foundation

/// 일일 알림의 **시간대(밴드) + 문구 풀** — Android `DailyReminderScheduler.Band` 와
/// `res/values*/reminder_messages.xml` 패리티(같은 경계·같은 문장·같은 순서).
///
/// 왜 여기 따로 두나: 문구가 밴드당 여러 개라 `L10n` enum 에 케이스로 풀어 넣으면 30개가 넘는다.
/// 배열째 들고 있는 편이 추가/삭제가 쉽고 Android 의 string-array 와 1:1로 비교된다.
enum DailyReminderMessages {

    /// 알림이 울릴 수 있는 시간대 — 낮 11시 ~ 밤 11시 반을 성격이 다른 5구간으로. 아침은 비워 둔다.
    enum Band: String, CaseIterable {
        case noon, afternoon, evening, night, late

        /// 구간 시작(그날의 분). Android `Band.startMinute` 와 같은 값.
        var startMinute: Int {
            switch self {
            case .noon:      return 11 * 60
            case .afternoon: return 13 * 60 + 30
            case .evening:   return 17 * 60
            case .night:     return 19 * 60 + 30
            case .late:      return 22 * 60
            }
        }

        /// 구간 끝(미포함). Android `Band.endMinute` 와 같은 값.
        var endMinute: Int {
            switch self {
            case .noon:      return 13 * 60 + 30
            case .afternoon: return 17 * 60
            case .evening:   return 19 * 60 + 30
            case .night:     return 22 * 60
            case .late:      return 23 * 60 + 30
            }
        }
    }

    /// 현재 인앱 언어의 문구 풀.
    @MainActor
    static func pool(_ band: Band) -> [String] {
        let all = table[band] ?? ([], [], [])
        switch LocaleManager.shared.effectiveLanguage {
        case "en": return all.en
        case "ja": return all.ja
        default:   return all.ko
        }
    }

    private static let table: [Band: (ko: [String], en: [String], ja: [String])] = [
        .noon: (
            ko: [
                "오늘 점심, 뭐 드셨어요? 사진 한 장이면 충분해요",
                "점심시간에 앉은 이 자리도 별이 될 수 있어요",
                "오늘 아침부터 지금까지, 기억에 남은 순간이 있나요?",
                "밥 먹다 문득 든 생각, 그것도 기록이에요",
                "지금 있는 곳의 창밖은 어떤가요?",
                "오늘의 첫 별을 남겨볼까요?",
            ],
            en: [
                "What's for lunch? One photo is plenty",
                "Even this seat you're sitting in could become a star",
                "Anything from this morning worth keeping?",
                "That stray thought over lunch — that counts too",
                "What does it look like outside your window right now?",
                "Shall we leave today's first star?",
            ],
            ja: [
                "今日のお昼、何を食べましたか?写真一枚で十分です",
                "お昼に座っているこの場所も、星になります",
                "今朝からここまで、心に残った瞬間はありますか?",
                "食事中にふと浮かんだ考えも、立派な記録です",
                "今いる場所の窓の外は、どんな様子ですか?",
                "今日最初の星を残してみませんか?",
            ]
        ),
        .afternoon: (
            ko: [
                "오후의 공기는 어떤가요? 한 줄만 남겨도 좋아요",
                "오늘 가장 오래 머문 곳은 어디였나요?",
                "잠깐 고개 들어 하늘 한 번, 그리고 Stary 한 번",
                "지나가다 본 것 중에 마음에 걸린 게 있나요?",
                "커피 한 잔의 시간이면 별 하나를 남길 수 있어요",
                "오늘 이 시간의 빛은 다시 오지 않아요",
            ],
            en: [
                "How's the afternoon air? A single line is enough",
                "Where have you spent the most time today?",
                "Look up at the sky for a second — then open Stary",
                "Did anything you passed by stay with you?",
                "One coffee's worth of time is enough for a star",
                "This afternoon's light won't come around again",
            ],
            ja: [
                "午後の空気はどうですか?一行だけでも十分です",
                "今日いちばん長くいた場所はどこですか?",
                "少し顔を上げて空を、そしてStaryを",
                "通りすがりに心に留まったものはありましたか?",
                "コーヒー一杯の時間で、星ひとつ残せます",
                "今日のこの時間の光は、二度と来ません",
            ]
        ),
        .evening: (
            ko: [
                "오늘 저녁은 무엇을 드셨나요?",
                "돌아가는 길, 오늘 하루는 어땠나요?",
                "해 지는 색이 오늘은 어떤가요?",
                "하루를 정리하기 딱 좋은 시간이에요",
                "오늘 만난 사람, 다녀온 곳 — 하나만 골라보세요",
                "매일 지나는 퇴근길도 누군가에겐 처음 보는 곳이에요",
            ],
            en: [
                "What did you have for dinner tonight?",
                "On your way back — how was today?",
                "What color is the sunset today?",
                "A good time to wrap up the day",
                "Someone you met, somewhere you went — just pick one",
                "The way home you walk every day is new to someone else",
            ],
            ja: [
                "今日の夕食は何を食べましたか?",
                "帰り道、今日はどんな一日でしたか?",
                "今日の夕焼けはどんな色ですか?",
                "一日を整理するのに、ちょうどいい時間です",
                "今日会った人、行った場所 — ひとつだけ選んでみて",
                "毎日通る帰り道も、誰かには初めての場所です",
            ]
        ),
        .night: (
            ko: [
                "오늘 하루를 별 하나에 남겨보세요",
                "오늘 딱 하나만 기억한다면, 무엇일까요?",
                "지금 이 자리에 별 하나를 띄워둘까요?",
                "오늘의 별자리에 아직 빈 자리가 있어요",
                "사진 한 장이면 오늘이 남아요",
                "밤이 깊기 전에, 짧게라도",
            ],
            en: [
                "Leave today behind as a star",
                "If you remember one thing from today, what is it?",
                "Shall we float a star right where you are?",
                "There's still an empty spot in today's constellation",
                "One photo, and today stays",
                "Before the night gets late — even just a line",
            ],
            ja: [
                "今日を星ひとつに残してみましょう",
                "今日ひとつだけ覚えておくなら、何ですか?",
                "今いるこの場所に、星をひとつ浮かべませんか?",
                "今日の星座には、まだ空いた席があります",
                "写真一枚で、今日が残ります",
                "夜が更ける前に、短くても",
            ]
        ),
        .late: (
            ko: [
                "자기 전에 딱 한 줄, 오늘은 어땠나요?",
                "오늘 하루, 별 하나 값은 했잖아요",
                "내일의 내가 오늘을 궁금해할 거예요",
                "불 끄기 전에 남겨두면 내일 아침에 반짝여요",
                "오늘 가장 조용했던 순간은 언제였나요?",
                "하루의 마지막 5분을 오늘에게 주세요",
            ],
            en: [
                "One line before bed — how was today?",
                "Today was worth at least one star",
                "Tomorrow-you will wonder about today",
                "Leave it before lights out and it'll be there in the morning",
                "What was the quietest moment of your day?",
                "Give the last five minutes of the day to today",
            ],
            ja: [
                "寝る前に一行だけ、今日はどうでしたか?",
                "今日も、星ひとつ分の価値はありました",
                "明日の自分が、今日を知りたがります",
                "電気を消す前に残しておけば、朝には光っています",
                "今日いちばん静かだった瞬間はいつでしたか?",
                "一日の最後の5分を、今日にあげてください",
            ]
        ),
    ]
}
