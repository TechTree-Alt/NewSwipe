const { chromium } = require('playwright');
const names = ['01-layouts', '02-split-keyboard', '03-key-actions', '04-autocorrect', '05-emoji-clipboard', '06-customize'];
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1200, height: 2000 }, deviceScaleFactor: 1 });
  await p.goto('file://' + __dirname + '/shots.html', { waitUntil: 'networkidle' });
  await p.evaluate(() => document.fonts.ready);
  for (let i = 0; i < names.length; i++) await (await p.$('#s' + (i + 1))).screenshot({ path: `${__dirname}/../${names[i]}.png` });
  await b.close();
})();
