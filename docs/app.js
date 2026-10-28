function switchTab(tabId) {
  // Update buttons
  const buttons = document.querySelectorAll('.tab-btn');
  buttons.forEach(btn => btn.classList.remove('active'));
  event.currentTarget.classList.add('active');

  // Update panels
  const panels = document.querySelectorAll('.code-panel');
  panels.forEach(p => p.classList.remove('active'));
  const target = document.getElementById('tab-' + tabId);
  if (target) {
    target.classList.add('active');
  }
}

function switchInstall(instId) {
  const tabs = document.querySelectorAll('.inst-tab');
  tabs.forEach(t => t.classList.remove('active'));
  event.currentTarget.classList.add('active');

  const panels = document.querySelectorAll('.inst-panel');
  panels.forEach(p => p.classList.remove('active'));
  const target = document.getElementById('inst-' + instId);
  if (target) {
    target.classList.add('active');
  }
}

function copyCliCommand(el) {
  const cmd = document.getElementById('cli-cmd').innerText;
  navigator.clipboard.writeText(cmd).then(() => {
    const btn = el.querySelector('.copy-btn');
    const originalSvg = btn.innerHTML;
    btn.innerHTML = `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#34d399" stroke-width="2"><polyline points="20 6 9 17 4 12"></polyline></svg>`;
    setTimeout(() => {
      btn.innerHTML = originalSvg;
    }, 2000);
  });
}
