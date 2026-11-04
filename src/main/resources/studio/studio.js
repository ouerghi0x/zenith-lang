// Zenith Studio Controller
let editor = null;
let viewerEditor = null;
let currentCompilation = null;
window.currentCompilation = null;
let isSpringRunning = false;
let logPollInterval = null;
let monacoMarkers = [];

// Redraw ERD arrows if ERD tab is active (cheap SVG path update without destroying DOM)
function redrawErdIfActive() {
  const erdTab = document.getElementById('tab-erd');
  if (!erdTab || !erdTab.classList.contains('active')) return;
  const comp = currentCompilation || window.currentCompilation;
  if (!comp || !comp.models || comp.models.length === 0) return;
  const canvas = document.getElementById('erdCanvas');
  const svg = document.getElementById('erdSvgLayer');
  if (canvas && svg) {
    drawRelationArrows(comp.models, canvas, svg);
  }
}
window.redrawErdIfActive = redrawErdIfActive;

// Default initial schema
const defaultSchema = `config {
    app: "ShopSphere"
    package: "com.shopsphere.backend"
    basePath: "/api/v1"
    port: 8080
    database: postgres
    auth: jwt
    swagger: true
    flyway: true
}

enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED
}

enum UserRole {
    ADMIN,
    CUSTOMER
}

model Category {
    id: Long @id @auto
    name: String(min = 2, max = 50) @unique
    description: String? @text
    createdAt: DateTime @autoNow

    relations {
        products: List<Product> @relation(oneToMany, mappedBy = "category")
    }
}

model Product {
    id: Long @id @auto
    sku: String(length = 32) @unique
    title: String(min = 3, max = 150)
    description: String? @text
    price: Decimal(precision = 12, scale = 2) @positive
    stock: Int @min(0) = 0
    createdAt: DateTime @autoNow
    updatedAt: DateTime @autoUpdate

    relations {
        category: Category @relation(manyToOne)
    }

    endpoints {
        crud {
            paginate: true
            filter: [title, price]
        }
    }
}

model Customer {
    id: UUID @id @auto
    email: String @email @unique
    password: String @password
    role: UserRole = CUSTOMER @role
    fullName: String(min = 2, max = 100)
    createdAt: DateTime @autoNow

    relations {
        orders: List<Order> @relation(oneToMany, mappedBy = "customer")
    }
}

model Order {
    id: UUID @id @auto
    orderNumber: String(length = 20) @unique
    totalAmount: Decimal(precision = 12, scale = 2) @positive
    status: OrderStatus = PENDING
    createdAt: DateTime @autoNow

    relations {
        customer: Customer @relation(manyToOne)
    }

    endpoints {
        crud {
            paginate: true
            filter: [status]
        }

        post "/{id}/pay" -> OrderResponse {
            description: "Pays for an order"
            guard: status == OrderStatus.PENDING, "Only pending orders can be paid"
            action: {
                status = OrderStatus.PAID
            }
        }
    }
}
`;

// Initialize Monaco Editor
require.config({ paths: { vs: 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.44.0/min/vs' } });

require(['vs/editor/editor.main'], function () {
  // Register Zenith Language
  monaco.languages.register({ id: 'zenith' });
  monaco.languages.setMonarchTokensProvider('zenith', {
    keywords: [
      'import', 'config', 'model', 'enum', 'dto', 'body', 'seed', 'relations', 'endpoints',
      'crud', 'guard', 'action', 'true', 'false', 'auth', 'public',
      'and', 'or', 'paginate', 'search', 'filter', 'sort', 'params', 'query', 'where', 'find',
      'create', 'update', 'notify', 'minio', 's3', 'storage', 'storageBucket', 'job', 'cron', 'fixedRate'
    ],
    httpKeywords: ['get', 'post', 'put', 'delete', 'patch'],
    typeKeywords: ['Long', 'String', 'Int', 'Decimal', 'DateTime', 'Date', 'Boolean', 'UUID', 'Text', 'List', 'Void'],
    operators: ['=', '==', '!=', '&&', '||', '!', '<', '>', '<=', '>=', '->'],
    tokenizer: {
      root: [
        [/@[a-zA-Z_]\w*/, 'annotation'],
        [/[a-zA-Z_]\w*/, {
          cases: {
            '@keywords': 'keyword',
            '@httpKeywords': 'httpMethod',
            '@typeKeywords': 'type',
            '@default': 'identifier'
          }
        }],
        [/"([^"\\]|\\.)*"/, 'string'],
        [/\d+(\.\d+)?/, 'number'],
        [/\/\/.*$/, 'comment'],
        [/[{}()\[\]]/, '@brackets'],
        [/[:=,]/, 'delimiter'],
      ]
    }
  });

  // Provide clickable links & tooltips for modular imports (e.g. import "./config.zen")
  monaco.languages.registerLinkProvider('zenith', {
    provideLinks: function (model, token) {
      const links = [];
      const lineCount = model.getLineCount();
      for (let i = 1; i <= lineCount; i++) {
        const line = model.getLineContent(i);
        const match = line.match(/(import\s+["'])([^"']+)((["']))/i);
        if (match) {
          const raw = match[2];
          const target = resolveTargetFileName(raw);
          if (target) {
            const startCol = match.index + match[1].length + 1;
            const endCol = startCol + raw.length;
            links.push({
              range: new monaco.Range(i, startCol, i, endCol),
              tooltip: `Double-click or Ctrl+Click to open tab: ${target}`
            });
          }
        }
      }
      return { links: links };
    }
  });

  // Minimalist Studio Theme
  monaco.editor.defineTheme('zenith-dark', {
    base: 'vs-dark',
    inherit: true,
    rules: [
      { token: 'keyword', foreground: 'e06c75', fontStyle: 'bold' },
      { token: 'httpMethod', foreground: '61afef', fontStyle: 'bold' },
      { token: 'type', foreground: 'e5c07b' },
      { token: 'annotation', foreground: 'd19a66' },
      { token: 'string', foreground: '98c379' },
      { token: 'number', foreground: 'd19a66' },
      { token: 'comment', foreground: '5c6370', fontStyle: 'italic' },
      { token: 'identifier', foreground: 'abb2bf' },
    ],
    colors: {
      'editor.background': '#09090b',
      'editor.foreground': '#abb2bf',
      'editor.lineHighlightBackground': '#18181b',
      'editorLineNumber.foreground': '#52525b',
      'editorLineNumber.activeForeground': '#a1a1aa',
      'editorCursor.foreground': '#f4f4f5',
      'editor.selectionBackground': '#27272a',
    }
  });

  // Mount editor
  editor = monaco.editor.create(document.getElementById('monacoEditorContainer'), {
    value: '',
    language: 'zenith',
    theme: 'zenith-dark',
    automaticLayout: true,
    fontSize: 14,
    fontFamily: 'JetBrains Mono',
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    lineNumbersMinChars: 3,
    padding: { top: 12, bottom: 12 },
  });

  // Double-click or Ctrl+Click navigation to switch tabs when clicking files or imports
  let lastFileSwitchTime = 0;

  function handleEditorFileNavigation(e) {
    if (!editor) return false;
    let pos = null;
    if (e && e.target && e.target.position) {
      pos = e.target.position;
    } else {
      pos = editor.getPosition();
    }
    if (!pos) return false;

    const model = editor.getModel();
    if (!model) return false;

    const lineContent = model.getLineContent(pos.lineNumber);
    if (!lineContent || !lineContent.trim()) return false;

    let targetFile = null;

    // 1. Check if the line contains an import statement: e.g. import "./config.zen"
    const importMatch = lineContent.match(/import\s+["']([^"']+)["']/i);
    if (importMatch && importMatch[1]) {
      targetFile = resolveTargetFileName(importMatch[1]);
    }

    // 2. Check if clicked inside a quoted file string on the line
    if (!targetFile) {
      const stringRegex = /["']([^"']+\.zen)["']/gi;
      let match;
      while ((match = stringRegex.exec(lineContent)) !== null) {
        const startCol = match.index + 1;
        const endCol = match.index + match[0].length + 1;
        if (pos.column >= startCol && pos.column <= endCol) {
          targetFile = resolveTargetFileName(match[1]);
          break;
        }
      }
    }

    // 3. Fallback: check word under cursor
    if (!targetFile) {
      const wordInfo = model.getWordAtPosition(pos);
      if (wordInfo && wordInfo.word) {
        targetFile = resolveTargetFileName(wordInfo.word);
      }
    }

    if (targetFile) {
      const now = Date.now();
      if (now - lastFileSwitchTime < 350) return true;
      lastFileSwitchTime = now;

      if (targetFile !== window.currentActiveFile) {
        console.log(`[Zenith Studio] Navigating to tab: ${targetFile}`);
        switchFile(targetFile);
        return true;
      }
    }
    return false;
  }

  // Double-click (detail === 2) and Ctrl/Cmd-click on Monaco editor
  editor.onMouseDown((e) => {
    const isDoubleClick = (e.event && e.event.detail === 2);
    const isCtrlClick = (e.event && (e.event.ctrlKey || e.event.metaKey));
    if (isDoubleClick || isCtrlClick) {
      const handled = handleEditorFileNavigation(e);
      if (handled && isCtrlClick) {
        e.event.preventDefault();
        e.event.stopPropagation();
      }
    }
  });

  // DOM double-click fallback on editor container
  const editorContainer = document.getElementById('monacoEditorContainer');
  if (editorContainer) {
    editorContainer.addEventListener('dblclick', () => {
      handleEditorFileNavigation();
    });
  }

  // Track active modular file and discovered schema modules
  window.currentActiveFile = 'schema.zen';
  window.currentSchemaFiles = [];
  window.fileBuffers = {};

  // Always fetch active schema file from server on startup
  fetch('/api/schema')
    .then(r => r.json())
    .then(d => {
      if (d && d.fileName) {
        window.currentActiveFile = d.fileName;
        const nameEl = document.getElementById('currentFileName');
        if (nameEl) nameEl.textContent = d.fileName;
        document.title = 'Zenith Studio PRO - ' + d.fileName;
      }
      if (d && d.files) {
        renderSchemaTabs(d.files);
      } else {
        renderSchemaTabs([{ name: window.currentActiveFile, relativePath: window.currentActiveFile, isRoot: true, isImported: true }]);
      }
      if (d && d.exists && d.content && d.content.trim().length > 0) {
        editor.setValue(d.content);
      } else {
        editor.setValue(defaultSchema);
      }
      triggerCompile();
    })
    .catch(() => {
      renderSchemaTabs([{ name: 'schema.zen', relativePath: 'schema.zen', isRoot: true, isImported: true }]);
      editor.setValue(defaultSchema);
      triggerCompile();
    });

  // Mount source code viewer (read-only Monaco editor)
  viewerEditor = monaco.editor.create(document.getElementById('sourceViewerContainer'), {
    value: '// Select a file to view code',
    language: 'java',
    theme: 'zenith-dark',
    readOnly: true,
    automaticLayout: true,
    fontSize: 13,
    fontFamily: 'JetBrains Mono',
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    lineNumbersMinChars: 3,
    padding: { top: 10, bottom: 10 },
  });

  window.editor = editor;
  window.viewerEditor = viewerEditor;

  // Live Compilation Debounce & Auto-Save
  let timeoutId = null;
  editor.onDidChangeModelContent(() => {
    // If user is actively typing, dismiss previous error banner so it never blocks view
    dismissError();
    const currentCode = editor.getValue();
    window.fileBuffers[window.currentActiveFile] = currentCode;
    try {
      localStorage.setItem('zenith_studio_' + window.currentActiveFile, currentCode);
    } catch (_) {}
    updateSaveIndicator('saving');

    clearTimeout(timeoutId);
    timeoutId = setTimeout(() => {
      triggerCompile();
      // Auto-save to the active file on disk as well
      fetch('/api/schema', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ source: currentCode, file: window.currentActiveFile })
      })
      .then(r => r.json())
      .then(d => {
        if (d && d.files) renderSchemaTabs(d.files);
        updateSaveIndicator('saved');
      })
      .catch(() => updateSaveIndicator('saved-local'));
    }, 600);
  });

  // Initialize draggable split resizer
  initPaneResizer();

  // Initial compile
  triggerCompile();
});

// Draggable Split Pane Resizer (VS Code style)
function initPaneResizer() {
  const resizer = document.getElementById('paneResizer');
  const workspace = document.getElementById('studioWorkspace');
  const editorPane = document.getElementById('editorPane');
  if (!resizer || !workspace || !editorPane) return;
  if (resizer.dataset.initialized === 'true') return;
  resizer.dataset.initialized = 'true';

  // Restore saved width if available
  try {
    const savedWidth = localStorage.getItem('zenith_studio_editor_width');
    if (savedWidth) {
      const widthNum = parseFloat(savedWidth);
      const workspaceWidth = workspace.getBoundingClientRect().width;
      if (widthNum >= 260 && (!workspaceWidth || widthNum <= workspaceWidth - 260)) {
        editorPane.style.width = widthNum + 'px';
        editorPane.style.flex = `0 0 ${widthNum}px`;
      }
    }
  } catch (_) {}

  let isDragging = false;
  let rafId = null;

  resizer.addEventListener('mousedown', (e) => {
    e.preventDefault();
    isDragging = true;
    resizer.classList.add('is-dragging');
    document.body.classList.add('resizing-panels');
  });

  window.addEventListener('mousemove', (e) => {
    if (!isDragging) return;
    e.preventDefault();

    if (rafId) cancelAnimationFrame(rafId);
    rafId = requestAnimationFrame(() => {
      const rect = workspace.getBoundingClientRect();
      const rawX = e.clientX - rect.left;
      const minW = 260;
      const maxW = Math.max(minW + 50, rect.width - 260);
      const clampedX = Math.max(minW, Math.min(maxW, rawX));

      editorPane.style.width = clampedX + 'px';
      editorPane.style.flex = `0 0 ${clampedX}px`;

      // Trigger Monaco editor redraws
      if (window.editor) window.editor.layout();
      if (window.viewerEditor) window.viewerEditor.layout();

      // Redraw ERD arrows if ERD tab is active (cheap SVG path update without destroying DOM)
      redrawErdIfActive();
    });
  });

  window.addEventListener('mouseup', () => {
    if (!isDragging) return;
    isDragging = false;
    resizer.classList.remove('is-dragging');
    document.body.classList.remove('resizing-panels');

    const currentW = Math.round(editorPane.getBoundingClientRect().width);
    try {
      localStorage.setItem('zenith_studio_editor_width', currentW);
    } catch (_) {}

    if (window.editor) window.editor.layout();
    if (window.viewerEditor) window.viewerEditor.layout();

    redrawErdIfActive();
    requestAnimationFrame(redrawErdIfActive);
  });

  // Double click resets to 50/50
  resizer.addEventListener('dblclick', () => {
    const rect = workspace.getBoundingClientRect();
    const half = Math.round(rect.width / 2);
    editorPane.style.width = half + 'px';
    editorPane.style.flex = `0 0 ${half}px`;
    try {
      localStorage.setItem('zenith_studio_editor_width', half);
    } catch (_) {}
    if (window.editor) window.editor.layout();
    if (window.viewerEditor) window.viewerEditor.layout();

    redrawErdIfActive();
    requestAnimationFrame(redrawErdIfActive);
  });

  window.addEventListener('resize', () => {
    const rect = workspace.getBoundingClientRect();
    const currentW = editorPane.getBoundingClientRect().width;
    const maxW = rect.width - 260;
    if (currentW > maxW && maxW >= 260) {
      editorPane.style.width = maxW + 'px';
      editorPane.style.flex = `0 0 ${maxW}px`;
    }
    if (window.editor) window.editor.layout();
    if (window.viewerEditor) window.viewerEditor.layout();

    redrawErdIfActive();
    requestAnimationFrame(redrawErdIfActive);
  });
}

// Ensure resizer runs on DOM load as well
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initPaneResizer);
} else {
  initPaneResizer();
}

function escapeHtml(str) {
  return (str || '').replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function resolveTargetFileName(raw) {
  if (!raw || typeof raw !== 'string') return null;

  let cleaned = raw.trim().replace(/^['"]|['"]$/g, '');
  cleaned = cleaned.replace(/^\.\//, '').replace(/^\//, '');
  let baseName = cleaned.split('/').pop().split('\\').pop();
  if (!baseName) return null;

  const files = window.currentSchemaFiles || [];
  const candidateWithExt = baseName.toLowerCase().endsWith('.zen') ? baseName : (baseName + '.zen');
  const candidateNoExt = baseName.replace(/\.zen$/i, '');

  for (const f of files) {
    const fName = (f.name || '').toLowerCase();
    const fRel = (f.relativePath || '').toLowerCase().replace(/\\/g, '/');
    const fBase = fName.split('/').pop().split('\\').pop();
    const fBaseNoExt = fBase.replace(/\.zen$/i, '');

    if (
      fName === cleaned.toLowerCase() ||
      fRel === cleaned.toLowerCase() ||
      fBase === baseName.toLowerCase() ||
      fBase === candidateWithExt.toLowerCase() ||
      fBaseNoExt === candidateNoExt.toLowerCase()
    ) {
      return f.name;
    }
  }

  if (baseName.toLowerCase().endsWith('.zen')) {
    return baseName;
  }

  const match = files.find(f => f.name.toLowerCase().startsWith(candidateNoExt.toLowerCase() + '.'));
  if (match) {
    return match.name;
  }

  return null;
}

function renderSchemaTabs(files) {
  if (!files || !Array.isArray(files) || files.length === 0) {
    files = [{ name: window.currentActiveFile || 'schema.zen', relativePath: window.currentActiveFile || 'schema.zen', isRoot: true, isImported: true }];
  }
  window.currentSchemaFiles = files;

  // 1. Horizontal Top Bar: Display ONLY the currently active file
  const hContainer = document.getElementById('editorTabsBar');
  if (hContainer) {
    hContainer.innerHTML = '';
    const activeFile = files.find(f => f.name === window.currentActiveFile) || files[0];
    if (activeFile) {
      const tab = document.createElement('div');
      tab.className = 'file-tab active';
      tab.title = activeFile.relativePath + (activeFile.isRoot ? ' (Root Entry Schema)' : activeFile.isImported ? ' (Imported module)' : ' (.zen file)');

      let badgeHtml = '';
      if (activeFile.isRoot) {
        badgeHtml = '<span class="tab-badge-root">ROOT</span>';
      } else if (activeFile.isImported) {
        badgeHtml = '<span class="tab-badge-imported">MOD</span>';
      }

      tab.innerHTML = `
        <span class="file-tab-icon">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
            <polyline points="14 2 14 8 20 8"></polyline>
          </svg>
        </span>
        <span id="currentFileName">${escapeHtml(activeFile.name)}</span>
        ${badgeHtml}
      `;
      hContainer.appendChild(tab);
    }
  }

  // 2. Vertical Files Sidebar: Display ALL workspace schema files stacked vertically
  const vContainer = document.getElementById('verticalTabsList');
  if (vContainer) {
    vContainer.innerHTML = '';
    files.forEach(f => {
      const vTab = document.createElement('div');
      const isActive = (f.name === window.currentActiveFile);
      vTab.className = 'v-file-item' + (isActive ? ' active' : '');
      vTab.title = f.relativePath + (f.isRoot ? ' (Root Entry Schema)' : f.isImported ? ' (Imported module)' : ' (.zen file in workspace)');

      let badgeHtml = '';
      if (f.isRoot) {
        badgeHtml = '<span class="tab-badge-root">ROOT</span>';
      } else if (f.isImported) {
        badgeHtml = '<span class="tab-badge-imported">MOD</span>';
      }

      vTab.innerHTML = `
        <span class="v-file-icon">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
            <polyline points="14 2 14 8 20 8"></polyline>
          </svg>
        </span>
        <span class="v-file-name">${escapeHtml(f.name)}</span>
        ${badgeHtml}
      `;

      vTab.onclick = () => switchFile(f.name);
      vContainer.appendChild(vTab);

      if (isActive) {
        setTimeout(() => {
          try {
            vTab.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
          } catch (_) {}
        }, 50);
      }
    });
  }

  // Ensure Monaco editor redraws cleanly
  if (window.editor) {
    setTimeout(() => {
      try {
        window.editor.layout();
      } catch (_) {}
    }, 60);
  }
}

async function switchFile(targetFileName) {
  if (!targetFileName || targetFileName === window.currentActiveFile) return;

  // 1. Save current active buffer before switching
  const currentContent = editor.getValue();
  window.fileBuffers[window.currentActiveFile] = currentContent;

  try {
    await fetch('/api/schema', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ source: currentContent, file: window.currentActiveFile })
    });
  } catch (err) {
    console.warn('Auto-save error before file switch:', err);
  }

  // 2. Fetch target file content from server
  try {
    const res = await fetch('/api/schema?file=' + encodeURIComponent(targetFileName));
    const data = await res.json();
    window.currentActiveFile = targetFileName;
    const nameEl = document.getElementById('currentFileName');
    if (nameEl) nameEl.textContent = targetFileName;
    document.title = 'Zenith Studio PRO - ' + targetFileName;

    // Use in-memory buffer if we previously typed in it, otherwise use server file content
    const newContent = window.fileBuffers[targetFileName] !== undefined ? window.fileBuffers[targetFileName] : (data.content || '');
    editor.setValue(newContent);

    if (data.files) {
      renderSchemaTabs(data.files);
    } else {
      renderSchemaTabs(window.currentSchemaFiles);
    }

    // 3. Re-compile unified schema with the new active file override
    triggerCompile();
  } catch (err) {
    console.error('Error switching file to ' + targetFileName, err);
  }
}

async function createNewZenFile() {
  const fileName = prompt("Enter new module file name (e.g. 'auth.zen', 'projects.zen'):");
  if (!fileName || !fileName.trim()) return;
  const cleanName = fileName.trim();

  try {
    const res = await fetch('/api/schema', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'create', fileName: cleanName })
    });
    const data = await res.json();
    if (data.success) {
      if (data.files) {
        renderSchemaTabs(data.files);
      }
      await switchFile(data.fileName);
    } else {
      alert("Failed creating file: " + (data.error || "Unknown error"));
    }
  } catch (err) {
    alert("Error creating file: " + err.message);
  }
}

function updateSaveIndicator(status) {
  const el = document.getElementById('saveStatus');
  if (!el) return;
  if (status === 'saving') {
    el.innerHTML = '<span style="color:#eab308">●</span> Saving...';
  } else if (status === 'saved') {
    el.innerHTML = '<span style="color:#22c55e">✓</span> Saved';
  } else if (status === 'saved-local') {
    el.innerHTML = '<span style="color:#3b82f6">✓</span> Saved (browser)';
  }
}

function resetToDefaultSchema() {
  if (confirm("Reset editor to default example schema? Your current edits will be replaced.")) {
    editor.setValue(defaultSchema);
    window.fileBuffers[window.currentActiveFile] = defaultSchema;
    try {
      localStorage.setItem('zenith_studio_' + window.currentActiveFile, defaultSchema);
    } catch (_) {}
    fetch('/api/schema', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ source: defaultSchema, file: window.currentActiveFile })
    }).catch(() => {});
    triggerCompile();
    updateSaveIndicator('saved');
  }
}

// Trigger Live In-Memory Compilation with Error Analysis
async function triggerCompile() {
  const code = editor.getValue();
  const startTime = performance.now();

  try {
    const res = await fetch('/api/compile', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ source: code, file: window.currentActiveFile || 'schema.zen' })
    });

    const data = await res.json();
    const elapsed = Math.round(performance.now() - startTime);

    if (data.schemaFiles) {
      renderSchemaTabs(data.schemaFiles);
    }

    if (data.success) {
      currentCompilation = data;
      window.currentCompilation = data;

      // Clear Monaco error markers
      monaco.editor.setModelMarkers(editor.getModel(), 'zenith', []);
      dismissError();

      document.getElementById('schemaStatus').innerHTML = `
        <span class="status-dot ready"></span>
        <span id="statusText">Valid Schema &bull; ${data.models.length} Models &bull; ${data.files.length} Files</span>
      `;
      const badge = document.getElementById('compileTimeBadge');
      badge.innerText = `Compiled in ${elapsed}ms`;
      badge.className = 'compile-badge';

      // Render All Views
      renderErd(data.models);
      renderApiExplorer(data.models, data.config);
      renderJavaTree(data.files);
      renderTypeScript(data.files);
      renderDocker(data.files);

    } else {
      // Highlight exact error line in Monaco Editor
      const errLine = data.line || 1;
      const errCol = data.column || 1;
      const errMsg = data.error || 'Syntax error';

      monaco.editor.setModelMarkers(editor.getModel(), 'zenith', [{
        startLineNumber: errLine,
        startColumn: errCol,
        endLineNumber: errLine,
        endColumn: errCol + 15,
        message: errMsg,
        severity: monaco.MarkerSeverity.Error
      }]);

      showErrorDrawer(errMsg, errLine, errCol);

      document.getElementById('schemaStatus').innerHTML = `
        <span class="status-dot error"></span>
        <span id="statusText" style="color: #ef4444;">Error at line ${errLine}</span>
      `;
      const badge = document.getElementById('compileTimeBadge');
      badge.innerText = `Syntax Error`;
      badge.className = 'compile-badge error-badge';
    }
  } catch (err) {
    console.error('Compile error:', err);
  }
}

// Show Diagnostic Drawer
function showErrorDrawer(msg, line, col) {
  const drawer = document.getElementById('errorDrawer');
  const body = document.getElementById('errorDrawerBody');
  body.innerHTML = `<strong>Line ${line}, Column ${col}:</strong><br/>${escapeHtml(msg)}`;
  drawer.className = 'error-drawer show';
}

function dismissError() {
  document.getElementById('errorDrawer').className = 'error-drawer';
}

// Switch Right Preview Tabs
function switchPreviewTab(tabKey) {
  document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
  document.querySelectorAll('.preview-panel').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.tab-dropdown-item').forEach(i => i.classList.remove('active'));
  closeArtifactsDropdown();

  // Reset dropdown tab label when switching to primary tabs
  const labelEl = document.getElementById('artifactsTabLabel');
  if (labelEl) labelEl.textContent = 'SDK & Docker';

  if (typeof event !== 'undefined' && event && event.currentTarget && event.currentTarget.classList) {
    event.currentTarget.classList.add('active');
  } else {
    const btn = document.querySelector(`.tab-btn[onclick*="'${tabKey}'"]`);
    if (btn) btn.classList.add('active');
  }

  const panel = document.getElementById(`tab-${tabKey}`);
  if (panel) panel.classList.add('active');

  if (tabKey === 'erd') {
    requestAnimationFrame(redrawErdIfActive);
  }
}

function toggleArtifactsDropdown(e) {
  if (e) e.stopPropagation();
  const menu = document.getElementById('artifactsDropdownMenu');
  const wrap = document.getElementById('artifactsDropdownWrap');
  if (!menu || !wrap) return;
  const isShown = menu.classList.contains('show');
  if (isShown) {
    menu.classList.remove('show');
    wrap.classList.remove('open');
  } else {
    menu.classList.add('show');
    wrap.classList.add('open');
  }
}

function closeArtifactsDropdown() {
  const menu = document.getElementById('artifactsDropdownMenu');
  const wrap = document.getElementById('artifactsDropdownWrap');
  if (menu) menu.classList.remove('show');
  if (wrap) wrap.classList.remove('open');
}

window.addEventListener('click', (e) => {
  const wrap = document.getElementById('artifactsDropdownWrap');
  if (wrap && !wrap.contains(e.target)) {
    closeArtifactsDropdown();
  }
});

function selectArtifactTab(tabKey, label) {
  document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
  document.querySelectorAll('.preview-panel').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.tab-dropdown-item').forEach(i => i.classList.remove('active'));

  const dropdownBtn = document.getElementById('artifactsDropdownBtn');
  if (dropdownBtn) dropdownBtn.classList.add('active');

  const labelEl = document.getElementById('artifactsTabLabel');
  if (labelEl) labelEl.textContent = label;

  const itemEl = document.getElementById(`item-${tabKey}`);
  if (itemEl) itemEl.classList.add('active');

  const panel = document.getElementById(`tab-${tabKey}`);
  if (panel) panel.classList.add('active');

  if (tabKey === 'erd') {
    requestAnimationFrame(redrawErdIfActive);
  }

  closeArtifactsDropdown();

  if (tabKey === 'typescript') {
    switchTsFile(currentTsTab || 'client');
  } else if (tabKey === 'docker') {
    if (currentCompilation && currentCompilation.files) {
      renderDocker(currentCompilation.files);
    }
  }
}

// ==========================================
// Architecture ERD with Clean Smart Routing
// ==========================================
let currentErdMode = 'smart'; // 'smart' (Focus on hover) or 'all' (Show all lines)
let activeHoveredModel = null;
let activeHoveredRelation = null;
let erdSearchQuery = '';

function setErdMode(mode) {
  currentErdMode = mode;
  document.querySelectorAll('.erd-toggle-btn').forEach(b => b.classList.remove('active'));
  const activeBtn = document.getElementById(mode === 'smart' ? 'btnErdModeSmart' : 'btnErdModeAll');
  if (activeBtn) activeBtn.classList.add('active');

  redrawErdIfActive();
}

function filterErdModels(query) {
  erdSearchQuery = (query || '').trim().toLowerCase();
  const cards = document.querySelectorAll('.erd-model-card');
  cards.forEach(card => {
    const name = (card.dataset.modelName || '').toLowerCase();
    const text = card.innerText.toLowerCase();
    const matches = !erdSearchQuery || name.includes(erdSearchQuery) || text.includes(erdSearchQuery);
    card.style.display = matches ? '' : 'none';
    if (erdSearchQuery && matches) {
      card.classList.add('erd-highlight-active');
    } else {
      card.classList.remove('erd-highlight-active');
    }
  });

  redrawErdIfActive();
}

// Topological & Adjacency Pairing Layout
function computeOptimalModelOrder(models) {
  if (!models || models.length === 0) return [];

  // Build Adjacency Graph
  const adj = {};
  models.forEach(m => { adj[m.name] = new Set(); });
  models.forEach(m => {
    (m.relations || []).forEach(r => {
      if (r.targetModel && r.targetModel !== m.name && adj[r.targetModel]) {
        adj[m.name].add(r.targetModel);
        adj[r.targetModel].add(m.name);
      }
    });
  });

  const ordered = [];
  const visited = new Set();
  const sortedByDeg = [...models].sort((a, b) => (adj[b.name]?.size || 0) - (adj[a.name]?.size || 0));

  while (visited.size < models.length) {
    const unvisited = sortedByDeg.filter(m => !visited.has(m.name));
    if (unvisited.length === 0) break;

    const current = unvisited[0];
    ordered.push(current);
    visited.add(current.name);

    // Pick best adjacent partner to place in opposite column of the same row
    const neighbors = [...(adj[current.name] || [])]
      .map(name => models.find(m => m.name === name))
      .filter(m => m && !visited.has(m.name));

    if (neighbors.length > 0) {
      neighbors.sort((a, b) => (adj[b.name]?.size || 0) - (adj[a.name]?.size || 0));
      const partner = neighbors[0];
      ordered.push(partner);
      visited.add(partner.name);
    } else {
      const fallback = unvisited.find(m => !visited.has(m.name));
      if (fallback) {
        ordered.push(fallback);
        visited.add(fallback.name);
      }
    }
  }

  return ordered;
}

// Render Architecture ERD
function renderErd(models) {
  const canvas = document.getElementById('erdCanvas');
  if (!canvas) return;
  canvas.innerHTML = '';

  if (!models || models.length === 0) {
    canvas.innerHTML = '<div style="color: var(--text-muted); padding: 40px; text-align: center;">No models declared yet. Define a <code>model Name { ... }</code> in the editor.</div>';
    return;
  }

  // Update Stats in Toolbar
  const statsEl = document.getElementById('erdStatsBadge');
  if (statsEl) {
    const totalRelations = models.reduce((acc, m) => acc + (m.relations ? m.relations.length : 0), 0);
    statsEl.innerText = `${models.length} Models • ${totalRelations} Relations`;
  }

  // Create SVG Layer
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', 'erd-svg-layer');
  svg.id = 'erdSvgLayer';
  svg.innerHTML = `
    <defs>
      <marker id="arrowhead" markerWidth="7" markerHeight="5" refX="6" refY="2.5" orient="auto">
        <polygon points="0 0, 7 2.5, 0 5" fill="#3b82f6" />
      </marker>
      <marker id="arrowhead-active" markerWidth="8" markerHeight="6" refX="7" refY="3" orient="auto">
        <polygon points="0 0, 8 3, 0 6" fill="#38bdf8" />
      </marker>
    </defs>
    <!-- Floating interactive tooltip (single instance, zero collision) -->
    <g id="erdFloatingTooltip" style="opacity: 0; pointer-events: none; transition: opacity 0.15s ease;">
      <rect id="erdTooltipBg" class="erd-tooltip-bg" x="0" y="0" width="160" height="26" />
      <text id="erdTooltipText" class="erd-tooltip-text" x="0" y="0"></text>
    </g>
  `;
  canvas.appendChild(svg);

  // Cards Container
  const cardsWrap = document.createElement('div');
  cardsWrap.className = 'erd-cards-container';
  canvas.appendChild(cardsWrap);

  const orderedModels = computeOptimalModelOrder(models);

  orderedModels.forEach(model => {
    const card = document.createElement('div');
    card.className = 'erd-model-card';
    card.id = `erd-card-${model.name}`;
    card.dataset.modelName = model.name;

    let fieldsHtml = '';
    model.fields.forEach(f => {
      let badges = '';
      if (f.id) badges += '<span class="erd-badge">PK</span>';
      if (f.unique) badges += '<span class="erd-badge">UQ</span>';
      if (f.password) badges += '<span class="erd-badge">HASH</span>';
      if (f.role) badges += '<span class="erd-badge">ROLE</span>';

      fieldsHtml += `
        <div class="erd-field-row" id="field-${model.name}-${f.name}">
          <span class="erd-field-name">${f.name}</span>
          <span class="erd-field-type">${f.type} ${badges}</span>
        </div>
      `;
    });

    let relationsHtml = '';
    if (model.relations && model.relations.length > 0) {
      relationsHtml = '<div style="border-top: 1px solid var(--border-muted); margin-top: 6px; padding-top: 6px;">';
      model.relations.forEach(r => {
        const fieldName = r.fieldName || r.field || '';
        const relType = r.relationType || r.type || 'manyToOne';
        const formattedType = relType.replace(/_/g, '').toLowerCase();

        relationsHtml += `
          <div class="erd-field-row erd-rel-row" 
               id="rel-${model.name}-${fieldName}" 
               data-source-model="${model.name}" 
               data-target-model="${r.targetModel}" 
               data-field="${fieldName}"
               data-rel-type="${relType}">
            <span style="color: #60a5fa; display: flex; align-items: center; gap: 4px;">
              <svg class="rel-arrow-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"></polyline></svg>
              ${fieldName}
            </span>
            <span class="erd-field-type">${r.targetModel} (${formattedType})</span>
          </div>
        `;
      });
      relationsHtml += '</div>';
    }

    card.innerHTML = `
      <div class="erd-model-header">
        <span>${model.name}</span>
        <span style="font-size: 0.72rem; color: #a1a1aa;">${model.fields.length} cols</span>
      </div>
      <div class="erd-model-fields">
        ${fieldsHtml}
        ${relationsHtml}
      </div>
    `;

    // Interactive Card Hover
    card.onmouseenter = () => highlightModelRelations(model.name);
    card.onmouseleave = () => resetModelHighlights();

    cardsWrap.appendChild(card);

    // Interactive Relation Row Hover & Click Navigation
    card.querySelectorAll('.erd-rel-row').forEach(row => {
      const targetModel = row.dataset.targetModel;
      const field = row.dataset.field;
      row.onmouseenter = (e) => {
        e.stopPropagation();
        highlightSpecificRelation(model.name, targetModel, field);
      };
      row.onmouseleave = (e) => {
        e.stopPropagation();
        highlightModelRelations(model.name);
      };
      row.onclick = (e) => {
        e.stopPropagation();
        focusModelCard(targetModel);
      };
    });
  });

  // Set up ResizeObserver on canvas & container so resizing split pane or window recalculates paths instantly
  if (!canvas.dataset.resizeObserved) {
    canvas.dataset.resizeObserved = 'true';
    if (window.ResizeObserver) {
      const ro = new ResizeObserver(() => {
        requestAnimationFrame(redrawErdIfActive);
      });
      ro.observe(canvas);
      if (cardsWrap) ro.observe(cardsWrap);
    }
  }

  requestAnimationFrame(() => {
    drawRelationArrows(models, canvas, svg);
  });

  canvas.onscroll = () => redrawErdIfActive();
  window.onresize = () => redrawErdIfActive();
}

// Draw Clean Relation Paths without Center Badge Collisions
function drawRelationArrows(models, canvas, svg) {
  if (!svg || !canvas) return;

  // Clear existing lines/paths (keep defs and tooltip)
  svg.querySelectorAll('.erd-rel-group').forEach(p => p.remove());

  const canvasRect = canvas.getBoundingClientRect();
  const scrollW = Math.max(canvas.scrollWidth, canvas.clientWidth);
  const scrollH = Math.max(canvas.scrollHeight, canvas.clientHeight);
  svg.style.width = `${scrollW}px`;
  svg.style.height = `${scrollH}px`;
  svg.setAttribute('width', scrollW);
  svg.setAttribute('height', scrollH);

  const drawnPairs = new Set();

  models.forEach(model => {
    if (!model.relations) return;

    model.relations.forEach(r => {
      const sourceName = model.name;
      const targetName = r.targetModel;
      if (!targetName || sourceName === targetName) return;

      const sourceRelEl = document.getElementById(`rel-${sourceName}-${r.fieldName || r.field}`);
      const targetCard = document.getElementById(`erd-card-${targetName}`);
      const sourceCard = document.getElementById(`erd-card-${sourceName}`);
      if (!sourceRelEl || !targetCard || !sourceCard) return;

      // Check for reciprocal relation on target
      const targetModelDef = models.find(m => m.name === targetName);
      let targetRelEl = null;
      let targetRelField = '';
      if (targetModelDef && targetModelDef.relations) {
        const backRel = targetModelDef.relations.find(br => br.targetModel === sourceName);
        if (backRel) {
          targetRelField = backRel.fieldName || backRel.field || '';
          targetRelEl = document.getElementById(`rel-${targetName}-${targetRelField}`);
        }
      }

      // If target has no reciprocal relation row, point to its Primary Key (id) row!
      if (!targetRelEl) {
        targetRelEl = targetCard.querySelector('.erd-field-row') || targetCard;
      }

      const pairKey = [sourceName, targetName].sort().join('<->');
      if (drawnPairs.has(pairKey)) return;
      drawnPairs.add(pairKey);

      const sRowRect = sourceRelEl.getBoundingClientRect();
      const sCardRect = sourceCard.getBoundingClientRect();
      const tCardRect = targetCard.getBoundingClientRect();
      const tRowRect = targetRelEl.getBoundingClientRect();

      // Skip drawing if either card is hidden or collapsed
      if (sCardRect.width === 0 || tCardRect.width === 0) return;

      const sCenterX = sCardRect.left + sCardRect.width / 2;
      const tCenterX = tCardRect.left + tCardRect.width / 2;
      const isDiffCol = Math.abs(sCenterX - tCenterX) > (sCardRect.width * 0.4);

      let startX, startY, endX, endY, pathData;
      let sPillX, sPillY, tPillX, tPillY;

      if (isDiffCol) {
        // Different Columns: connect cleanly across central gap
        if (sCenterX < tCenterX) {
          // Source on LEFT, Target on RIGHT
          startX = sCardRect.right - canvasRect.left + canvas.scrollLeft;
          startY = sRowRect.top - canvasRect.top + canvas.scrollTop + sRowRect.height / 2;

          endX = tCardRect.left - canvasRect.left + canvas.scrollLeft;
          endY = tRowRect.top - canvasRect.top + canvas.scrollTop + tRowRect.height / 2;

          const midX = (startX + endX) / 2;
          pathData = `M ${startX} ${startY} C ${midX} ${startY}, ${midX} ${endY}, ${endX} ${endY}`;

          sPillX = startX + 11; sPillY = startY;
          tPillX = endX - 11; tPillY = endY;
        } else {
          // Source on RIGHT, Target on LEFT
          startX = sCardRect.left - canvasRect.left + canvas.scrollLeft;
          startY = sRowRect.top - canvasRect.top + canvas.scrollTop + sRowRect.height / 2;

          endX = tCardRect.right - canvasRect.left + canvas.scrollLeft;
          endY = tRowRect.top - canvasRect.top + canvas.scrollTop + tRowRect.height / 2;

          const midX = (startX + endX) / 2;
          pathData = `M ${startX} ${startY} C ${midX} ${startY}, ${midX} ${endY}, ${endX} ${endY}`;

          sPillX = startX - 11; sPillY = startY;
          tPillX = endX + 11; tPillY = endY;
        }
      } else {
        // Same Column: route gracefully via outer margin/gutter (never cutting through cards!)
        const isLeftCol = sCenterX < (canvasRect.left + canvasRect.width / 2);
        if (isLeftCol) {
          startX = sCardRect.left - canvasRect.left + canvas.scrollLeft;
          startY = sRowRect.top - canvasRect.top + canvas.scrollTop + sRowRect.height / 2;

          endX = tCardRect.left - canvasRect.left + canvas.scrollLeft;
          endY = tRowRect.top - canvasRect.top + canvas.scrollTop + tRowRect.height / 2;

          const gutterX = Math.min(startX, endX) - 30;
          pathData = `M ${startX} ${startY} C ${gutterX} ${startY}, ${gutterX} ${endY}, ${endX} ${endY}`;

          sPillX = startX - 11; sPillY = startY;
          tPillX = endX - 11; tPillY = endY;
        } else {
          startX = sCardRect.right - canvasRect.left + canvas.scrollLeft;
          startY = sRowRect.top - canvasRect.top + canvas.scrollTop + sRowRect.height / 2;

          endX = tCardRect.right - canvasRect.left + canvas.scrollLeft;
          endY = tRowRect.top - canvasRect.top + canvas.scrollTop + tRowRect.height / 2;

          const gutterX = Math.max(startX, endX) + 30;
          pathData = `M ${startX} ${startY} C ${gutterX} ${startY}, ${gutterX} ${endY}, ${endX} ${endY}`;

          sPillX = startX + 11; sPillY = startY;
          tPillX = endX + 11; tPillY = endY;
        }
      }

      // Group Container
      const group = document.createElementNS('http://www.w3.org/2000/svg', 'g');
      group.setAttribute('class', 'erd-rel-group');
      group.dataset.source = sourceName;
      group.dataset.target = targetName;
      group.dataset.pair = pairKey;

      // Main Connection Path
      const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      path.setAttribute('d', pathData);
      path.setAttribute('class', 'erd-rel-path');
      path.setAttribute('marker-end', 'url(#arrowhead)');
      group.appendChild(path);

      // Port Cardinality Badges (Standard ERD notation at card ports)
      const relType = (r.relationType || r.type || 'manyToOne').toLowerCase();
      let sCard = '1', tCard = 'N';
      if (relType.includes('onetoone')) { sCard = '1'; tCard = '1'; }
      else if (relType.includes('manytomany')) { sCard = 'M'; tCard = 'N'; }
      else if (relType.includes('manytoone')) { sCard = 'N'; tCard = '1'; }

      appendCardinalityBadge(group, sPillX, sPillY, sCard);
      appendCardinalityBadge(group, tPillX, tPillY, tCard);

      // Interactive Hover on Path
      const sourceField = r.fieldName || r.field || '';
      const tooltipText = `${sourceName}.${sourceField} [${sCard}:${tCard}] ➔ ${targetName}`;
      const midX = (startX + endX) / 2;
      const midY = (startY + endY) / 2;

      group.onmouseenter = () => {
        showFloatingTooltip(midX, midY, tooltipText);
        highlightSpecificRelation(sourceName, targetName, sourceField);
      };
      group.onmouseleave = () => {
        hideFloatingTooltip();
        resetModelHighlights();
      };

      svg.appendChild(group);
    });
  });
}

// Append Clean Port-Bound Cardinality Badges
function appendCardinalityBadge(group, x, y, text) {
  const size = 15;
  const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
  rect.setAttribute('x', x - size / 2);
  rect.setAttribute('y', y - size / 2);
  rect.setAttribute('width', size);
  rect.setAttribute('height', size);
  rect.setAttribute('class', 'erd-cardinality-bg');

  const textEl = document.createElementNS('http://www.w3.org/2000/svg', 'text');
  textEl.setAttribute('x', x);
  textEl.setAttribute('y', y + 0.5);
  textEl.setAttribute('class', 'erd-cardinality-badge');
  textEl.textContent = text;

  group.appendChild(rect);
  group.appendChild(textEl);
}

// Floating Tooltip on Hover
function showFloatingTooltip(x, y, text) {
  const tt = document.getElementById('erdFloatingTooltip');
  const bg = document.getElementById('erdTooltipBg');
  const txt = document.getElementById('erdTooltipText');
  if (!tt || !bg || !txt) return;

  const w = text.length * 7 + 24;
  const h = 26;
  bg.setAttribute('x', x - w / 2);
  bg.setAttribute('y', y - h - 6);
  bg.setAttribute('width', w);
  bg.setAttribute('height', h);

  txt.setAttribute('x', x);
  txt.setAttribute('y', y - h / 2 - 5);
  txt.textContent = text;

  tt.style.opacity = '1';
}

function hideFloatingTooltip() {
  const tt = document.getElementById('erdFloatingTooltip');
  if (tt) tt.style.opacity = '0';
}

// Interactive Focus Highlights
function highlightModelRelations(modelName) {
  activeHoveredModel = modelName;
  const cards = document.querySelectorAll('.erd-model-card');
  const groups = document.querySelectorAll('.erd-rel-group');

  const partnerModels = new Set();
  groups.forEach(g => {
    const s = g.dataset.source;
    const t = g.dataset.target;
    if (s === modelName || t === modelName) {
      partnerModels.add(s);
      partnerModels.add(t);
      g.classList.add('highlighted');
      g.classList.remove('dimmed');
    } else {
      g.classList.remove('highlighted');
      if (currentErdMode === 'smart') g.classList.add('dimmed');
    }
  });

  cards.forEach(card => {
    const name = card.dataset.modelName;
    if (name === modelName) {
      card.classList.add('erd-highlight-active');
      card.classList.remove('erd-highlight-partner', 'erd-dimmed');
    } else if (partnerModels.has(name)) {
      card.classList.add('erd-highlight-partner');
      card.classList.remove('erd-highlight-active', 'erd-dimmed');
    } else {
      card.classList.remove('erd-highlight-active', 'erd-highlight-partner');
      if (currentErdMode === 'smart') card.classList.add('erd-dimmed');
    }
  });
}

function highlightSpecificRelation(sourceName, targetName, fieldName) {
  const pairKey = [sourceName, targetName].sort().join('<->');
  const cards = document.querySelectorAll('.erd-model-card');
  const groups = document.querySelectorAll('.erd-rel-group');

  groups.forEach(g => {
    if (g.dataset.pair === pairKey) {
      g.classList.add('highlighted', 'active-flow');
      g.classList.remove('dimmed');
    } else {
      g.classList.remove('highlighted', 'active-flow');
      g.classList.add('dimmed');
    }
  });

  cards.forEach(card => {
    const name = card.dataset.modelName;
    if (name === sourceName) {
      card.classList.add('erd-highlight-active');
      card.classList.remove('erd-highlight-partner', 'erd-dimmed');
    } else if (name === targetName) {
      card.classList.add('erd-highlight-partner');
      card.classList.remove('erd-highlight-active', 'erd-dimmed');
    } else {
      card.classList.remove('erd-highlight-active', 'erd-highlight-partner');
      card.classList.add('erd-dimmed');
    }
  });
}

function resetModelHighlights() {
  activeHoveredModel = null;
  activeHoveredRelation = null;
  hideFloatingTooltip();

  document.querySelectorAll('.erd-model-card').forEach(c => {
    c.classList.remove('erd-highlight-active', 'erd-highlight-partner', 'erd-dimmed');
  });
  document.querySelectorAll('.erd-rel-group').forEach(g => {
    g.classList.remove('highlighted', 'active-flow', 'dimmed');
  });
}

function focusModelCard(targetName) {
  const card = document.getElementById(`erd-card-${targetName}`);
  if (card) {
    card.scrollIntoView({ behavior: 'smooth', block: 'center' });
    card.classList.add('erd-card-focus-flash');
    setTimeout(() => card.classList.remove('erd-card-focus-flash'), 1200);
  }
}

// Helper to compute model slug matching Spring Boot Controller paths
function getModelSlug(modelName) {
  if (!modelName) return '';
  let plural = modelName;
  if (plural.endsWith('y') && !/[aeiou]y$/i.test(plural)) {
    plural = plural.substring(0, plural.length - 1) + 'ies';
  } else if (/[sxz]$|ch$|sh$/i.test(plural)) {
    plural = plural + 'es';
  } else if (!plural.endsWith('s')) {
    plural = plural + 's';
  }
  return plural.replace(/([A-Z])/g, (m, c, offset) => (offset > 0 ? '_' : '') + c.toLowerCase());
}

let activeEndpointTemplate = null;

// Persistent state cache for API Tester: remembers URL, params, requestBody, and response per endpoint
const apiTesterState = {};

function saveCurrentEndpointState() {
  if (!activeEndpointTemplate) return;
  const key = `${activeEndpointTemplate.method}:${activeEndpointTemplate.fullUrl}`;
  if (!apiTesterState[key]) apiTesterState[key] = {};

  const urlEl = document.getElementById('runnerUrl');
  const bodyEl = document.getElementById('runnerRequestBody');
  const statusEl = document.getElementById('runnerResponseStatus');
  const timeEl = document.getElementById('runnerResponseTime');
  const resEl = document.getElementById('runnerResponseBody');

  if (urlEl) apiTesterState[key].url = urlEl.value;
  if (bodyEl) apiTesterState[key].requestBody = bodyEl.value;
  if (statusEl && statusEl.innerText && statusEl.innerText !== '-') {
    apiTesterState[key].hasTested = true;
    apiTesterState[key].responseStatus = statusEl.innerText;
    apiTesterState[key].responseStatusColor = statusEl.style.color;
  }
  if (timeEl && timeEl.innerText) apiTesterState[key].responseTime = timeEl.innerText;
  if (resEl && resEl.innerText && !resEl.innerText.startsWith('Click "Send') && !resEl.innerText.startsWith('Sending request')) {
    apiTesterState[key].responseBody = resEl.innerText;
  }
}

function initApiTesterListeners() {
  const bodyEl = document.getElementById('runnerRequestBody');
  if (bodyEl && !bodyEl.dataset.initialized) {
    bodyEl.dataset.initialized = 'true';
    bodyEl.addEventListener('input', () => {
      if (activeEndpointTemplate) {
        const key = `${activeEndpointTemplate.method}:${activeEndpointTemplate.fullUrl}`;
        if (!apiTesterState[key]) apiTesterState[key] = {};
        apiTesterState[key].requestBody = bodyEl.value;
      }
    });
  }

  const urlEl = document.getElementById('runnerUrl');
  if (urlEl && !urlEl.dataset.initialized) {
    urlEl.dataset.initialized = 'true';
    urlEl.addEventListener('input', () => {
      if (activeEndpointTemplate) {
        const key = `${activeEndpointTemplate.method}:${activeEndpointTemplate.fullUrl}`;
        if (!apiTesterState[key]) apiTesterState[key] = {};
        apiTesterState[key].url = urlEl.value;
      }
    });
  }

  const tokenEl = document.getElementById('runnerAuthToken');
  if (tokenEl && !tokenEl.dataset.initialized) {
    tokenEl.dataset.initialized = 'true';
    tokenEl.addEventListener('input', () => {
      try { localStorage.setItem('zenith_auth_token', tokenEl.value.trim()); } catch (_) {}
    });
    try {
      const savedToken = localStorage.getItem('zenith_auth_token');
      if (savedToken && !tokenEl.value.trim()) {
        tokenEl.value = savedToken;
      }
    } catch (_) {}
  }
}

function updateEndpointBadge(key, color, title) {
  const item = document.querySelector(`.endpoint-item[data-endpoint-key="${key}"]`);
  if (item) {
    let dot = item.querySelector('.endpoint-status-dot');
    if (!dot) {
      dot = document.createElement('span');
      dot.className = 'endpoint-status-dot';
      item.appendChild(dot);
    }
    dot.style.background = color;
    dot.title = title || '';
  }
}

// Render Interactive API Tester
function renderApiExplorer(models, config) {
  initApiTesterListeners();
  const container = document.getElementById('endpointList');
  if (!container) return;
  container.innerHTML = '';

  const basePath = config.basePath || '/api/v1';
  let firstItem = null;

  // 1. Auth Group (if auth is enabled)
  const isAuthEnabled = Boolean(
    config.hasAuth ||
    config.auth === 'jwt' ||
    (config.auth && config.auth !== 'none') ||
    (typeof editor !== 'undefined' && editor && editor.getValue && editor.getValue().includes('auth: jwt'))
  );

  if (isAuthEnabled) {
    // Find the auth user model to accurately match Create<UserModel>Request DTO
    const authModelName = config.userModel || (currentCompilation && currentCompilation.userModel);
    const authUserModel = models.find(m => m.name === authModelName) ||
                          models.find(m => m.fields && m.fields.some(f => f.password || f.name.toLowerCase() === 'password')) ||
                          models[0];
    const registerPayload = authUserModel ? generateSamplePayload(authUserModel) : JSON.stringify({ email: "developer@example.com", password: "Password123!" }, null, 2);

    const authGroup = createEndpointGroup('🔐 Authentication', [
      {
        method: 'POST',
        pathDisplay: '/auth/login',
        fullUrl: `${basePath}/auth/login`,
        desc: 'Login & Obtain JWT Token',
        sampleBody: JSON.stringify({ email: "admin@example.com", password: "password123" }, null, 2)
      },
      {
        method: 'POST',
        pathDisplay: '/auth/register',
        fullUrl: `${basePath}/auth/register`,
        desc: 'Register new user account',
        sampleBody: registerPayload
      }
    ]);
    container.appendChild(authGroup.el);
    if (!firstItem && authGroup.first) firstItem = authGroup.first;
  }

  // 2. Model Groups
  models.forEach(m => {
    const slug = getModelSlug(m.name);
    const hasCrud = m.hasCrud !== false;
    const customList = m.customEndpoints || [];

    // Skip models that have no endpoints
    if (!hasCrud && customList.length === 0) return;

    const routes = [];
    const modelSample = generateSamplePayload(m);

    // Standard CRUD Routes
    if (hasCrud) {
      routes.push({
        method: 'GET',
        pathDisplay: `/${slug}`,
        fullUrl: `${basePath}/${slug}`,
        desc: 'List & Search'
      });
      routes.push({
        method: 'POST',
        pathDisplay: `/${slug}`,
        fullUrl: `${basePath}/${slug}`,
        desc: 'Create new',
        sampleBody: modelSample
      });
      routes.push({
        method: 'GET',
        pathDisplay: `/${slug}/{id}`,
        fullUrl: `${basePath}/${slug}/{id}`,
        desc: 'Find by ID'
      });
      routes.push({
        method: 'PUT',
        pathDisplay: `/${slug}/{id}`,
        fullUrl: `${basePath}/${slug}/{id}`,
        desc: 'Update',
        sampleBody: modelSample
      });
      routes.push({
        method: 'DELETE',
        pathDisplay: `/${slug}/{id}`,
        fullUrl: `${basePath}/${slug}/{id}`,
        desc: 'Delete'
      });
    }

    // Custom Actions & Sub-resources
    customList.forEach(cep => {
      let customSample = null;
      if (cep.bodyType) {
        customSample = generateDtoSamplePayload(cep.bodyType) || modelSample;
      }
      routes.push({
        method: cep.method.toUpperCase(),
        pathDisplay: cep.path,
        fullUrl: `${basePath}/${slug}${cep.path.startsWith('/') ? cep.path : '/' + cep.path}`,
        desc: cep.description || (cep.bodyType ? `body: ${cep.bodyType}` : 'Action'),
        sampleBody: customSample
      });
    });

    const modelGroup = createEndpointGroup(`📦 ${m.name}`, routes);
    container.appendChild(modelGroup.el);
    if (!firstItem && modelGroup.first) firstItem = modelGroup.first;
  });

  if (activeEndpointTemplate) {
    const prevKey = `${activeEndpointTemplate.method}:${activeEndpointTemplate.fullUrl}`;
    const prevItem = container.querySelector(`.endpoint-item[data-endpoint-key="${prevKey}"]`);
    if (prevItem) {
      prevItem.click();
      return;
    }
  }
  if (firstItem) firstItem.click();
}

function createEndpointGroup(title, routes) {
  const group = document.createElement('div');
  group.className = 'endpoint-group';

  const header = document.createElement('div');
  header.className = 'endpoint-group-header';
  header.innerHTML = `
    <span class="endpoint-group-title">
      <span class="endpoint-group-chevron">▾</span>
      ${title}
    </span>
    <span class="endpoint-group-badge">${routes.length}</span>
  `;
  header.onclick = () => group.classList.toggle('collapsed');
  group.appendChild(header);

  const itemsContainer = document.createElement('div');
  itemsContainer.className = 'endpoint-group-items';

  let first = null;
  routes.forEach(r => {
    const item = createEndpointItem(r);
    itemsContainer.appendChild(item);
    if (!first) first = item;
  });

  group.appendChild(itemsContainer);
  return { el: group, first };
}

function createEndpointItem(route) {
  const el = document.createElement('div');
  el.className = 'endpoint-item';
  const key = `${route.method}:${route.fullUrl}`;
  el.dataset.endpointKey = key;
  el.dataset.search = (route.method + ' ' + route.pathDisplay + ' ' + route.fullUrl + ' ' + (route.desc || '')).toLowerCase();

  const saved = apiTesterState[key];
  const dotHtml = (saved && saved.hasTested)
    ? `<span class="endpoint-status-dot" style="background:${saved.responseStatusColor || '#10b981'}" title="${saved.responseStatus || ''}"></span>`
    : '';

  el.innerHTML = `
    <span class="method-tag ${route.method.toLowerCase()}">${route.method}</span>
    <span class="endpoint-path" title="${route.fullUrl}">${route.pathDisplay}</span>
    ${route.desc ? `<span class="endpoint-desc" title="${route.desc}">${route.desc}</span>` : ''}
    ${dotHtml}
  `;

  el.onclick = () => selectEndpoint(route, el);
  return el;
}

function selectEndpoint(route, el) {
  // 1. Save state of previous active endpoint
  saveCurrentEndpointState();

  document.querySelectorAll('.endpoint-item').forEach(x => x.classList.remove('active'));
  if (el) el.classList.add('active');

  activeEndpointTemplate = route;
  const key = `${route.method}:${route.fullUrl}`;
  const saved = apiTesterState[key];

  // Set Method Badge
  const badge = document.getElementById('runnerMethod');
  badge.innerText = route.method;
  badge.className = `http-badge method-tag ${route.method.toLowerCase()}`;

  // Extract path variables: e.g. {id}, {projectId}
  const port = (currentCompilation && currentCompilation.config && currentCompilation.config.port) || 8080;
  const baseUrl = `http://localhost:${port}${route.fullUrl}`;
  const paramMatches = [...route.fullUrl.matchAll(/\{([^}]+)\}/g)];

  const paramsBar = document.getElementById('pathParamsContainer');
  const paramsInputs = document.getElementById('pathParamsInputs');

  if (paramMatches.length > 0) {
    paramsBar.style.display = 'flex';
    paramsInputs.innerHTML = '';
    const paramValues = (saved && saved.paramValues) ? { ...saved.paramValues } : {};

    paramMatches.forEach(m => {
      const pName = m[1];
      const defaultVal = paramValues[pName] || (pName.toLowerCase().includes('id') ? '3fa85f64-5717-4562-b3fc-2c963f66afa6' : '1');
      paramValues[pName] = defaultVal;

      const pill = document.createElement('div');
      pill.className = 'param-pill';
      pill.innerHTML = `
        <span class="param-key">${pName}:</span>
        <input type="text" class="param-input" value="${defaultVal}" data-param="${pName}" />
      `;
      const input = pill.querySelector('input');
      input.oninput = () => {
        paramValues[pName] = input.value;
        if (!apiTesterState[key]) apiTesterState[key] = {};
        apiTesterState[key].paramValues = paramValues;
        updateRunnerUrlWithParams(baseUrl, paramValues);
        apiTesterState[key].url = document.getElementById('runnerUrl').value;
      };
      paramsInputs.appendChild(pill);
    });

    if (saved && saved.url) {
      document.getElementById('runnerUrl').value = saved.url;
    } else {
      updateRunnerUrlWithParams(baseUrl, paramValues);
    }
  } else {
    paramsBar.style.display = 'none';
    document.getElementById('runnerUrl').value = (saved && saved.url) ? saved.url : baseUrl;
  }

  // Request body: restore saved customized body if present, else use default sample
  const bodyEl = document.getElementById('runnerRequestBody');
  if (saved && saved.requestBody !== undefined) {
    bodyEl.value = saved.requestBody;
  } else {
    bodyEl.value = route.sampleBody || '';
  }

  // Response status, time, and body: restore saved if previously executed
  const statusEl = document.getElementById('runnerResponseStatus');
  const timeEl = document.getElementById('runnerResponseTime');
  const resEl = document.getElementById('runnerResponseBody');

  if (saved && saved.hasTested) {
    statusEl.innerText = saved.responseStatus || '-';
    statusEl.style.color = saved.responseStatusColor || '';
    timeEl.innerText = saved.responseTime || '';
    resEl.innerText = saved.responseBody || '(Empty response)';
  } else {
    statusEl.innerText = '-';
    statusEl.style.color = '';
    timeEl.innerText = '';
    resEl.innerText = 'Click "Send ⚡" to test endpoint...';
  }
}

function updateRunnerUrlWithParams(templateUrl, paramValues) {
  let finalUrl = templateUrl;
  for (const [key, val] of Object.entries(paramValues)) {
    finalUrl = finalUrl.replace(`{${key}}`, val.trim() || `{${key}}`);
  }
  document.getElementById('runnerUrl').value = finalUrl;
}

function filterEndpoints(query) {
  const q = (query || '').trim().toLowerCase();
  document.querySelectorAll('.endpoint-group').forEach(group => {
    let hasMatch = false;
    group.querySelectorAll('.endpoint-item').forEach(item => {
      const text = item.dataset.search || '';
      if (!q || text.includes(q)) {
        item.style.display = 'flex';
        hasMatch = true;
      } else {
        item.style.display = 'none';
      }
    });
    group.style.display = hasMatch ? 'flex' : 'none';
    if (q && hasMatch) {
      group.classList.remove('collapsed');
    }
  });
}

function generateDtoSamplePayload(dtoName) {
  if (!dtoName || !currentCompilation || !currentCompilation.dtos) return null;
  const dto = currentCompilation.dtos.find(d => d.name.toLowerCase() === dtoName.toLowerCase());
  if (!dto) return null;
  const obj = {};
  dto.fields.forEach(f => {
    const t = f.type || 'String';
    if (t.startsWith('String')) obj[f.name] = 'Sample ' + f.name;
    else if (t.startsWith('Int') || t.startsWith('Long')) obj[f.name] = 1;
    else if (t.startsWith('Decimal')) obj[f.name] = 49.99;
    else if (t.startsWith('Boolean')) obj[f.name] = true;
    else if (t.startsWith('UUID')) obj[f.name] = '3fa85f64-5717-4562-b3fc-2c963f66afa6';
    else if (t.startsWith('DateTime') || t.startsWith('Date')) obj[f.name] = new Date().toISOString();
    else obj[f.name] = t;
  });
  return JSON.stringify(obj, null, 2);
}

function prettifyRequestBody() {
  const ta = document.getElementById('runnerRequestBody');
  try {
    const parsed = JSON.parse(ta.value);
    ta.value = JSON.stringify(parsed, null, 2);
    if (activeEndpointTemplate) {
      const key = `${activeEndpointTemplate.method}:${activeEndpointTemplate.fullUrl}`;
      if (!apiTesterState[key]) apiTesterState[key] = {};
      apiTesterState[key].requestBody = ta.value;
    }
  } catch (e) {
    alert('Invalid JSON: ' + e.message);
  }
}

function copyResponseBody() {
  const text = document.getElementById('runnerResponseBody').innerText;
  navigator.clipboard.writeText(text);
  const btn = event.target;
  const oldText = btn.innerText;
  btn.innerText = 'Copied!';
  setTimeout(() => btn.innerText = oldText, 1500);
}

function generateSamplePayload(model) {
  if (!model || !model.fields) return '{}';
  const obj = {};
  model.fields.forEach(f => {
    if (f.id || f.auto) return;
    const nameLower = f.name.toLowerCase();

    // Check if field has a default value (e.g. role = DEVELOPER)
    if (f.defaultValue && f.defaultValue.trim()) {
      let def = f.defaultValue.trim();
      if (def.includes('.')) def = def.split('.').pop();
      obj[f.name] = def;
      return;
    }

    // Smart semantic mock data
    if (nameLower.includes('email')) {
      obj[f.name] = 'developer@example.com';
    } else if (nameLower === 'password') {
      obj[f.name] = 'Password123!';
    } else if (nameLower === 'fullname' || nameLower === 'name' || nameLower === 'username') {
      obj[f.name] = 'Alex Mercer';
    } else if (f.type.startsWith('String')) {
      obj[f.name] = 'Sample ' + f.name;
    } else if (f.type.startsWith('Int') || f.type.startsWith('Long')) {
      obj[f.name] = 10;
    } else if (f.type.startsWith('Decimal')) {
      obj[f.name] = 49.99;
    } else if (f.type.startsWith('Boolean')) {
      obj[f.name] = true;
    } else if (f.type.startsWith('UUID')) {
      obj[f.name] = '3fa85f64-5717-4562-b3fc-2c963f66afa6';
    } else if (f.type.startsWith('DateTime') || f.type.startsWith('Date')) {
      obj[f.name] = new Date().toISOString();
    } else {
      // Check if type matches a known enum
      const enumMatch = (currentCompilation && currentCompilation.enums) 
        ? currentCompilation.enums.find(e => e.name === f.type) 
        : null;
      if (enumMatch && enumMatch.values && enumMatch.values.length > 0) {
        obj[f.name] = enumMatch.values[0];
      } else {
        obj[f.name] = f.type;
      }
    }
  });

  // Also include required relations (ManyToOne foreign keys like projectId)
  if (model.relations) {
    model.relations.forEach(r => {
      if (r.relationType === 'MANY_TO_ONE' || r.relationType === 'ONE_TO_ONE') {
        obj[r.fieldName + 'Id'] = '3fa85f64-5717-4562-b3fc-2c963f66afa6';
      }
    });
  }

  return JSON.stringify(obj, null, 2);
}

// Send Live API Request
async function sendApiRequest() {
  const method = document.getElementById('runnerMethod').innerText;
  const url = document.getElementById('runnerUrl').value;
  const bodyText = document.getElementById('runnerRequestBody').value;
  const token = document.getElementById('runnerAuthToken').value.trim();

  const resBox = document.getElementById('runnerResponseBody');
  const statusSpan = document.getElementById('runnerResponseStatus');
  const timeSpan = document.getElementById('runnerResponseTime');
  resBox.innerText = 'Sending request to Spring Boot backend...';
  timeSpan.innerText = '';

  const currentKey = activeEndpointTemplate ? `${activeEndpointTemplate.method}:${activeEndpointTemplate.fullUrl}` : null;

  const startTime = performance.now();
  try {
    const headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    };
    if (token) {
      headers['Authorization'] = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
    }

    const opts = { method, headers };
    if (['POST', 'PUT', 'PATCH'].includes(method) && bodyText.trim()) {
      opts.body = bodyText;
    }

    const res = await fetch(url, opts);
    const duration = Math.round(performance.now() - startTime);

    const statusText = `${res.status} ${res.statusText}`;
    const statusColor = res.ok ? '#10b981' : (res.status < 500 ? '#f59e0b' : '#f43f5e');
    const durationText = `⏱ ${duration}ms`;

    statusSpan.innerText = statusText;
    statusSpan.style.color = statusColor;
    timeSpan.innerText = durationText;

    const text = await res.text();
    try {
      const parsed = JSON.parse(text);
      resBox.innerText = JSON.stringify(parsed, null, 2);
      const extractedToken = parsed.token || parsed.accessToken || parsed.access_token ||
                             (parsed.data && (parsed.data.token || parsed.data.accessToken));
      if (extractedToken) {
        document.getElementById('runnerAuthToken').value = extractedToken;
        try { localStorage.setItem('zenith_auth_token', extractedToken); } catch (_) {}
      }
    } catch (_) {
      resBox.innerText = text || '(Empty response)';
    }

    // Persist full execution state so returning to this endpoint keeps everything
    if (currentKey) {
      if (!apiTesterState[currentKey]) apiTesterState[currentKey] = {};
      apiTesterState[currentKey].hasTested = true;
      apiTesterState[currentKey].url = url;
      apiTesterState[currentKey].requestBody = bodyText;
      apiTesterState[currentKey].responseStatus = statusText;
      apiTesterState[currentKey].responseStatusColor = statusColor;
      apiTesterState[currentKey].responseTime = durationText;
      apiTesterState[currentKey].responseBody = resBox.innerText;
      updateEndpointBadge(currentKey, statusColor, statusText);
    }
  } catch (err) {
    const duration = Math.round(performance.now() - startTime);
    const statusText = 'Connection Error';
    const statusColor = '#f43f5e';
    const durationText = `⏱ ${duration}ms`;

    statusSpan.innerText = statusText;
    statusSpan.style.color = statusColor;
    timeSpan.innerText = durationText;
    resBox.innerText = `Error connecting to ${url}.\n\nEnsure Spring Boot is running (click "Run" in top bar)!\nDetails: ${err.message}`;

    if (currentKey) {
      if (!apiTesterState[currentKey]) apiTesterState[currentKey] = {};
      apiTesterState[currentKey].hasTested = true;
      apiTesterState[currentKey].url = url;
      apiTesterState[currentKey].requestBody = bodyText;
      apiTesterState[currentKey].responseStatus = statusText;
      apiTesterState[currentKey].responseStatusColor = statusColor;
      apiTesterState[currentKey].responseTime = durationText;
      apiTesterState[currentKey].responseBody = resBox.innerText;
      updateEndpointBadge(currentKey, statusColor, statusText);
    }
  }
}

function quickLoginDemo() {
  const token = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImV4cCI6MTk5OTk5OTk5OX0.sample_zenith_jwt_token';
  const el = document.getElementById('runnerAuthToken');
  if (el) {
    el.value = token;
    try { localStorage.setItem('zenith_auth_token', token); } catch (_) {}
  }
}

let currentActiveJavaFilePath = null;
try {
  currentActiveJavaFilePath = localStorage.getItem('zenith_active_file') || null;
} catch (_) {}

// Build and Render Hierarchical Repository Tree
function renderJavaTree(files) {
  const treeContainer = document.getElementById('javaTree');
  if (!treeContainer) return;

  const previousActivePath = currentActiveJavaFilePath || (document.getElementById('javaActiveFileName') ? document.getElementById('javaActiveFileName').innerText : null);
  let previousViewState = null;
  if (viewerEditor && viewerEditor.getModel()) {
    try {
      previousViewState = viewerEditor.saveViewState();
    } catch (_) {}
  }

  treeContainer.innerHTML = '';

  if (!files || files.length === 0) return;

  // Build nested folder node structure
  const root = { name: 'root', type: 'dir', children: {} };

  files.forEach(file => {
    const normalized = file.path.replace(/\\/g, '/');
    const segments = normalized.split('/');
    let curr = root;

    for (let i = 0; i < segments.length; i++) {
      const seg = segments[i];
      const isFile = (i === segments.length - 1);

      if (isFile) {
        curr.children[seg] = {
          name: seg,
          type: 'file',
          path: file.path,
          content: file.content
        };
      } else {
        if (!curr.children[seg]) {
          curr.children[seg] = {
            name: seg,
            type: 'dir',
            children: {}
          };
        }
        curr = curr.children[seg];
      }
    }
  });

  // Compress single-child folder chains (e.g. src/main/java/com/shopsphere/backend)
  function compressNode(node) {
    if (node.type !== 'dir') return;
    const childKeys = Object.keys(node.children);
    childKeys.forEach(k => compressNode(node.children[k]));

    if (node.name !== 'root' && childKeys.length === 1) {
      const singleChild = node.children[childKeys[0]];
      if (singleChild.type === 'dir') {
        node.name = node.name + '/' + singleChild.name;
        node.children = singleChild.children;
      }
    }
  }
  compressNode(root);

  // Recursive DOM rendering
  function renderDOM(node, container, depth = 0) {
    const sortedKeys = Object.keys(node.children).sort((a, b) => {
      const childA = node.children[a];
      const childB = node.children[b];
      if (childA.type !== childB.type) {
        return childA.type === 'dir' ? -1 : 1; // Folders first
      }
      return a.localeCompare(b);
    });

    sortedKeys.forEach(key => {
      const item = node.children[key];

      if (item.type === 'dir') {
        const folderWrap = document.createElement('div');
        folderWrap.className = 'tree-folder-group';

        const header = document.createElement('div');
        header.className = 'tree-folder-header';
        header.style.paddingLeft = `${depth * 14 + 6}px`;

        header.innerHTML = `
          <svg class="folder-arrow expanded" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <polyline points="9 18 15 12 9 6"></polyline>
          </svg>
          <svg class="folder-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
          </svg>
          <span class="folder-name">${item.name}</span>
        `;

        const subContainer = document.createElement('div');
        subContainer.className = 'tree-folder-children';

        header.onclick = (e) => {
          e.stopPropagation();
          const arrow = header.querySelector('.folder-arrow');
          const isCollapsed = subContainer.style.display === 'none';
          subContainer.style.display = isCollapsed ? 'block' : 'none';
          if (isCollapsed) {
            arrow.classList.add('expanded');
          } else {
            arrow.classList.remove('expanded');
          }
        };

        folderWrap.appendChild(header);
        folderWrap.appendChild(subContainer);
        container.appendChild(folderWrap);

        renderDOM(item, subContainer, depth + 1);

      } else {
        // File item
        const fileEl = document.createElement('div');
        fileEl.className = 'tree-file-item';
        fileEl.style.paddingLeft = `${depth * 14 + 18}px`;
        fileEl.dataset.path = item.path;

        const iconSvg = getFileIconSvg(item.name);

        fileEl.innerHTML = `
          <span class="tree-file-icon">${iconSvg}</span>
          <span class="tree-file-name" title="${item.path}">${item.name}</span>
        `;

        fileEl.onclick = (e, preserveScroll = false) => {
          if (e) e.stopPropagation();
          document.querySelectorAll('.tree-file-item').forEach(x => x.classList.remove('active'));
          fileEl.classList.add('active');
          currentActiveJavaFilePath = item.path;
          try {
            localStorage.setItem('zenith_active_file', item.path);
          } catch (_) {}
          document.getElementById('javaActiveFileName').innerText = item.path;
          
          if (viewerEditor) {
            const lang = detectLanguage(item.name);
            const model = monaco.editor.createModel(item.content, lang);
            viewerEditor.setModel(model);
            if (preserveScroll && previousViewState) {
              try {
                viewerEditor.restoreViewState(previousViewState);
              } catch (_) {}
            }
          }
        };

        container.appendChild(fileEl);
      }
    });
  }

  renderDOM(root, treeContainer, 0);

  // Re-select previously active file or fallback to first file
  let targetFileEl = null;
  if (previousActivePath) {
    const allFileItems = treeContainer.querySelectorAll('.tree-file-item');
    for (const el of allFileItems) {
      if (el.dataset.path === previousActivePath) {
        targetFileEl = el;
        break;
      }
    }
  }

  if (targetFileEl) {
    targetFileEl.onclick(null, true);

    // Expand parent folders to ensure visibility
    let parent = targetFileEl.parentElement;
    while (parent && parent !== treeContainer) {
      if (parent.classList.contains('tree-folder-children')) {
        parent.style.display = 'block';
        const folderGroup = parent.parentElement;
        if (folderGroup) {
          const arrow = folderGroup.querySelector('.folder-arrow');
          if (arrow) arrow.classList.add('expanded');
        }
      }
      parent = parent.parentElement;
    }
    try {
      targetFileEl.scrollIntoView({ block: 'nearest', behavior: 'auto' });
    } catch (_) {}
  } else {
    const firstFileEl = treeContainer.querySelector('.tree-file-item');
    if (firstFileEl) {
      firstFileEl.click();
    }
  }
}

function copyCurrentJavaCode() {
  if (viewerEditor) {
    const code = viewerEditor.getValue();
    navigator.clipboard.writeText(code);
  }
}

function detectLanguage(filename) {
  if (filename.endsWith('.java')) return 'java';
  if (filename.endsWith('.ts')) return 'typescript';
  if (filename.endsWith('.js')) return 'javascript';
  if (filename.endsWith('.json')) return 'json';
  if (filename.endsWith('.xml')) return 'xml';
  if (filename.endsWith('.yml') || filename.endsWith('.yaml')) return 'yaml';
  if (filename.endsWith('.sql')) return 'sql';
  if (filename.endsWith('.md')) return 'markdown';
  if (filename.endsWith('Dockerfile') || filename.endsWith('.dockerignore')) return 'dockerfile';
  return 'plaintext';
}

function getFileIconSvg(filename) {
  const lower = filename.toLowerCase();
  if (lower.endsWith('.java')) {
    // Java cup icon in warm orange/amber
    return `<svg viewBox="0 0 24 24" fill="none" stroke="#ea580c" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8h1a4 4 0 0 1 0 8h-1"></path><path d="M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z"></path><line x1="6" y1="1" x2="6" y2="4"></line><line x1="10" y1="1" x2="10" y2="4"></line><line x1="14" y1="1" x2="14" y2="4"></line></svg>`;
  }
  if (lower.endsWith('.ts')) {
    // TypeScript icon in blue
    return `<svg viewBox="0 0 24 24" fill="#3b82f6"><rect x="2" y="2" width="20" height="20" rx="4" fill="#3178c6"/><text x="6" y="16" fill="#fff" font-size="10" font-weight="bold" font-family="sans-serif">TS</text></svg>`;
  }
  if (lower.endsWith('.json')) {
    // JSON icon in yellow/amber
    return `<svg viewBox="0 0 24 24" fill="none" stroke="#eab308" stroke-width="2"><circle cx="12" cy="12" r="9"></circle><path d="M10 9a2 2 0 0 0-2 2v2a2 2 0 0 0 2 2"></path><path d="M14 9a2 2 0 0 1 2 2v2a2 2 0 0 1-2 2"></path></svg>`;
  }
  if (lower.endsWith('.xml')) {
    // XML icon in red/orange
    return `<svg viewBox="0 0 24 24" fill="none" stroke="#f97316" stroke-width="2"><polyline points="16 18 22 12 16 6"></polyline><polyline points="8 6 2 12 8 18"></polyline></svg>`;
  }
  if (lower.endsWith('.yml') || lower.endsWith('.yaml')) {
    // YAML icon in purple
    return `<svg viewBox="0 0 24 24" fill="none" stroke="#a855f7" stroke-width="2"><path d="M4 4l8 8-8 8"></path><line x1="12" y1="12" x2="20" y2="12"></line></svg>`;
  }
  if (lower.endsWith('.sql')) {
    // SQL database icon in cyan
    return `<svg viewBox="0 0 24 24" fill="none" stroke="#06b6d4" stroke-width="2"><ellipse cx="12" cy="5" rx="9" ry="3"></ellipse><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"></path><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"></path></svg>`;
  }
  if (lower.endsWith('dockerfile') || lower.includes('docker')) {
    // Docker whale/container in sky blue
    return `<svg viewBox="0 0 24 24" fill="none" stroke="#0ea5e9" stroke-width="2"><rect x="2" y="2" width="20" height="8" rx="2"></rect><rect x="2" y="14" width="20" height="8" rx="2"></rect><line x1="6" y1="6" x2="6.01" y2="6"></line><line x1="6" y1="18" x2="6.01" y2="18"></line></svg>`;
  }
  if (lower.endsWith('.md')) {
    // Markdown document icon in slate
    return `<svg viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line></svg>`;
  }
  // Generic file
  return `<svg viewBox="0 0 24 24" fill="none" stroke="#71717a" stroke-width="2"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path><polyline points="13 2 13 9 20 9"></polyline></svg>`;
}

// Render TypeScript
let currentTsTab = 'client';
function renderTypeScript(files) {
  switchTsFile(currentTsTab);
}

function switchTsFile(tab) {
  currentTsTab = tab;
  document.querySelectorAll('.ts-tab').forEach(t => t.classList.remove('active'));
  // Not tied to a click event: this also runs from triggerCompile(), where `event` is undefined
  const activeBtn = document.querySelector(`.ts-tab[onclick*="'${tab}'"]`);
  if (activeBtn) activeBtn.classList.add('active');

  if (!currentCompilation || !currentCompilation.files) return;

  const targetName = tab === 'client' ? 'client.ts' : (tab === 'types' ? 'types.ts' : 'react-query.ts');
  const file = currentCompilation.files.find(f => f.path.endsWith(targetName));
  const preview = document.getElementById('tsCodePreview');
  if (file) {
    preview.innerText = file.content;
  } else {
    preview.innerText = `// ${targetName} is not generated in this schema configuration.`;
  }
}

function copyTsCode() {
  const code = document.getElementById('tsCodePreview').innerText;
  navigator.clipboard.writeText(code);
}

// Render Docker
function renderDocker(files) {
  if (!files) return;
  const df = files.find(f => f.path.endsWith('Dockerfile'));
  const dc = files.find(f => f.path.endsWith('docker-compose.yml'));

  if (df) document.getElementById('dockerfilePreview').innerText = df.content;
  if (dc) document.getElementById('dockerComposePreview').innerText = dc.content;
}

// Toggle Run / Stop Spring Boot Process
async function toggleRunApp() {
  const runBtn = document.getElementById('runBtn');
  const runBtnText = document.getElementById('runBtnText');
  const swaggerBtn = document.getElementById('swaggerBtn');

  if (isSpringRunning) {
    // STOP process
    try {
      await fetch('/api/stop', { method: 'POST' });
      isSpringRunning = false;
      runBtnText.innerText = 'Run';
      runBtn.className = 'btn btn-primary';
      swaggerBtn.style.display = 'none';
      clearInterval(logPollInterval);
      appendConsoleLog('[Zenith Studio] Stopped application.');
    } catch (err) {
      console.error('Stop error:', err);
    }
  } else {
    // START process
    try {
      runBtnText.innerText = 'Starting...';
      const code = editor.getValue();
      const res = await fetch('/api/run', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ source: code, file: window.currentActiveFile || 'schema.zen' })
      });
      const data = await res.json();

      if (data.success) {
        isSpringRunning = true;
        runBtnText.innerText = 'Stop';
        runBtn.className = 'btn btn-primary running-state';
        swaggerBtn.style.display = 'inline-flex';

        // Switch to console tab automatically
        const consoleTabBtn = Array.from(document.querySelectorAll('.tab-btn')).find(b => b.innerText.includes('Console'));
        if (consoleTabBtn) consoleTabBtn.click();

        startLogPolling();
      } else {
        runBtnText.innerText = 'Run';
        alert('Failed to launch Spring Boot: ' + data.error);
      }
    } catch (err) {
      runBtnText.innerText = 'Run';
      console.error('Run error:', err);
    }
  }
}

// Poll logs from Zenith Studio Server
function startLogPolling() {
  clearInterval(logPollInterval);
  logPollInterval = setInterval(async () => {
    try {
      const res = await fetch('/api/logs');
      const data = await res.json();
      const box = document.getElementById('consoleOutput');
      box.innerHTML = '';

      data.logs.forEach(line => {
        const el = document.createElement('div');
        el.className = 'log-line';
        if (line.includes('ERROR') || line.includes('Exception') || line.includes('FAILURE')) {
          el.className += ' error';
        } else if (line.includes('Started ShopSphere') || line.includes('Started ')) {
          el.className += ' success';
        } else if (line.includes('[Zenith Studio]')) {
          el.className += ' info';
        }
        el.innerText = line;
        box.appendChild(el);
      });

      box.scrollTop = box.scrollHeight;
    } catch (_) {}
  }, 1000);
}

function appendConsoleLog(line) {
  const box = document.getElementById('consoleOutput');
  const el = document.createElement('div');
  el.className = 'log-line info';
  el.innerText = line;
  box.appendChild(el);
  box.scrollTop = box.scrollHeight;
}

// Download Project ZIP
function exportZip() {
  window.location.href = '/api/export';
}

// Reference Modal
function toggleCheatSheet(forceOpen) {
  const modal = document.getElementById('referenceModal');
  if (!modal) return;
  const isShown = modal.classList.contains('show');
  const shouldOpen = (forceOpen !== undefined) ? forceOpen : !isShown;
  if (shouldOpen) {
    modal.classList.add('show');
  } else {
    modal.classList.remove('show');
  }
}

// Close on Escape key
window.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    toggleCheatSheet(false);
  }
});

function escapeHtml(str) {
  return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
