// firestore.stage2.rules 가 firestore.rules(Stage 1)에서 build-stage2.js 로 만든 결과와 같은지 — 두 파일이 어긋나면 실패.
const { describe, it } = require("node:test");
const assert = require("node:assert");
const fs = require("fs");
const { build } = require("../build-stage2");
const H = require("./helpers");

describe("규칙 파일 동기화", () => {
  it("stage2 파일 == build(stage1)", () => {
    const s1 = fs.readFileSync(H.RULES.stage1, "utf8");
    const s2 = fs.readFileSync(H.RULES.stage2, "utf8");
    assert.strictEqual(s2, build(s1));
  });
});
