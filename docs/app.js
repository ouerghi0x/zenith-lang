// Zenith Documentation Interactive Logic

document.addEventListener('DOMContentLoaded', () => {
  // 1. Copy code button logic
  document.querySelectorAll('.code-copy-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const codeBlock = btn.closest('.code-block-wrap').querySelector('pre.code-content code');
      if (!codeBlock) return;
      const text = codeBlock.innerText;
      navigator.clipboard.writeText(text).then(() => {
        const originalText = btn.innerHTML;
        btn.innerHTML = `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#34d399" stroke-width="2"><polyline points="20 6 9 17 4 12"></polyline></svg> Copied`;
        setTimeout(() => {
          btn.innerHTML = originalText;
        }, 2000);
      });
    });
  });

  // 2. Search / Filter sidebar links
  const searchInput = document.getElementById('docsSearch');
  if (searchInput) {
    searchInput.addEventListener('input', (e) => {
      const query = e.target.value.toLowerCase().trim();
      const links = document.querySelectorAll('.sidebar-link');
      const categories = document.querySelectorAll('.sidebar-category');

      links.forEach(link => {
        const text = link.textContent.toLowerCase();
        if (text.includes(query)) {
          link.style.display = 'block';
        } else {
          link.style.display = 'none';
        }
      });

      // Hide category headers if all children are hidden
      categories.forEach(cat => {
        const visibleLinks = cat.querySelectorAll('.sidebar-link[style="display: block;"], .sidebar-link:not([style*="display"])');
        const anyVisible = Array.from(cat.querySelectorAll('.sidebar-link')).some(l => l.style.display !== 'none');
        cat.style.display = anyVisible ? 'block' : 'none';
      });
    });
  }

  // 3. ScrollSpy for Active Sidebar Links
  const sections = document.querySelectorAll('.section-heading');
  const navLinks = document.querySelectorAll('.sidebar-link');

  function updateActiveLink() {
    let currentId = '';
    const scrollPos = window.scrollY + 100;

    sections.forEach(sec => {
      if (sec.offsetTop <= scrollPos) {
        currentId = sec.getAttribute('id');
      }
    });

    if (currentId) {
      navLinks.forEach(link => {
        if (link.getAttribute('href') === '#' + currentId) {
          link.classList.add('active');
        } else {
          link.classList.remove('active');
        }
      });
    }
  }

  window.addEventListener('scroll', updateActiveLink);
  updateActiveLink();
});
