// Automated end-to-end browser tests for docs/visualizer.html using Playwright.
// Tests DOM rendering, user interactions, keyboard navigation, state snapshot display,
// and console message integrity.
//
// Run: node docs/visualizer-e2e.mjs

import { createServer } from 'node:http';
import { readFileSync, existsSync } from 'node:fs';
import { join, extname, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const projectRoot = join(__dirname, '..');

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.ico': 'image/x-icon'
};

// Start a lightweight static server for test files
function createStaticServer() {
  return createServer((req, res) => {
    try {
      const urlPath = new URL(req.url, 'http://localhost').pathname;
      let filePath;

      if (urlPath === '/' || urlPath === '/visualizer.html') {
        filePath = join(__dirname, 'visualizer.html');
      } else if (urlPath.startsWith('/docs/')) {
        filePath = join(__dirname, urlPath.slice(6));
      } else if (urlPath.startsWith('/examples/')) {
        filePath = join(projectRoot, urlPath.slice(1));
      } else if (urlPath.startsWith('/src/')) {
        filePath = join(projectRoot, urlPath.slice(1));
      } else {
        filePath = join(__dirname, urlPath.slice(1));
      }

      if (existsSync(filePath)) {
        const ext = extname(filePath);
        const mime = MIME_TYPES[ext] || 'application/octet-stream';
        const data = readFileSync(filePath);
        res.writeHead(200, { 'Content-Type': mime, 'Access-Control-Allow-Origin': '*' });
        res.end(data);
      } else {
        res.writeHead(404, { 'Content-Type': 'text/plain' });
        res.end(`File not found: ${urlPath}`);
      }
    } catch (err) {
      res.writeHead(500, { 'Content-Type': 'text/plain' });
      res.end(`Internal server error: ${err.message}`);
    }
  });
}

async function runTests() {
  const server = createStaticServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  const baseUrl = `http://127.0.0.1:${port}/visualizer.html`;
  console.log(`Test server running at ${baseUrl}`);

  let passed = 0;
  let failed = 0;

  const assert = (condition, msg) => {
    if (condition) {
      console.log(`  PASS: ${msg}`);
      passed++;
    } else {
      console.error(`  FAIL: ${msg}`);
      failed++;
    }
  };

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  const consoleErrors = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      consoleErrors.push(msg.text());
    }
  });
  page.on('pageerror', (err) => {
    consoleErrors.push(err.message);
  });

  try {
    // -------------------------------------------------------------
    // Test 1: Page Load and Initial Clean State
    // -------------------------------------------------------------
    console.log('\nTest 1: Initial page state');
    await page.goto(baseUrl);
    const title = await page.title();
    assert(title.includes('interleave'), 'Page title contains "interleave"');

    const badgeText = await page.locator('#verdictBadge').textContent();
    assert(badgeText === '—', 'Verdict badge initialized with "—"');

    // -------------------------------------------------------------
    // Test 2: Load lost-update.json via paste & verify snapshot strip
    // -------------------------------------------------------------
    console.log('\nTest 2: Load lost-update.json with snapshot states');
    const lostUpdateJson = readFileSync(join(projectRoot, 'examples/traces/lost-update.json'), 'utf8');
    await page.locator('#paste').fill(lostUpdateJson);
    await page.locator('#loadPaste').click();

    const verdictLostUpdate = await page.locator('#verdictBadge').textContent();
    assert(verdictLostUpdate === 'VIOLATION', 'lost-update renders VIOLATION verdict');

    const stepCount = await page.locator('#stepCount').textContent();
    assert(stepCount === '4', 'Step count is 4');

    const timelineRows = page.locator('#timeline tbody tr');
    const rowCount = await timelineRows.count();
    assert(rowCount === 5, 'Timeline renders 5 configurations (0 to 4)');

    // Row 4 should be active by default (selectedIdx = trace length)
    const activeRow4 = timelineRows.nth(4);
    assert(await activeRow4.evaluate((el) => el.classList.contains('active-row')), 'Final row 4 is active by default');
    assert(await activeRow4.getAttribute('aria-pressed') === 'true', 'Row 4 has aria-pressed="true"');

    // State strip for config 4 should show counter = 1
    const config4Label = await page.locator('#selectedIdxLabel').textContent();
    assert(config4Label.includes('config 4'), 'State strip header shows config 4');

    const counterRow4 = page.locator('#stateStrip table tbody tr').filter({ hasText: 'counter' });
    const counterVal4 = await counterRow4.locator('td').nth(2).textContent();
    assert(counterVal4?.trim() === '1', 'State strip at config 4 displays counter = 1');

    // -------------------------------------------------------------
    // Test 3: Interactive Row Click & State Diff Highlighting
    // -------------------------------------------------------------
    console.log('\nTest 3: Interactive timeline selection and diff highlight');
    // Click config 3 (where counter became 1)
    await timelineRows.nth(3).click();
    assert(await timelineRows.nth(3).evaluate((el) => el.classList.contains('active-row')), 'Row 3 becomes active on click');
    assert(await timelineRows.nth(4).evaluate((el) => !el.classList.contains('active-row')), 'Row 4 is no longer active');

    const config3Label = await page.locator('#selectedIdxLabel').textContent();
    assert(config3Label.includes('config 3'), 'State strip header updates to config 3');

    // In config 3, counter changed from 0 to 1, so counter value cell must have .val-changed
    const counterRow3 = page.locator('#stateStrip table tbody tr').filter({ hasText: 'counter' });
    const isCounterChanged3 = await counterRow3.locator('td').nth(2).locator('.val-changed').count() > 0;
    assert(isCounterChanged3, 'State strip at config 3 highlights counter value cell with .val-changed');

    // Click config 4: no values changed from config 3, so .val-changed must be absent
    await timelineRows.nth(4).click();
    const changedCellCount4 = await page.locator('#stateStrip .val-changed').count();
    assert(changedCellCount4 === 0, 'State strip at config 4 has 0 .val-changed cells when no values changed');

    // Click config 0 (initial state: counter = 0)
    await timelineRows.nth(0).click();
    assert(await timelineRows.nth(0).evaluate((el) => el.classList.contains('active-row')), 'Row 0 becomes active on click');
    const counterRow0 = page.locator('#stateStrip table tbody tr').filter({ hasText: 'counter' });
    const counterVal0 = await counterRow0.locator('td').nth(2).textContent();
    assert(counterVal0?.trim() === '0', 'State strip at config 0 displays counter = 0');

    // -------------------------------------------------------------
    // Test 4: Keyboard Navigation (Enter and Space)
    // -------------------------------------------------------------
    console.log('\nTest 4: Keyboard navigation');
    const row2 = timelineRows.nth(2);
    await row2.focus();
    await page.keyboard.press('Enter');
    assert(await row2.evaluate((el) => el.classList.contains('active-row')), 'Pressing Enter activates row 2');
    const config2Label = await page.locator('#selectedIdxLabel').textContent();
    assert(config2Label.includes('config 2'), 'State strip updates to config 2 via Enter');

    const row1 = timelineRows.nth(1);
    await row1.focus();
    await page.keyboard.press('Space');
    assert(await row1.evaluate((el) => el.classList.contains('active-row')), 'Pressing Space activates row 1');
    const config1Label = await page.locator('#selectedIdxLabel').textContent();
    assert(config1Label.includes('config 1'), 'State strip updates to config 1 via Space');

    // -------------------------------------------------------------
    // Test 5: Load peterson.json (PASS verdict)
    // -------------------------------------------------------------
    console.log('\nTest 5: Load peterson.json fixture');
    const petersonJson = readFileSync(join(projectRoot, 'examples/traces/peterson.json'), 'utf8');
    await page.locator('#clearBtn').click();
    await page.locator('#paste').fill(petersonJson);
    await page.locator('#loadPaste').click();

    const verdictPeterson = await page.locator('#verdictBadge').textContent();
    assert(verdictPeterson === 'PASS', 'peterson.json renders PASS verdict');

    // -------------------------------------------------------------
    // Test 6: Load incomplete-only.json (INCOMPLETE verdict)
    // -------------------------------------------------------------
    console.log('\nTest 6: Load incomplete-only.json fixture');
    const incompleteJson = readFileSync(join(projectRoot, 'examples/traces/incomplete-only.json'), 'utf8');
    await page.locator('#clearBtn').click();
    await page.locator('#paste').fill(incompleteJson);
    await page.locator('#loadPaste').click();

    const verdictIncomplete = await page.locator('#verdictBadge').textContent();
    assert(verdictIncomplete === 'INCOMPLETE', 'incomplete-only renders INCOMPLETE verdict');

    // -------------------------------------------------------------
    // Test 7: Program File Load alongside Trace
    // -------------------------------------------------------------
    console.log('\nTest 7: Load program file and verify invariant');
    await page.locator('#clearBtn').click();
    await page.locator('#paste').fill(lostUpdateJson);
    await page.locator('#loadPaste').click();

    // Set program file input
    const programPath = join(projectRoot, 'src/main/resources/programs/lost-update.json');
    await page.locator('#programFile').setInputFiles(programPath);

    await page.waitForFunction(() => document.querySelector('#stateStrip')?.textContent.includes('Invariant:'), { timeout: 3000 });
    const stripText = await page.locator('#stateStrip').textContent();
    assert(stripText.includes('Invariant:'), 'State strip renders Invariant header when program is loaded');

    // -------------------------------------------------------------
    // Fresh Java -> report -> normalization -> browser contract, enabled in CI.
    if(process.env.SOURCE_TRACE_JSON){
      const sourcePath = join(projectRoot, process.env.SOURCE_TRACE_JSON);
      const sourceJson = readFileSync(sourcePath, 'utf8');
      const raw = JSON.parse(sourceJson);
      const sourceTrace = raw.benchmarks[0].incompleteTrace;
      assert(sourceTrace.executedSteps.map(s=>s.instructionIndex).join(',')==='0,2,3', 'Java emitted actual skipped-branch PCs');
      await page.locator('#clearBtn').click();
      await page.locator('#paste').fill(sourceJson);
      await page.locator('#loadPaste').click();
      await page.waitForFunction(()=>document.querySelector('#meta')?.textContent.includes('LOOP_LIMIT'));
      const sourceRows = page.locator('#timeline tbody tr');
      assert(await sourceRows.count()===4, 'Cutoff does not add an uncommitted action row');
      for(let i=0; i<sourceTrace.executedSteps.length; i++){
        const pill = sourceRows.nth(i).locator('.cell-active');
        const step = sourceTrace.executedSteps[i];
        assert((await pill.textContent())===step.label && (await pill.getAttribute('title')).includes(`${step.source.endLine}:${step.source.endColumn}`), `Source action row ${i} has its own label and span`);
      }
      assert((await sourceRows.nth(0).textContent()).includes('if (false)'), 'First source action shows its decision');
      assert((await page.locator('#meta').textContent()).includes('<source>:'), 'Cutoff source location is visible');
      assert((await page.locator('#meta').textContent()).includes(sourceTrace.sourceDigest), 'Exact source digest is visible');
      const counterRow = page.locator('#stateStrip tbody tr').filter({has:page.locator('td:nth-child(2) code', {hasText:/^counter$/})});
      assert((await counterRow.locator('td').nth(2).textContent()).trim()==='2', 'State strip shows the final committed counter');
      const unsafe = structuredClone(raw);
      unsafe.benchmarks[0].incompleteTrace.executedSteps[1].label = '<img src=x onerror=alert(1)>';
      await page.locator('#paste').fill(JSON.stringify(unsafe));
      await page.locator('#loadPaste').click();
      assert((await page.locator('#timeline tbody tr').nth(1).locator('.cell-active').textContent())===unsafe.benchmarks[0].incompleteTrace.executedSteps[1].label, 'Unsafe source label loaded as literal text');
      assert(await page.locator('#timeline img').count()===0, 'Source labels are rendered as text');
    }

    // Fresh private-assertion report must retain rollback and the distinct failure kind.
    if(process.env.SOURCE_ASSERTION_JSON){
      const assertionJson = readFileSync(join(projectRoot, process.env.SOURCE_ASSERTION_JSON), 'utf8');
      const assertion = JSON.parse(assertionJson).benchmarks[0].failingTrace;
      assert(assertion.failure.kind==='ASSERTION' && assertion.failure.phase==='STEP', 'Java emitted false statement evidence');
      await page.locator('#clearBtn').click();
      await page.locator('#paste').fill(assertionJson);
      await page.locator('#loadPaste').click();
      await page.waitForFunction(()=>document.querySelector('#meta')?.textContent.includes('ASSERTION'));
      const meta = await page.locator('#meta').textContent();
      assert(meta.includes('ASSERTION') && meta.includes('STEP') && (await page.locator('#verdictBadge').textContent()).includes('VIOLATION'), 'Assertion failure, verdict and observation phase are visible');
      assert(meta.includes(`${assertion.failure.source.line}:${assertion.failure.source.column}`), 'Actual inner assertion location is visible');
      assert(await page.locator('#timeline tbody tr').count()===2, 'False private assertion retains one outer attempted action');
      const x = page.locator('#stateStrip tbody tr').filter({has:page.locator('td:nth-child(2) code', {hasText:/^x$/})});
      assert((await x.locator('td').nth(2).textContent()).trim()==='0', 'Assertion rollback retains committed x=0');
    }

    // Actual CLI output remains a direct incomplete trace even when its prefix is empty.
    if(process.env.SOURCE_SIMPLIFIED_CUTOFF_JSON){
      const simplifiedJson = readFileSync(join(projectRoot, process.env.SOURCE_SIMPLIFIED_CUTOFF_JSON), 'utf8');
      const simplified = JSON.parse(simplifiedJson);
      assert(simplified.outcome==='INCOMPLETE' && simplified.cutoff.reason==='LOOP_LIMIT', 'CLI simplification retains its incomplete loop witness');
      await page.locator('#clearBtn').click();
      await page.locator('#paste').fill(simplifiedJson);
      await page.locator('#loadPaste').click();
      await page.waitForFunction(()=>document.querySelector('#meta')?.textContent.includes('LOOP_LIMIT'));
      assert((await page.locator('#verdictBadge').textContent()).includes('INCOMPLETE'), 'Simplified cutoff is never displayed as PASS');
      assert(await page.locator('#timeline tbody tr').count()===1, 'Empty simplified prefix has one configuration and no attempted row');
      const meta = await page.locator('#meta').textContent();
      assert(meta.includes(`${simplified.cutoff.source.file}:${simplified.cutoff.source.line}:${simplified.cutoff.source.column}`), 'Simplified witness retains the actual omitted source location');
      const noise = page.locator('#stateStrip tbody tr').filter({has:page.locator('td:nth-child(2) code', {hasText:/^noise$/})});
      assert((await noise.locator('td').nth(2).textContent()).trim()==='0', 'Simplified root snapshot was regenerated rather than copied');
    }

    // Actual opt-in CLI reports label quotient counts without changing strategy/store identity.
    if(process.env.SOURCE_SYMMETRY_JSON){
      const symmetryJson = readFileSync(join(projectRoot, process.env.SOURCE_SYMMETRY_JSON), 'utf8');
      const symmetry = JSON.parse(symmetryJson).benchmarks[0];
      assert(symmetry.symmetryReduction===true && symmetry.statesExplored===5, 'Actual symmetry CLI artifact contains five representative visits');
      await page.locator('#clearBtn').click();
      await page.locator('#paste').fill(symmetryJson);
      await page.locator('#loadPaste').click();
      await page.waitForFunction(()=>document.querySelector('#meta')?.textContent.includes('Thread symmetry'));
      const meta = await page.locator('#meta').textContent();
      assert(meta.includes('representative visits'), 'Viewer explicitly identifies quotient counts');
      assert(meta.includes('DFS') && meta.includes('EXACT'), 'Actual strategy and exact-store identities remain visible');
      assert((await page.locator('#verdictBadge').textContent()).includes('PASS'), 'Symmetry report retains the actual PASS verdict');
    }

    if(process.env.SOURCE_SHORTEST_CUTOFF_PROVEN_JSON && process.env.SOURCE_SHORTEST_CUTOFF_PARTIAL_JSON){
      for(const [path, proven, length, work] of [
        [process.env.SOURCE_SHORTEST_CUTOFF_PROVEN_JSON, true, 0, '4 / 4'],
        [process.env.SOURCE_SHORTEST_CUTOFF_PARTIAL_JSON, false, 3, '1 / 1']
      ]){
        const payload = readFileSync(join(projectRoot, path), 'utf8');
        const raw = JSON.parse(payload);
        assert(raw.cutoffSearch.shortestProven===proven, 'Fresh Java proof metadata matches fixture');
        await page.locator('#clearBtn').click();
        await page.locator('#paste').fill(payload);
        await page.locator('#loadPaste').click();
        const expected = proven ? 'Shortest among original subsequences' : 'Best found; shortest not proven';
        await page.waitForFunction(text=>document.querySelector('#meta')?.textContent.includes(text), expected);
        const meta = await page.locator('#meta').textContent();
        assert(meta.includes(expected) && meta.includes(work), 'Viewer shows actual proof state and candidate usage');
        assert(meta.includes('no global shortest guarantee'), 'Viewer limits the proof to original subsequences');
        assert((await page.locator('#verdictBadge').textContent())==='INCOMPLETE', 'Shortest search never changes INCOMPLETE to PASS');
        assert(await page.locator('#timeline tbody tr').count()===length+1, 'Fresh shortened snapshots align with executed choices');
        assert(meta.includes('LOOP_LIMIT') && meta.includes(raw.sourceDigest), 'Cutoff and current source identity remain visible');
      }
    }

    // Test 8: Integrity & Console Sanity
    // -------------------------------------------------------------
    console.log('\nTest 8: Console and error integrity');
    assert(consoleErrors.length === 0, `Zero console errors or exceptions (found ${consoleErrors.length}: ${consoleErrors.join(', ')})`);

  } finally {
    await browser.close();
    await new Promise((resolve) => server.close(resolve));
  }

  console.log(`\nResults: ${passed} passed, ${failed} failed`);
  if (failed > 0) {
    process.exit(1);
  }
}

runTests().catch((err) => {
  console.error('Fatal test error:', err);
  process.exit(1);
});
