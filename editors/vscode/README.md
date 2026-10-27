# Zenith Language Support for VS Code

Rich syntax highlighting, bracket matching, snippets, and code scaffolding for **Zenith DSL** (`.zen` files).

## Features

* **Syntax Highlighting**: Keywords (`config`, `model`, `enum`, `guard`, `action`, `endpoints`, `crud`), HTTP methods (`get`, `post`, `put`, `delete`), decorators (`@id`, `@auto`, `@unique`, `@password`, `@role`), types, operators, and literals.
* **Auto-closing Pairs**: Automatic closing of braces `{}`, brackets `[]`, parentheses `()`, and quotes `""`.
* **Snippets**:
  * Type `config` $\rightarrow$ generate starter application configuration.
  * Type `model` $\rightarrow$ generate model with fields and CRUD endpoints.
  * Type `enum` $\rightarrow$ generate enum.
  * Type `endpoint` $\rightarrow$ generate custom endpoint with guard and action block.

## Installation

### Method A: Install locally into your VS Code / Antigravity extensions
Copy or symlink this folder into your VS Code extensions directory:
```bash
# Windows:
xcopy /E /I "editors\vscode" "%USERPROFILE%\.vscode\extensions\zenith-dsl"
```

### Method B: Package to .vsix
```bash
npm install -g @vscode/vsce
cd editors/vscode
vsce package
```
Then in VS Code: Extensions $\rightarrow$ `...` $\rightarrow$ **Install from VSIX...**
