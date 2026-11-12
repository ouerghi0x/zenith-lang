const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
  });
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  await page.goto('http://localhost:3000');
  await page.waitForTimeout(1000);
  await page.click('#toggleEditorBtn');
  await page.waitForTimeout(500);
  await page.click('#tabBtnWorkflows');
  await page.waitForTimeout(1000);
  await page.click('#btnAutoWireAst');
  await page.waitForTimeout(1000);

  // Zoom to 65% like user screenshot!
  await page.click('.wf-zoom-btn:nth-child(1)');
  await page.waitForTimeout(200);
  await page.click('.wf-zoom-btn:nth-child(1)');
  await page.waitForTimeout(200);
  await page.click('.wf-zoom-btn:nth-child(1)');
  await page.waitForTimeout(200);
  await page.click('.wf-zoom-btn:nth-child(1)');
  await page.waitForTimeout(200);

  const res = await page.evaluate(() => {
    const surface = document.getElementById('wfCanvasSurface');
    const surfaceRect = surface.getBoundingClientRect();
    const dots = Array.from(document.querySelectorAll('.wf-port-dot'));
    const paths = Array.from(document.querySelectorAll('.wf-wire'));

    const orderDot = dots.find(d => d.getAttribute('data-port-name')?.includes('orderId') && d.classList.contains('output'));
    const itemDot = dots.find(d => d.getAttribute('data-port-name')?.includes('orderId') && d.classList.contains('input'));

    const oRect = orderDot.getBoundingClientRect();
    const iRect = itemDot.getBoundingClientRect();

    const match = surface.style.transform.match(/scale\(([^)]+)\)/);
    const scale = match ? parseFloat(match[1]) : 1;

    const exactOrderDotCanvas = {
      x: (oRect.left + oRect.width / 2 - surfaceRect.left) / scale,
      y: (oRect.top + oRect.height / 2 - surfaceRect.top) / scale
    };

    const exactItemDotCanvas = {
      x: (iRect.left + iRect.width / 2 - surfaceRect.left) / scale,
      y: (iRect.top + iRect.height / 2 - surfaceRect.top) / scale
    };

    const wireD = paths.find(p => p.getAttribute('data-wire-id') === 'w_order_item')?.getAttribute('d');

    return {
      zoomLabel: document.getElementById('wfZoomLabel').textContent,
      surfaceTransform: surface.style.transform,
      scale,
      wireD,
      exactOrderDotCanvas,
      exactItemDotCanvas
    };
  });

  console.log('Scaled result:', JSON.stringify(res, null, 2));
  await browser.close();
})();
