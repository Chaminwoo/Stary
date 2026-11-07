// 규칙 테스트 실행기 — 비어 있는 포트를 골라 에뮬레이터(demo-stary)를 띄우고 node --test 를 돌린다.
// 사용: node run-tests.js            (npm test 와 같음)   /  JAVA_HOME 은 JDK 21 이상이어야 한다.
const net = require("net");
const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");

function freePort() {
  return new Promise((resolve, reject) => {
    const srv = net.createServer();
    srv.listen(0, "127.0.0.1", () => {
      const { port } = srv.address();
      srv.close(() => resolve(port));
    });
    srv.on("error", reject);
  });
}

(async () => {
  const [fs_, st, hub] = [await freePort(), await freePort(), await freePort()];
  const cfg = {
    firestore: { rules: "placeholder.firestore.rules" },
    storage: { rules: "placeholder.storage.rules" },
    emulators: {
      firestore: { port: fs_, host: "127.0.0.1" },
      storage: { port: st, host: "127.0.0.1" },
      hub: { port: hub, host: "127.0.0.1" },
      ui: { enabled: false },
      singleProjectMode: false,
    },
  };
  const cfgPath = path.join(__dirname, "firebase.generated.json");
  fs.writeFileSync(cfgPath, JSON.stringify(cfg, null, 2));
  const testCmd = "node --test --test-concurrency=1 --test-timeout=60000 test/*.test.js";
  const fb = path.join(__dirname, "node_modules", ".bin", process.platform === "win32" ? "firebase.cmd" : "firebase");
  const bin = fs.existsSync(fb) ? fb : "firebase";
  const r = spawnSync(bin, ["emulators:exec", "--config", "firebase.generated.json", "--only", "firestore,storage", "--project", "demo-stary", `"${testCmd}"`], {
    cwd: __dirname,
    stdio: "inherit",
    shell: process.platform === "win32",
  });
  try { fs.unlinkSync(cfgPath); } catch (_) { /* 무시 */ }
  process.exit(r.status === null ? 1 : r.status);
})();
