// Сквозные проверки Cotes. Запуск: python3 -m http.server 8765 & NODE_PATH=$(npm root -g) node replica/e2e.js
const { chromium } = require('playwright');
const B = process.env.COTES_URL || "http://localhost:8765", OUT = process.env.OUT || "replica/clone-screens";
const OLD = [
  { title: 'Список покупок', content: 'Молоко\nХлеб\nКофе' },
  { title: '<b>HTML</b> заголовок', content: '<img src=x onerror="window.__xss=1">' },
  { title: 'Идеи', content: 'Длинный текст про кофе '.repeat(30) }];
let fails = 0;
const ok = (c, m) => { console.log((c ? 'PASS ' : 'FAIL ') + m); if (!c) fails++; };
(async () => {
  const b = await chromium.launch();
  const ctx = await b.newContext({ viewport: { width: 1280, height: 800 }, acceptDownloads: true });
  const p = await ctx.newPage();
  const errs = [];
  p.on('pageerror', e => errs.push(e.message));
  p.on('console', m => { if (m.type() === 'error') errs.push(m.text()); });
  p.on('request', r => { if (!r.url().startsWith(B)) errs.push('external ' + r.url()); });
  await p.goto(B + '/index.html');
  await p.evaluate(o => localStorage.setItem('notes', JSON.stringify(o)), OLD);
  await p.goto(B + '/index.html');
  ok((await p.textContent('#local-count')).includes('3 заметки'), 'landing shows local count');
  await p.screenshot({ path: OUT + '/S01-desk.png', fullPage: true });

  await p.goto(B + '/notes-and-edit.html');
  await p.waitForTimeout(300);
  const stored = await p.evaluate(() => JSON.parse(localStorage.getItem('notes')));
  ok(stored.length === 3 && stored.every(n => n.id && n.updated && n.title !== undefined), 'old notes migrated with ids');
  ok(await p.evaluate(() => !window.__xss) && (await p.locator('.note-list img').count()) === 0, 'no HTML injection');
  ok((await p.inputValue('#title')) === 'Идеи', 'desktop opens latest note');
  await p.screenshot({ path: OUT + '/S02-desk.png' });

  // search then open the right one (B1)
  await p.keyboard.press('Control+k');
  await p.keyboard.type('покуп');
  ok((await p.locator('.note-item').count()) === 1, 'search filters');
  ok((await p.locator('.note-item mark').count()) >= 1, 'search highlights');
  await p.screenshot({ path: OUT + '/S02-search.png' });
  await p.locator('.note-item').first().click();
  ok((await p.inputValue('#title')) === 'Список покупок', 'filtered click opens the right note');
  await p.keyboard.press('Control+k'); await p.keyboard.type('zzzz');
  ok(await p.isVisible('#empty-search'), 'no results state');
  await p.keyboard.press('Escape');
  ok((await p.locator('.note-item').count()) === 3, 'Esc clears search');

  // new note + autosave
  await p.keyboard.press('Alt+n');
  await p.keyboard.type('Новая мысль');
  await p.keyboard.press('Enter');
  await p.keyboard.type('Текст новой заметки');
  await p.waitForTimeout(700);
  let s = await p.evaluate(() => JSON.parse(localStorage.getItem('notes')));
  ok(s.some(n => n.title === 'Новая мысль' && n.content === 'Текст новой заметки'), 'autosave without button');
  ok((await p.textContent('#save-status')) === 'Сохранено', 'status says saved');
  ok((await p.textContent('#meta-count')).startsWith('3 слова'), 'word count');
  ok((await p.locator('.note-item').first().textContent()).includes('Новая мысль'), 'sorted by last edit');

  // empty draft is discarded
  await p.keyboard.press('Alt+n');
  await p.locator('.note-item').nth(1).click();
  s = await p.evaluate(() => JSON.parse(localStorage.getItem('notes')));
  ok(s.length === 4 && (await p.locator('.note-item').count()) === 4, 'empty draft not saved');

  // pin
  await p.locator('.note-item', { hasText: 'Идеи' }).click();
  await p.click('#pin');
  ok((await p.locator('.note-item').first().textContent()).includes('Идеи'), 'pinned goes on top');

  // delete + undo
  await p.click('#delete');
  ok((await p.locator('.note-item').count()) === 3, 'deleted');
  await p.screenshot({ path: OUT + '/S02-toast.png' });
  await p.click('#toast-action');
  ok((await p.locator('.note-item').count()) === 4 && (await p.inputValue('#title')) === 'Идеи', 'undo restores');

  // export
  await p.click('#menu-button');
  const [dl] = await Promise.all([p.waitForEvent('download'), p.click('[data-action="export-json"]')]);
  const fs = require('fs'); const exp = JSON.parse(fs.readFileSync(await dl.path(), 'utf8'));
  ok(exp.notes.length === 4, 'export json has all notes');
  // import same file -> nothing duplicated
  await p.setInputFiles('#import-file', await dl.path());
  await p.waitForTimeout(300);
  ok((await p.locator('.note-item').count()) === 4, 'import of same copy adds nothing');

  // other tab sync
  const p2 = await ctx.newPage();
  await p2.goto(B + '/notes-and-edit.html#new');
  await p2.keyboard.type('Из другой вкладки');
  await p2.waitForTimeout(700);
  await p.waitForTimeout(300);
  ok((await p.locator('.note-item', { hasText: 'Из другой вкладки' }).count()) === 1, 'tab sync');
  await p2.close();

  // legacy links
  await p.goto(B + '/editor.html?index=0');
  await p.waitForTimeout(300);
  ok((await p.inputValue('#title')) === 'Список покупок', 'editor.html?index=0 opens note');
  await p.goto(B + '/editor.html?index=99');
  await p.waitForTimeout(300);
  ok(await p.isVisible('#toast'), 'bad index -> toast, no crash');
  await p.goto(B + '/editor.html');
  await p.waitForTimeout(300);
  ok(await p.isVisible('#editor') && (await p.inputValue('#title')) === '', 'editor.html -> new note');

  // dark theme screenshots
  await p.goto(B + '/notes-and-edit.html');
  await p.click('[data-theme-toggle]'); await p.click('[data-theme-toggle]');
  ok(await p.evaluate(() => document.documentElement.dataset.theme === 'dark'), 'theme toggles to dark');
  await p.screenshot({ path: OUT + '/S02-dark.png' });
  await p.goto(B + '/index.html'); await p.screenshot({ path: OUT + '/S01-dark.png', fullPage: true });
  await p.evaluate(() => localStorage.removeItem('cotes:theme'));
  ok(errs.length === 0, 'no console errors / external requests: ' + errs.join(' | '));

  // mobile
  const m = await (await b.newContext({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true })).newPage();
  m.on('pageerror', e => ok(false, 'mobile error ' + e.message));
  await m.goto(B + '/index.html');
  await m.screenshot({ path: OUT + '/S01-mob.png', fullPage: true });
  await m.evaluate(o => localStorage.setItem('notes', JSON.stringify(o)), OLD);
  await m.goto(B + '/notes-and-edit.html');
  ok(await m.isVisible('#note-list') && !(await m.isVisible('#editor')), 'mobile starts on list');
  await m.screenshot({ path: OUT + '/S02-mob-list.png' });
  await m.locator('.note-item').first().tap();
  ok(await m.isVisible('#editor') && !(await m.isVisible('#note-list')), 'mobile opens editor pane');
  await m.screenshot({ path: OUT + '/S02-mob-editor.png' });
  await m.tap('#back');
  await m.waitForTimeout(200);
  ok(await m.isVisible('#note-list'), 'mobile back to list');
  await m.evaluate(() => localStorage.clear()); await m.reload();
  ok(await m.isVisible('#empty-none'), 'empty state when no notes');
  await m.screenshot({ path: OUT + '/S02-mob-empty.png' });
  await b.close();
  console.log(fails ? fails + ' FAILED' : 'ALL PASSED');
  process.exit(fails ? 1 : 0);
})();
