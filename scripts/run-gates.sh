#!/usr/bin/env bash
# 测试门禁编排：一条命令跑完全部可自动化的门禁，替代人肉逐个 gradle 命令。
#
# 背景（2026-07-18）：07-18 那轮 22 条用例里，TC-P-001 与 TC-F-001~F-007 的判据
# 本来就由 JVM/androidTest 承载（AccountAuthInjectionHarness / LoginAttemptCoordinatorTest /
# AuthSyncBreakerTest / CacheServiceBatchKeyTest 等），瓶颈不是"没脚本"而是"没编排"。
# 本脚本把它们串起来，产出结构化结果供 results.md 引用。
#
# 用法：
#   ./scripts/run-gates.sh              # 跑全部门禁
#   ./scripts/run-gates.sh framework    # 只跑框架自检（不需要设备）
#   ./scripts/run-gates.sh jvm          # 只跑 debox JVM 单测
#   ./scripts/run-gates.sh device       # 只跑 androidTest（需要设备 + autotest.enabled=true）
#
# ⚠️ 首次使用需实测校准：各 gradle 任务名与模块路径以本仓/debox 实际为准，
#    跑通后把真实耗时补进下方 EXPECTED_MIN，供 §5.7 分层回归估算。

set -uo pipefail

AUTOTEST_DIR="/Users/xiaochengcheng/StudioProjects/autotest"
DEBOX_DIR="/Users/xiaochengcheng/StudioProjects/debox-android"
SCOPE="${1:-all}"

TS="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${AUTOTEST_DIR}/build/gates/${TS}"
mkdir -p "$OUT_DIR"
SUMMARY="${OUT_DIR}/summary.tsv"
printf "gate\tstatus\texit\tseconds\tlog\n" > "$SUMMARY"

PASS=0; FAIL=0; SKIP=0

# run_gate <名称> <工作目录> <命令...>
run_gate() {
  local name="$1"; shift
  local wd="$1"; shift
  local log="${OUT_DIR}/${name}.log"

  if [[ ! -d "$wd" ]]; then
    printf "%s\tSKIP\t-\t0\t%s\n" "$name" "(工作目录不存在: $wd)" >> "$SUMMARY"
    echo "⏭  SKIP  $name — 工作目录不存在"
    SKIP=$((SKIP+1)); return
  fi

  echo "▶  RUN   $name"
  local t0; t0=$(date +%s)
  ( cd "$wd" && "$@" ) > "$log" 2>&1
  local rc=$?
  local dt=$(( $(date +%s) - t0 ))

  if [[ $rc -eq 0 ]]; then
    printf "%s\tPASS\t0\t%s\t%s\n" "$name" "$dt" "$log" >> "$SUMMARY"
    echo "✅ PASS  $name (${dt}s)"
    PASS=$((PASS+1))
  else
    printf "%s\tFAIL\t%s\t%s\t%s\n" "$name" "$rc" "$dt" "$log" >> "$SUMMARY"
    echo "❌ FAIL  $name (${dt}s, exit=$rc) → $log"
    FAIL=$((FAIL+1))
  fi
  # 不 early-exit：门禁之间互相独立，一个失败不阻塞其余（对齐 §5「只测不修、FAIL 不阻塞」）
}

# assert_tests_ran <门禁名> <test-results 目录>
# 反「假 PASS」守卫：gradle 退出 0 不等于测试真的执行了（UP-TO-DATE / 无匹配测试都会 exit 0）。
# 对齐 L-005：未执行只能记 NOT_RUN，绝不可记 PASS。
assert_tests_ran() {
  local name="$1" results_dir="$2"
  local n=0
  if [[ -d "$results_dir" ]]; then
    n=$(find "$results_dir" -name 'TEST-*.xml' -newermt '-10 minutes' 2>/dev/null | wc -l | tr -d ' ')
  fi
  if [[ "$n" -eq 0 ]]; then
    printf "%s-executed\tFAIL\t-\t0\t(无新鲜测试报告，疑似未真正执行)\n" "$name" >> "$SUMMARY"
    echo "❌ FAIL  ${name}-executed — 未产生新的测试报告，判定「未执行」而非通过"
    FAIL=$((FAIL+1))
  else
    printf "%s-executed\tPASS\t0\t0\t(%s 个测试报告)\n" "$name" "$n" >> "$SUMMARY"
    echo "✅ PASS  ${name}-executed — $n 个测试报告"
    PASS=$((PASS+1))
  fi
}

# ── 1. 框架自检（TC-P-001，无需设备）────────────────────────────
if [[ "$SCOPE" == "all" || "$SCOPE" == "framework" ]]; then
  run_gate "framework-compile"  "$AUTOTEST_DIR" ./gradlew :autotest:compileReleaseKotlin
  # --rerun-tasks 必须加：否则 gradle UP-TO-DATE 会跳过执行，脚本把「没跑」误报成 PASS
  # （2026-07-18 实测踩到：:autotest:test 1 秒 "PASS"，实为 34 tasks up-to-date）
  run_gate "framework-unittest" "$AUTOTEST_DIR" ./gradlew :autotest:test --rerun-tasks
  assert_tests_ran "framework-unittest" "${AUTOTEST_DIR}/autotest/build/test-results"
  run_gate "framework-publish"  "$AUTOTEST_DIR" ./gradlew :autotest:publishToMavenLocal
fi

# ── 2. debox JVM 单测（TC-F-001/004/005 的主机侧判据）──────────
# NODE_PATH 见 TEST_GUIDE §7.7 / debox L-BUILD-01：手动集成 RN 需显式指定
if [[ "$SCOPE" == "all" || "$SCOPE" == "jvm" ]]; then
  export NODE_PATH="${DEBOX_DIR}/ReactNative/node_modules"
  run_gate "debox-basemodule-unittest" "$DEBOX_DIR" ./gradlew :business:BaseModule:testDebugUnitTest
fi

# ── 3. 设备侧 androidTest（需 autotest.enabled=true + 设备在线）─
if [[ "$SCOPE" == "all" || "$SCOPE" == "device" ]]; then
  DEVICES=$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | wc -l | tr -d ' ')
  if [[ "$DEVICES" -eq 0 ]]; then
    printf "device-androidtest\tSKIP\t-\t0\t(无在线设备)\n" >> "$SUMMARY"
    echo "⏭  SKIP  device-androidtest — 无在线设备"
    SKIP=$((SKIP+1))
  elif ! grep -qE '^autotest\.enabled\s*=\s*true' "${DEBOX_DIR}/local.properties" 2>/dev/null; then
    printf "device-androidtest\tSKIP\t-\t0\t(autotest.enabled 未开启)\n" >> "$SUMMARY"
    echo "⏭  SKIP  device-androidtest — local.properties 未设 autotest.enabled=true"
    SKIP=$((SKIP+1))
  else
    export NODE_PATH="${DEBOX_DIR}/ReactNative/node_modules"
    run_gate "device-androidtest" "$DEBOX_DIR" ./gradlew :app:connectedAppDebugAndroidTest
  fi
fi

# ── 汇总 ────────────────────────────────────────────────────────
echo
echo "════════ 门禁汇总 ════════"
column -t -s$'\t' "$SUMMARY"
echo "─────────────────────────"
echo "PASS $PASS / FAIL $FAIL / SKIP $SKIP"
echo "证据目录: $OUT_DIR"
echo
echo "→ 把 summary.tsv 的结论引用进 runs/日期-功能/results.md 对应用例行"

[[ $FAIL -eq 0 ]] && exit 0 || exit 1
