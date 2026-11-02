const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1200, height: 2000 }, deviceScaleFactor: 1 });
  await p.goto('file://' + __dirname + '/shots.html', { waitUntil: 'networkidle' });
  await p.evaluate(() => document.fonts.ready);
  for (let i = 1; i <= 5; i++) await (await p.$('#s' + i)).screenshot({ path: `../0${i}.png` });
  await b.close();
})();
