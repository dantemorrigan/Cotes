const { chromium } = require('playwright');
const fs = require('fs');
const [svgPath, outDir] = process.argv.slice(2);
const svg = fs.readFileSync(svgPath, 'utf8').replace(/<\?xml[^>]*>|<!DOCTYPE[^>]*>/g, '');
let cat;
const font = "font-family: 'Roboto', 'Noto Sans', 'DejaVu Sans', sans-serif;";
const icon = () => `<html><body style="margin:0"><div style="width:512px;height:512px;background:#fff;display:flex;align-items:center;justify-content:center">
  <img src="${cat}" style="width:300px;height:340px;object-fit:contain"></div></body></html>`;
const banner = (title, tagline) => `<html><body style="margin:0;${font}">
<div style="width:1024px;height:500px;background:linear-gradient(135deg,#6A3FB5 0%,#8C63D6 55%,#C7A6FF 100%);display:flex;align-items:center;gap:56px;padding:0 88px;box-sizing:border-box">
  <div style="width:220px;height:220px;flex:none;border-radius:64px;background:#fff;box-shadow:0 18px 40px rgba(40,10,90,.35);display:flex;align-items:center;justify-content:center">
    <img src="${cat}" style="width:150px;height:150px;object-fit:contain"></div>
  <div style="color:#fff">
    <div style="font-size:92px;font-weight:700;letter-spacing:-1px;line-height:1">${title}</div>
    <div style="font-size:36px;margin-top:22px;line-height:1.25;opacity:.95;max-width:560px">${tagline}</div>
  </div></div></body></html>`;
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  // Обрезаем viewBox по контуру кота, чтобы он стоял ровно по центру.
  await p.setContent(`<html><body>${svg}</body></html>`);
  const tight = await p.evaluate(() => {
    const el = document.querySelector('svg');
    const vb = el.viewBox.baseVal;
    el.setAttribute('width', vb.width * 10); el.setAttribute('height', vb.height * 10);
    const box = el.getBoundingClientRect();
    const r = el.querySelector('g').getBoundingClientRect();
    const k = vb.width / box.width;
    const x = vb.x + (r.left - box.left) * k, y = vb.y + (r.top - box.top) * k;
    el.setAttribute('viewBox', `${x} ${y} ${r.width * k} ${r.height * k}`);
    el.removeAttribute('width'); el.removeAttribute('height');
    return el.outerHTML;
  });
  cat = 'data:image/svg+xml;base64,' + Buffer.from(tight).toString('base64');
  const shot = async (html, w, h, file) => {
    await p.setViewportSize({ width: w, height: h });
    await p.setContent(html); await p.waitForTimeout(150);
    await p.screenshot({ path: file, clip: { x: 0, y: 0, width: w, height: h } });
  };
  await shot(icon(), 512, 512, `${outDir}/icon.png`);
  await shot(banner('Cotes', 'Private notes that stay on your phone'), 1024, 500, `${outDir}/feature-en.png`);
  await shot(banner('Cotes', 'Заметки, которые остаются на вашем телефоне'), 1024, 500, `${outDir}/feature-ru.png`);
  await b.close();
})();
