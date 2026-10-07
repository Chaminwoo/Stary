/**
 * 다이어리 자동 선번역(2026-10-07).
 *
 * 다이어리가 평소대로 저장된 **뒤에** 서버가 비동기로 제목/본문을 번역해 같은 문서(diaries/{id})의
 * `translations` 필드에 저장한다. 번역 때문에 다이어리 생성·UI 가 영향받지 않으며(실패해도 조용히 로그만),
 * 번역 API 호출은 서버에서만 일어난다(클라이언트에는 키도, 호출도 없다).
 *
 * ── 저장 구조 (diaries/{id}.translations — 언어 코드가 key 라서 언어를 늘려도 구조가 같다) ─────────────
 *   translations: {
 *     sourceLang: "ko",                 // 감지된 원문 언어
 *     title:   { ko: "원문", en: "...", ja: "...", zh: "..." },   // 원문 언어 key 에도 원문을 그대로 둔다
 *     content: { ko: "원문", en: "...", ja: "...", zh: "..." },
 *     status:  "done" | "partial" | "failed" | "skipped" | "processing",
 *     srcHash: "…",                     // 번역한 원문(제목+본문) 지문 — 같은 원문은 다시 번역하지 않고, 수정되면 달라진다
 *     updatedAt: <epoch ms>,
 *     startedAt: <epoch ms>,            // processing 일 때만(중복 실행 방지용 임대 시각)
 *   }
 *
 * ── 클라이언트가 읽는 규칙(권장) ────────────────────────────────────────────────────────────────────
 *   표시 언어 L 의 제목 = translations.title[L] 이 있으면 그것, 없으면 원문(title). translations 자체가 없는 예전
 *   다이어리·번역 실패/진행 중인 다이어리도 같은 규칙으로 원문이 나오므로 별도 분기가 필요 없다.
 *   (수정 직후 잠깐 낡은 번역이 남는 것이 걱정되면 translations.title[sourceLang] == title 인지로 최신 여부를 알 수 있다.)
 *
 * ── 언어 추가 ────────────────────────────────────────────────────────────────────────────────────────
 *   TRANSLATION_LANGS 에 코드(예: "es") 한 줄 추가 후 functions 재배포. 구조·로직 변경 없음.
 *   이미 번역된 다이어리는 translateDiary 가 다시 불릴 때 **빠진 언어만** 채운다(이미 있는 언어는 재호출 안 함).
 *
 * ── 번역 API ────────────────────────────────────────────────────────────────────────────────────────
 *   Google Cloud Translation v3(NMT). Cloud Functions 런타임 서비스 계정 권한(ADC)으로 호출하므로 API 키·시크릿이 없다.
 *   필요: 프로젝트에서 translate.googleapis.com 활성화 + 런타임 서비스 계정에 roles/cloudtranslate.user (docs/PROJECT_NOTES 8.77).
 */
const crypto = require("crypto");
const { FieldValue } = require("firebase-admin/firestore");
const logger = require("firebase-functions/logger");

const DIARIES = "diaries";

/** 번역해 두는 언어(= translations 맵의 key). 원문 언어는 자동으로 제외된다. */
const TRANSLATION_LANGS = ["ko", "en", "ja", "zh"];

/**
 * 한 번 번역을 시작하면 이 시간 동안은 같은 원문에 대한 다른 실행(이벤트 중복 전달 등)이 끼어들지 못한다.
 * 함수 timeout(120초)보다 길어야 한다 — 실행이 도중에 죽어도 이 시간이 지나면 다음 실행이 이어받는다.
 */
const LEASE_MS = 3 * 60 * 1000;

/** 언어 감지에 쓰는 앞부분 길이(글자 수) — 감지는 짧아도 충분하고 글자 수만큼 과금된다. */
const DETECT_SAMPLE_CHARS = 300;

const str = (v) => (typeof v === "string" ? v : "");

/** 번역 대상 원문의 지문 — 제목/본문이 같으면 같은 값. */
function textHash(title, content) {
  return crypto.createHash("sha1").update(`${title}\u0000${content}`).digest("hex").slice(0, 16);
}

/** Google 언어 코드(zh-CN, zh-TW, pt-BR …) → 주 언어 코드(zh, pt). 판별 불가("und"/빈 값)는 null. */
function normalizeLang(code) {
  const base = String(code || "").toLowerCase().split("-")[0];
  return base && base !== "und" ? base : null;
}

/** 아직 번역이 없는 대상 언어(원문 언어 제외). 제목·본문이 둘 다 있어야 "있음"으로 본다. */
function missingLangs(sourceLang, t) {
  const has = (field, lang) => typeof (t && t[field] && t[field][lang]) === "string";
  return TRANSLATION_LANGS.filter(
    (lang) => lang !== sourceLang && !(has("title", lang) && has("content", lang))
  );
}

let gClient = null;

/**
 * 기본 번역기 — Google Cloud Translation v3. 번역기는 {detect, translate} 두 함수만 지키면 되므로
 * 다른 서비스로 바꿀 때(또는 테스트에서) 이 객체만 갈아 끼우면 된다.
 */
function googleTranslator() {
  const { v3 } = require("@google-cloud/translate");
  gClient = gClient || new v3.TranslationServiceClient();
  const parent = async () => `projects/${await gClient.getProjectId()}/locations/global`;
  return {
    /** 원문 언어 코드(주 언어). 판별 불가면 null. */
    async detect(sample) {
      const [res] = await gClient.detectLanguage({
        parent: await parent(),
        content: sample,
        mimeType: "text/plain",
      });
      const top = res.languages && res.languages[0];
      return normalizeLang(top && top.languageCode);
    },
    /** texts 를 from → to 로 번역해 같은 순서·같은 개수로 돌려준다(text/plain: 줄바꿈·특수문자 그대로). */
    async translate(texts, from, to) {
      const [res] = await gClient.translateText({
        parent: await parent(),
        contents: texts,
        mimeType: "text/plain",
        sourceLanguageCode: from,
        targetLanguageCode: to,
      });
      const out = (res.translations || []).map((t) => t.translatedText || "");
      if (out.length !== texts.length) {
        throw new Error(`번역 결과 개수 불일치(${out.length}/${texts.length})`);
      }
      return out;
    },
  };
}

/**
 * 번역을 시작해도 되는지 판정하고 임대(processing)를 건다 — 한 트랜잭션이라 같은 원문에 동시에 둘이 시작할 수 없다.
 * 번역하지 않을 이유가 있으면 { reason }, 진행해야 하면 { run: true, ... } 를 돌려준다.
 */
function claim(db, ref) {
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) return { reason: "문서 없음" };
    const d = snap.data();
    // 나만 보기: 읽는 사람이 작성자 본인뿐이라 번역할 가치가 없고, 사적인 글을 외부 API 로 보내지도 않는다.
    if ((d.visibilityType || "public") === "private") return { reason: "나만 보기" };
    const title = str(d.title);
    const content = str(d.content);
    if (!title.trim() && !content.trim()) return { reason: "원문 없음" };

    const hash = textHash(title, content);
    const t = d.translations;
    const now = Date.now();
    if (t && t.srcHash === hash) {
      // 같은 원문에 대한 번역이 이미 있음/진행 중 → 중복 호출 방지.
      if (t.status === "skipped") return { reason: "번역 불필요" };
      if (t.status === "processing" && now - (t.startedAt || 0) < LEASE_MS) {
        return { reason: "이미 진행 중" };
      }
      if (t.sourceLang && missingLangs(t.sourceLang, t).length === 0) return { reason: "이미 완료" };
      // 이전 시도가 실패/중단된 경우 — 있는 언어는 두고 빠진 언어만 이어서 번역한다.
      tx.update(ref, { "translations.status": "processing", "translations.startedAt": now });
      return { run: true, title, content, hash, have: t };
    }
    // 처음이거나 원문이 수정됨 → 낡은 번역을 버리고 새로 시작.
    tx.update(ref, { translations: { srcHash: hash, status: "processing", startedAt: now } });
    return { run: true, title, content, hash, have: null };
  });
}

/**
 * 결과 저장 — 그 사이 문서가 삭제되거나 원문이 수정됐다면(= 지문이 달라졌다면) 낡은 결과를 버린다.
 * (수정 쪽 실행이 새 원문으로 다시 번역한다.) 저장했으면 true.
 */
function commit(db, ref, hash, fields) {
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) return false;
    const d = snap.data();
    const same =
      d.translations &&
      d.translations.srcHash === hash &&
      textHash(str(d.title), str(d.content)) === hash;
    if (!same) return false;
    tx.update(ref, { ...fields, "translations.startedAt": FieldValue.delete() });
    return true;
  });
}

/**
 * diaries/{diaryId} 의 제목/본문을 번역해 translations 에 저장한다. **절대 throw 하지 않는다**(실패는 로그 + status).
 * 같은 원문에 대해 여러 번 불려도(이벤트 중복 전달·재시도) API 는 한 번만 호출된다.
 * @returns 처리 결과 요약(로그/테스트용)
 */
async function translateDiary(db, diaryId, translator) {
  const ref = db.collection(DIARIES).doc(diaryId);
  let job = null;
  try {
    job = await claim(db, ref);
    if (!job.run) {
      logger.info(`translate ${diaryId}: 생략 (${job.reason})`);
      return job.reason;
    }

    const { title, content, hash } = job;
    const api = translator || googleTranslator();
    const have = job.have || {};

    let sourceLang = have.sourceLang || null;
    if (!sourceLang) {
      sourceLang = await api.detect([...`${title}\n${content}`.trim()].slice(0, DETECT_SAMPLE_CHARS).join(""));
      if (!sourceLang) {
        // 이모지·숫자만 있는 글 등 — 번역할 게 없다.
        await commit(db, ref, hash, { "translations.status": "skipped", "translations.updatedAt": Date.now() });
        logger.info(`translate ${diaryId}: 언어 판별 불가 → 번역 불필요로 표시`);
        return "번역 불필요";
      }
    }

    const targets = missingLangs(sourceLang, have);
    // 비어 있는 필드는 API 에 보내지 않는다(빈 문자열은 오류가 나고 과금 대상도 아니다).
    const texts = [title, content].filter((s) => s.trim());
    const results = await Promise.allSettled(targets.map((to) => api.translate(texts, sourceLang, to)));

    const fields = {
      "translations.sourceLang": sourceLang,
      [`translations.title.${sourceLang}`]: title,
      [`translations.content.${sourceLang}`]: content,
    };
    let done = 0;
    results.forEach((r, i) => {
      const lang = targets[i];
      if (r.status !== "fulfilled") {
        logger.warn(`translate ${diaryId}: ${sourceLang}→${lang} 실패 — ${r.reason && (r.reason.code || "")} ${r.reason && r.reason.message}`);
        return;
      }
      let k = 0;
      fields[`translations.title.${lang}`] = title.trim() ? r.value[k++] : "";
      fields[`translations.content.${lang}`] = content.trim() ? r.value[k++] : "";
      done++;
    });
    const hadBefore = TRANSLATION_LANGS.filter((l) => l !== sourceLang).length - targets.length;
    fields["translations.status"] =
      done === targets.length ? "done" : done + hadBefore > 0 ? "partial" : "failed";
    fields["translations.updatedAt"] = Date.now();

    const saved = await commit(db, ref, hash, fields);
    logger.info(
      `translate ${diaryId}: ${sourceLang}→[${targets.join(",")}] ${done}/${targets.length} ` +
        (saved ? `저장(${fields["translations.status"]})` : "저장 생략(그 사이 수정/삭제됨)")
    );
    return saved ? fields["translations.status"] : "폐기";
  } catch (e) {
    logger.error(`translate ${diaryId}: 실패 — ${e.code || ""} ${e.message}`);
    if (job && job.run) {
      // 임대를 풀고 실패를 남긴다(다음 실행/수정이 이어받을 수 있게). 이것마저 실패해도 임대가 곧 만료된다.
      await commit(db, ref, job.hash, {
        "translations.status": "failed",
        "translations.updatedAt": Date.now(),
      }).catch(() => {});
    }
    return "error";
  }
}

module.exports = { translateDiary, TRANSLATION_LANGS };
