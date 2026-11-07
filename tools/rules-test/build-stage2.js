// firestore.rules (Stage 1) 에서 firestore.stage2.rules 를 만든다 — 두 파일의 차이를 [STAGE2] 표시 구간으로만 한정하려는 도구.
// 사용: node build-stage2.js  (파일을 다시 쓴다)   /  require('./build-stage2').build(stage1Text) 로 비교용 문자열만 얻을 수도 있다.
const fs = require("fs");
const path = require("path");

const BLOCKS = {
  "읽기 규칙": `      // 문서별 공개 범위를 읽기 규칙이 강제한다.
      //  - 단건(get): 전체 공개 / 본인 / 대상 목록(audience)에 포함 / 글쓴이의 친구 목록에 내가 있음.
      //  - 목록(list): 쿼리가 규칙 조건과 같은 형태여야 통과한다(공개 · 내 글 · audience array-contains 나).
      //    visibilityType 이 없는 옛 문서는 백필(tools/security/backfill.js) 전까지 공개 쿼리에 잡히지 않는다.
      allow get: if isSignedIn()
        && (resource.data.get('visibilityType', 'public') == 'public'
            || isMe(resource.data.userId)
            || myAppUserId() in resource.data.get('audience', [])
            || exists(/databases/$(database)/documents/users/$(resource.data.userId)/friends/$(myAppUserId())));
      allow list: if isSignedIn()
        && (resource.data.visibilityType == 'public'
            || isMe(resource.data.userId)
            || myAppUserId() in resource.data.audience);`,
  "friends 쓰기": `        // 내 목록은 내가 쓴다. 상대 목록에 나를 넣는 것(수락)은 그 상대가 나에게 보낸 요청이 있을 때만.
        allow create: if (isMe(userId)
                          || (isMe(friendId)
                              && exists(/databases/$(database)/documents/friendRequests/$(userId + '_' + friendId))))
          && strOk(request.resource.data, 'userName', 200)
          && strOk(request.resource.data, 'photoUrl', 2000);
        allow update: if (isMe(userId) || isMe(friendId))
          && strOk(request.resource.data, 'userName', 200)
          && strOk(request.resource.data, 'photoUrl', 2000);
        allow delete: if isMe(userId) || isMe(friendId);`,
};

// friendRequests 생성은 문서 id 가 "{from}_{to}" 여야 한다는 조건을 더한다.
const REQ_OLD = "      allow create: if isMe(request.resource.data.fromId)\n        && request.resource.data.keys().hasOnly(['id', 'fromId'";
const REQ_NEW = "      allow create: if isMe(request.resource.data.fromId)\n        && requestId == request.resource.data.fromId + '_' + request.resource.data.toId\n        && request.resource.data.keys().hasOnly(['id', 'fromId'";

function build(s1) {
  let out = s1;
  for (const [name, body] of Object.entries(BLOCKS)) {
    const re = new RegExp("(\\s*// \\[STAGE2\\] " + name + " 시작\\n)[\\s\\S]*?(\\n\\s*// \\[STAGE2\\] " + name + " 끝)");
    if (!re.test(out)) throw new Error("marker missing: " + name);
    out = out.replace(re, (_m, a, b) => a + body + b);
  }
  if (!out.includes(REQ_OLD)) throw new Error("friendRequests create block not found");
  out = out.replace(REQ_OLD, REQ_NEW);
  out = out.replace("// Stary Firestore 보안 규칙 (named DB: stary-db) — Stage 1", "// Stary Firestore 보안 규칙 (named DB: stary-db) — Stage 2 (firestore.rules 에서 tools/rules-test/build-stage2.js 로 생성)");
  out = out.replace(/\/\/ 이 파일은 Stage 1 이다\.[\s\S]*?\n\/\/ 두 파일의 차이는[^\n]*\n/,
    "// 이 파일은 Stage 2 다 — 문서별 열람 범위를 읽기 규칙으로 강제한다. 새 앱(versionCode ≥ 35)이 퍼진 뒤,\n" +
    "// 백필(tools/security/backfill.js)을 마치고 배포한다. Stage 1 과의 차이는 [STAGE2] 표시가 붙은 세 곳뿐.\n" +
    "// ⚠️ 이 파일을 직접 고치지 말고 firestore.rules 를 고친 뒤 `node tools/rules-test/build-stage2.js` 로 다시 만들 것.\n");
  out = out.replace("//  읽기 : Stage 1 은 로그인 세션 전체. (Stage 2 에서 문서별 공개 범위로 좁힌다.)", "//  읽기 : 문서별 공개 범위(아래 get / list 규칙).");
  return out;
}

module.exports = { build };

if (require.main === module) {
  const root = path.join(__dirname, "..", "..");
  const s1 = fs.readFileSync(path.join(root, "firestore.rules"), "utf8");
  fs.writeFileSync(path.join(root, "firestore.stage2.rules"), build(s1));
  console.log("wrote firestore.stage2.rules");
}
